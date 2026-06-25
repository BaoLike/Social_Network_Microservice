package com.ig.PostService.service;

import com.ig.PostService.config.R2Config;
import com.ig.PostService.exception.AccountLockedException;
import com.ig.PostService.exception.PostViolationException;
import com.ig.PostService.exception.UserNotFoundException;
import com.ig.PostService.mapper.Mapper;
import com.ig.PostService.model.Comment;
import com.ig.PostService.model.Post;
import com.ig.PostService.model.PostLike;
import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.request.UpdatePostRequest;
import com.ig.PostService.payload.response.CommentResponse;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.model.CommentLike;
import com.ig.PostService.repo.CommentLikeRepo;
import com.ig.PostService.repo.CommentRepo;
import com.ig.PostService.repo.PostLikeRepo;
import com.ig.PostService.repo.PostRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

/**
 * Post service. Kiểm duyệt AI khi tạo/sửa bài ({@link #checkPostWithAI}) luôn chạy
 * <strong>đồng bộ</strong> qua HTTP tới ai-server — không qua Kafka, vì app phải
 * nhận kết quả ngay và user xác nhận trách nhiệm khi claim chưa verify (unverifiedInfo).
 * Kafka chỉ áp dụng cho side-effect async: {@link #notifyPostInteraction} →
 * topic {@code notification.interaction.created}.
 *
 * @see docs/kafka/EVENT-DRIVEN-ARCHITECTURE.md §2.4
 */
@Slf4j
@Service
public class PostService {
    @Value("${app.services.identity:http://localhost:8080/identity/user}")
    private String identityUrl;
    @Value("${app.services.profile:http://localhost:8081/profile/}")
    private String profileUrl;
    @Value("${app.services.notification:http://localhost:8082/notification}")
    private String notificationUrl;
    @Value("${app.services.ai:http://localhost:5000/check-post}")
    private String aiCheckPostUrl;

    private static final int HTTP_RETRY_MAX = 3;
    @Autowired
    private S3Client r2Client;
    @Autowired
    private R2Config r2Config;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PostRepo postRepo;
    @Autowired
    private CommentRepo commentRepo;
    @Autowired
    private PostLikeRepo postLikeRepo;
    @Autowired
    private CommentLikeRepo commentLikeRepo;
    @Autowired
    private PostViolationService postViolationService;
    @Autowired
    private Mapper mapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private com.ig.PostService.kafka.PostKafkaEventPublisher postKafkaEventPublisher;

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

    private final String urlR2 = "https://pub-bd5ab7734dda491c8c8e7f89705ed9c2.r2.dev";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public PostResponse CreateNewPost(PostRequest request, MultipartFile media) {
        try {
            checkPostWithAI(request.getDescription(), media);
        } catch (PostViolationException e) {
            postViolationService.trackViolation(
                    request.getUserId(), e.getMessage(), () -> lockUserAccount(request.getUserId()));
        }

        Map<String, Object> userInfo = getUserById(request.getUserId());
        String userName = valueAsString(userInfo.get("userName"));
        Map<String, Object> profileInfo = getProfileById(request.getUserId());
        UUID uuid = UUID.randomUUID();
        String idPost = request.getUserId() + media.getName() + uuid.toString();
        Post newPost = new Post();
        LocalDateTime dateCreate = LocalDateTime.now();
        newPost.setUserName(userName);
        newPost.setId(idPost);
        newPost.setCreateAt(dateCreate);
        newPost.setLiked(0l);
        newPost.setCommentList(new ArrayList<>());
        newPost.setUserId(request.getUserId());
        newPost.setDescription(request.getDescription());
        newPost.setUnverifiedInfo(Boolean.TRUE.equals(request.getUnverifiedInfo()));
        newPost.setFirstName(valueAsString(profileInfo.get("firstName")));
        newPost.setLastName(valueAsString(profileInfo.get("lastName")));
        newPost.setAvatarUrl(valueAsString(profileInfo.get("avatar")));
        newPost.setUrlMedia(uploadMediaToS3(request.getUserId(), media));
        postRepo.save(newPost);
        if(checkRedisConnection()){
            PostResponse postResponse = mapper.PostResponseMapper(newPost);
            Cache postCache = cacheManager.getCache("Post");
            Cache.ValueWrapper postValueWrapper = postCache.get(request.getUserId());
            UserPostProfileResponse userPostProfileResponse = new UserPostProfileResponse();
            if(postValueWrapper != null){
                userPostProfileResponse = (UserPostProfileResponse) postValueWrapper.get();
            }
            userPostProfileResponse.getListUserPost().add(postResponse);
            postCache.put(request.getUserId(), userPostProfileResponse);
        }
        else{
            log.warn("Redis connection was interrupted");
        }
        return mapper.PostResponseMapper(newPost);
    }

    public void recordViolationAttempt(String authorizationHeader, String reason) {
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        String violationReason = (reason == null || reason.isBlank())
                ? "Bài đăng vi phạm chính sách cộng đồng"
                : reason;
        postViolationService.trackViolation(
                userId, violationReason, () -> lockUserAccount(userId));
    }


    @Transactional(readOnly = true)
    public UserPostProfileResponse GetPostInUserProfile(String userId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        UserPostProfileResponse userPostProfileResponse = new UserPostProfileResponse();
        List<PostResponse> listPostResponse = postRepo.getPostsByUserId(userId).stream().map((post) -> {
            PostResponse postResponse = mapper.PostResponseMapper(post);
            return  postResponse;
        }).toList();
        userPostProfileResponse.getListUserPost().addAll(listPostResponse);
        userPostProfileResponse.setUserId(userId);
        return userPostProfileResponse;
    }

    public void DeletePost(String userId, String postId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        if(checkRedisConnection()){
            Cache cache = cacheManager.getCache("Post");
            Cache.ValueWrapper valueWrapper = cache.get(userId);
            if(valueWrapper != null){
                UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) valueWrapper.get();
                userPostProfileResponse.getListUserPost().stream().filter((item) -> !item.getId().equals(postId));
                cache.put(userId, userPostProfileResponse);
            }
        }
        else{
            log.warn("Redis Connection Was Interrupted");
        }
        Post post = postRepo.getPostByIdAndUserId(postId, userId);
        postRepo.delete(post);
    }

    public void LikePost(String postId, String authorizationHeader){
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Optional<Post> postOptional = postRepo.findById(postId);
        if(postOptional.isPresent()){
            if (postLikeRepo.existsByPostIdAndUserId(postId, userId)) {
                return;
            }
            Post post = postOptional.get();
            PostLike postLike = new PostLike();
            postLike.setPostId(postId);
            postLike.setUserId(userId);
            postLike.setCreatedAt(LocalDateTime.now());
            postLikeRepo.save(postLike);
            post.setLiked(post.getLiked()+1);
            postRepo.save(post);
            notifyPostInteraction(post.getUserId(), userId, postId, "LIKE");
            if(checkRedisConnection()){
                Cache cache = cacheManager.getCache("Post");
                Cache.ValueWrapper valueWrapper = cache.get(post.getUserId());
                if(valueWrapper != null){
                    UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) valueWrapper.get();
                    userPostProfileResponse.getListUserPost().stream().filter((item) -> item.getId().equals(postId))
                            .findFirst()
                            .ifPresent((item) -> item.setLiked(item.getLiked()+1));
                }
            }
            else{
                log.warn("Redis Connection Was Interrupted");
            }
        }
    }

    @Transactional
    public void unlikePost(String postId, String authorizationHeader){
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Optional<Post> postOptional = postRepo.findById(postId);
        if(postOptional.isPresent()){
            if (!postLikeRepo.existsByPostIdAndUserId(postId, userId)) {
                return;
            }
            Post post = postOptional.get();
            postLikeRepo.deleteByPostIdAndUserId(postId, userId);
            if(post.getLiked() > 0){
                post.setLiked(post.getLiked()-1);
                postRepo.save(post);

                if(checkRedisConnection()){
                    Cache cache = cacheManager.getCache("Post");
                    Cache.ValueWrapper valueWrapper = cache.get(post.getUserId());
                    if(valueWrapper != null){
                        UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) valueWrapper.get();
                        userPostProfileResponse.getListUserPost().stream().filter((item) -> item.getId().equals(postId))
                                .findFirst()
                                .ifPresent((item) -> {
                                    if(item.getLiked() > 0){
                                        item.setLiked(item.getLiked() -1);
                                    }
                                });
                    }
                }
            }

        }
    }

    public void commentPost(String postId, CommentRequest request){
        if(!CheckUserExisted(request.getUserId())){
            throw new UserNotFoundException(request.getUserId());
        }
        Optional<Post> postOptional = postRepo.findById(postId);
        String userNameComment = getUserNameById(request.getUserId());
        Map<String, Object> userInfor = getProfileById(request.getUserId());
        if(postOptional.isPresent() && !userNameComment.isEmpty()){
            Post post = postOptional.get();
            LocalDateTime dateComment = LocalDateTime.now();
            Comment newComment = new Comment();
            newComment.setComment(request.getComment());
            newComment.setLastName(valueAsString(userInfor.get("lastName")));
            newComment.setFirstName(valueAsString(userInfor.get("firstName")));
            newComment.setAvatarUrl(valueAsString(userInfor.get("avatar")));
            newComment.setCommentAt(dateComment);
            newComment.setLiked(0l);
            newComment.setPost(post);

            if (request.getParentCommentId() != null) {
                Comment parentComment = commentRepo.findByIdAndPost_Id(
                                request.getParentCommentId(), postId)
                        .orElseThrow(() -> new RuntimeException("Comment gốc không tồn tại hoặc không thuộc bài viết này"));
                newComment.setParentComment(parentComment);
            }

            newComment.setUserName(userNameComment);
            commentRepo.save(newComment);
            String postOwnerId = post.getUserId();
            String commenterId = request.getUserId();
            log.info("Comment notification: postOwnerId={}, commenterId={}, postId={}", postOwnerId, commenterId, postId);
            notifyPostInteraction(postOwnerId, commenterId, postId, "COMMENT");
            CommentResponse commentResponse = mapper.CommentResponseMapper(newComment);
            Cache cache = cacheManager.getCache("Post");
            Cache.ValueWrapper postValueWrapper = cache.get(post.getUserId());
            if(postValueWrapper != null) {
                UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) postValueWrapper.get();
                userPostProfileResponse.getListUserPost().stream()
                        .filter(item -> item.getId().equals(postId))
                        .findFirst()
                        .ifPresent(item -> item.getCommentList().add(commentResponse));
                cache.put(post.getUserId(), userPostProfileResponse);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getPost(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String userId = extractUserIdFromAuthorizationHeader(authHeader);
        List<String> followedUserIds = getUserIdFollowed(authHeader);

        // LinkedHashMap deduplicates by post ID while preserving insertion order
        Map<String, PostResponse> followedPostsMap = new LinkedHashMap<>();

        // Phase 1: collect posts from followed users — Redis first, then DB to fill gaps
        if (!followedUserIds.isEmpty()) {
            if (checkRedisConnection()) {
                Cache cache = cacheManager.getCache("Post");
                for (String followedUserId : followedUserIds) {
                    Cache.ValueWrapper wrapper = cache.get(followedUserId);
                    if (wrapper != null) {
                        UserPostProfileResponse cached = (UserPostProfileResponse) wrapper.get();
                        if (cached != null) {
                            for (PostResponse p : cached.getListUserPost()) {
                                if (p.getId() != null) followedPostsMap.put(p.getId(), p);
                            }
                        }
                    }
                }
            }
            // putIfAbsent avoids overwriting fresher Redis data
            for (String followedUserId : followedUserIds) {
                postRepo.getPostsByUserId(followedUserId).stream()
                        .map(mapper::PostResponseMapper)
                        .forEach(p -> {
                            if (p.getId() != null) followedPostsMap.putIfAbsent(p.getId(), p);
                        });
            }
        }

        // Phase 2: sort followed posts by createAt descending (newest first)
        Comparator<PostResponse> newestFirst = Comparator.comparing(
                PostResponse::getCreateAt, Comparator.nullsLast(Comparator.reverseOrder()));

        List<PostResponse> result = followedPostsMap.values().stream()
                .sorted(newestFirst)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Phase 3: append non-followed posts sorted by newest first
        Set<String> addedIds = new HashSet<>(followedPostsMap.keySet());
        postRepo.findAll().stream()
                .map(mapper::PostResponseMapper)
                .filter(p -> p.getId() != null && !addedIds.contains(p.getId()))
                .sorted(newestFirst)
                .forEach(result::add);

        // Phase 4: annotate likedByUser for the current user
        if (userId != null && !userId.isBlank() && !result.isEmpty()) {
            Set<String> postIds = result.stream()
                    .map(PostResponse::getId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            if (!postIds.isEmpty()) {
                Set<String> likedPostIds = postLikeRepo.findLikedPostIdsByUserIdAndPostIds(userId, postIds);
                result.forEach(item -> item.setLikedByUser(likedPostIds.contains(item.getId())));
            }
        }

        return result;
    }

    private void lockUserAccount(String userId) {
        try {
            String lockUrl = identityUrl + "/lock?userId=" + userId;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(lockUrl))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Đã khóa tài khoản userId={}", userId);
            } else {
                log.warn("Khóa tài khoản userId={} thất bại, status={}", userId, response.statusCode());
            }
        } catch (Exception e) {
            log.error("Không thể gọi identity để khóa userId={}: {}", userId, e.getMessage());
        }
    }

    private String resolveImageFilename(String originalFilename, String contentType) {
        // Nếu filename đã có extension hợp lệ thì giữ nguyên
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".png") || lower.endsWith(".webp")
                    || lower.endsWith(".bmp") || lower.endsWith(".gif")) {
                return originalFilename;
            }
        }
        // Map content-type → extension
        String ext = switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png"               -> ".png";
            case "image/webp"              -> ".webp";
            case "image/bmp"               -> ".bmp";
            case "image/gif"               -> ".gif";
            default                        -> ".jpg";
        };
        return "image" + ext;
    }

    /**
     * Kiểm duyệt nội dung bài đăng — bắt buộc sync trong cùng request tạo post.
     * Không publish Kafka: vi phạm phải reject ngay trước khi lưu DB.
     */
    private void checkPostWithAI(String description, MultipartFile media) {
        String aiUrl = aiCheckPostUrl;

        try {
            org.springframework.util.MultiValueMap<String, Object> body =
                    new org.springframework.util.LinkedMultiValueMap<>();

            if (description != null && !description.isBlank()) {
                body.add("message", description);
            }

            if (media != null && !media.isEmpty()) {
                final byte[] bytes = media.getBytes();
                final String contentType = media.getContentType() != null
                        ? media.getContentType() : "image/jpeg";
                final String filename = resolveImageFilename(media.getOriginalFilename(), contentType);

                org.springframework.core.io.ByteArrayResource imageResource =
                        new org.springframework.core.io.ByteArrayResource(bytes) {
                            @Override
                            public String getFilename() { return filename; }
                        };

                org.springframework.http.HttpHeaders imageHeaders =
                        new org.springframework.http.HttpHeaders();
                imageHeaders.setContentType(
                        org.springframework.http.MediaType.parseMediaType(contentType));

                body.add("image", new org.springframework.http.HttpEntity<>(imageResource, imageHeaders));
            }

            org.springframework.http.HttpHeaders headers =
                    new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

            org.springframework.http.ResponseEntity<String> response =
                    new org.springframework.web.client.RestTemplate().postForEntity(
                            aiUrl,
                            new org.springframework.http.HttpEntity<>(body, headers),
                            String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> aiResult = objectMapper.readValue(response.getBody(), Map.class);

                // Response format: {"label": "violation"/"safe", "result": 0/1, ...}
                String label = String.valueOf(aiResult.getOrDefault("label", ""));
                Object resultCode = aiResult.get("result");

                boolean violated = "violation".equalsIgnoreCase(label)
                        || (resultCode instanceof Number && ((Number) resultCode).intValue() == 0);

                if (violated) {
                    String method = String.valueOf(aiResult.getOrDefault("method", ""));
                    String reason = "Bài đăng vi phạm chính sách cộng đồng"
                            + (method.isBlank() ? "" : " (" + method + ")");
                    throw new PostViolationException(reason);
                }
            } else {
                log.warn("AI check-post returned status {}, bỏ qua kiểm duyệt",
                        response.getStatusCode());
            }

        } catch (PostViolationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không thể kết nối tới AI server ({}): {}, bỏ qua kiểm duyệt",
                    aiUrl, e.getMessage());
        }
    }

    protected String extractUserIdFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new RuntimeException("Missing Authorization header");
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7).trim()
                : authorizationHeader.trim();
        try {
            String[] tokenParts = token.split("\\.");
            if (tokenParts.length < 2) {
                throw new RuntimeException("Invalid JWT format");
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]));
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
            Object sub = payload.get("sub");
            if (sub == null || sub.toString().isBlank()) {
                throw new RuntimeException("Token does not contain user id");
            }
            return sub.toString();
        } catch (IllegalArgumentException | IOException e) {
            throw new RuntimeException("Invalid Authorization token", e);
        }
    }

    public List<String> getUserIdFollowed(String authorizationHeader){
        List<String> listUserId = new ArrayList<>();
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            log.warn("Missing Authorization header when calling profile service");
            return listUserId;
        }

        String authHeader = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader
                : "Bearer " + authorizationHeader.trim();
        try{
            HttpResponse<String> response = sendGetWithRetry(profileUrl + "getFollowed", authHeader);
            ObjectMapper mapper = new ObjectMapper();
            if (response.statusCode() != 200) {
                log.warn("Profile service returned non-200 status: {}", response.statusCode());
                return listUserId;
            }

            Map<String, Object> datares = mapper.readValue(response.body(), Map.class);

            Object resultObj = datares.get("result");
            if (!(resultObj instanceof List<?> result)) {
                log.warn("Profile service returned empty or invalid result: {}", response.body());
                return listUserId;
            }

            for (Object item : result) {
                if (item instanceof Map<?, ?> user) {
                    Object userId = user.get("userId");
                    if (userId instanceof String userIdStr) {
                        listUserId.add(userIdStr);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return listUserId;
    }


    protected String uploadMediaToS3(String userId, MultipartFile media)  {
        UUID uuid = UUID.randomUUID();
        String fileName = uuid.toString() + media.getName();
        String dirName = String.format("post/%s/%s", userId, fileName);

        PutObjectRequest request = PutObjectRequest.builder().bucket(r2Config.getBucketName())
                .key(dirName)
                .contentType(media.getContentType())
                .contentLength(media.getSize())
                .build();
        try {
            r2Client.putObject(request, RequestBody.fromInputStream(media.getInputStream(), media.getSize()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return  urlR2 + "/" + dirName;
    }

    protected boolean CheckUserExisted(String userId){
        String codeIdentityService = "1002";
        try {
            HttpResponse<String> response = sendGetWithRetry(identityUrl + "/getById?userId=" + userId, null);
            ObjectMapper mapper = new ObjectMapper();
            Map dataRes = mapper.readValue(response.body(), Map.class);
            codeIdentityService = dataRes.get("code").toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return codeIdentityService.equals("1000");
    }

    protected Map<String, Object> getProfileById(String userId){
        try {
            HttpResponse<String> response = sendGetWithRetry(profileUrl + "info/internal/profile/" + userId, null);
            log.info(profileUrl + "info/internal/profile/" + userId);
            if (response.statusCode() != 200) {
                throw new RuntimeException("Cannot get profile for userId: " + userId);
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dataRes = mapper.readValue(response.body(), Map.class);
            Object result = dataRes.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                throw new RuntimeException("Profile response format is invalid");
            }
            return (Map<String, Object>) resultMap;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }



    protected String getUserNameById(String userId){
        String userName = "";
        try{
            HttpResponse<String> response = sendGetWithRetry(identityUrl + "/getById?userId=" + userId, null);
            ObjectMapper mapper = new ObjectMapper();
            Map datares = mapper.readValue(response.body(), Map.class);
            Map<String, Object> userInfo = (Map<String, Object>) datares.get("result");
            userName = userInfo.get("userName").toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return userName;
    }

    protected Map<String, Object> getUserById(String userId){
        try {
            HttpResponse<String> response = sendGetWithRetry(identityUrl + "/getById?userId=" + userId, null);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dataRes = mapper.readValue(response.body(), Map.class);
            if (!"1000".equals(String.valueOf(dataRes.get("code")))) {
                throw new UserNotFoundException(userId);
            }
            Object result = dataRes.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                throw new RuntimeException("Identity response format is invalid");
            }
            return (Map<String, Object>) resultMap;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected HttpResponse<String> sendGetWithRetry(String url, String authorizationHeader) {
        for (int attempt = 1; attempt <= HTTP_RETRY_MAX; attempt++) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .GET();
                if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                    requestBuilder.header("Authorization", authorizationHeader);
                }
                return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while calling downstream: " + url, e);
            } catch (IOException e) {
                boolean retryable = isRetryableConnectionError(e);
                if (retryable && attempt < HTTP_RETRY_MAX) {
                    log.warn("Retry {}/{} for url {} due to {}", attempt, HTTP_RETRY_MAX, url, e.getClass().getSimpleName());
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw new RuntimeException("Cannot connect to downstream url: " + url, e);
            }
        }
        throw new RuntimeException("Cannot connect to downstream url: " + url);
    }

    private boolean isRetryableConnectionError(IOException e) {
        if (e instanceof ConnectException) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause instanceof ClosedChannelException;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(150L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }



    public boolean checkRedisConnection() {
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.warn("Redis connection check failed: {}", e.getMessage());
            return false;
        }
    }


    public void likeComment(Long commentId, String authorizationHeader) {
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        if (!CheckUserExisted(userId)) {
            throw new UserNotFoundException(userId);
        }
        Optional<Comment> commentOptional = commentRepo.findById(commentId);
        if (commentOptional.isPresent()) {
            if (commentLikeRepo.existsByCommentIdAndUserId(commentId, userId)) {
                return;
            }
            Comment comment = commentOptional.get();
            CommentLike commentLike = new CommentLike();
            commentLike.setCommentId(commentId);
            commentLike.setUserId(userId);
            commentLike.setCreatedAt(LocalDateTime.now());
            commentLikeRepo.save(commentLike);
            comment.setLiked(comment.getLiked() + 1);
            commentRepo.save(comment);
        }
    }

    @Transactional
    public void unlikeComment(Long commentId, String authorizationHeader) {
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        if (!CheckUserExisted(userId)) {
            throw new UserNotFoundException(userId);
        }
        Optional<Comment> commentOptional = commentRepo.findById(commentId);
        if (commentOptional.isPresent()) {
            if (!commentLikeRepo.existsByCommentIdAndUserId(commentId, userId)) {
                return;
            }
            Comment comment = commentOptional.get();
            commentLikeRepo.deleteByCommentIdAndUserId(commentId, userId);
            if (comment.getLiked() > 0) {
                comment.setLiked(comment.getLiked() - 1);
                commentRepo.save(comment);
            }
        }
    }

    public PostResponse updatePost(String postId, String authorizationHeader, UpdatePostRequest request) {
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        if (!CheckUserExisted(userId)) {
            throw new UserNotFoundException(userId);
        }
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + postId));
        if (!userId.equals(post.getUserId())) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa bài viết này");
        }

        String newDescription = request.getDescription();
        if (newDescription != null && !newDescription.isBlank()) {
            // Kiểm duyệt AI (chỉ text, không cần media)
            try {
                checkPostWithAI(newDescription, null);
            } catch (PostViolationException e) {
                postViolationService.trackViolation(
                        userId, e.getMessage(), () -> lockUserAccount(userId));
                throw e;
            }
        }

        post.setDescription(newDescription);
        postRepo.save(post);

        // Xóa cache Redis để feed hiển thị đúng
        if (checkRedisConnection()) {
            Cache cache = cacheManager.getCache("Post");
            cache.evict(userId);
        }

        return mapper.PostResponseMapper(post);
    }

    public void clearCache(){
        Cache cache = cacheManager.getCache("Post");
        cache.clear();
    }

    /**
     * Thông báo like/comment — candidate chuyển sang Kafka topic
     * {@code notification.interaction.created} (không ảnh hưởng luồng đăng bài).
     */
    private void notifyPostInteraction(String recipientUserId, String actorUserId, String postId, String type) {
        if (recipientUserId == null || actorUserId == null || recipientUserId.equals(actorUserId)) {
            return;
        }
        try {
            Map<String, Object> profile = getProfileById(actorUserId);
            String firstName = valueAsString(profile.get("firstName"));
            String lastName = valueAsString(profile.get("lastName"));

            if (kafkaEnabled && postKafkaEventPublisher != null) {
                com.common_library.common.kafka.payload.NotificationInteractionCreatedPayload payload =
                        com.common_library.common.kafka.payload.NotificationInteractionCreatedPayload.builder()
                                .recipientUserId(recipientUserId)
                                .actorUserId(actorUserId)
                                .actorFirstName(firstName)
                                .actorLastName(lastName)
                                .type(type)
                                .postId(postId)
                                .build();
                postKafkaEventPublisher.publishInteraction(payload);

                if ("LIKE".equalsIgnoreCase(type)) {
                    postKafkaEventPublisher.publishPostLiked(
                            com.common_library.common.kafka.payload.PostLikedPayload.builder()
                                    .postId(postId)
                                    .postOwnerId(recipientUserId)
                                    .actorId(actorUserId)
                                    .likedAt(java.time.Instant.now().toString())
                                    .build());
                } else if ("COMMENT".equalsIgnoreCase(type)) {
                    postKafkaEventPublisher.publishPostCommented(
                            com.common_library.common.kafka.payload.PostCommentedPayload.builder()
                                    .postId(postId)
                                    .postOwnerId(recipientUserId)
                                    .commenterId(actorUserId)
                                    .commentedAt(java.time.Instant.now().toString())
                                    .build());
                }
                return;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("recipientUserId", recipientUserId);
            payload.put("actorUserId", actorUserId);
            payload.put("actorFirstName", firstName);
            payload.put("actorLastName", lastName);
            payload.put("type", type);
            payload.put("postId", postId);

            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(notificationUrl + "/internal/interaction"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Notification service returned {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to send interaction notification: {}", e.getMessage());
        }
    }
}
