package com.story.ig.service;

import com.story.ig.config.R2Config;
import com.story.ig.dto.StoryDTO;
import com.story.ig.dto.UserStoriesDTO;
import com.story.ig.exception.UserNotFoundException;
import com.story.ig.model.Story;
import com.story.ig.model.UserStories;
import com.story.ig.payload.request.HighlightStory;
import com.story.ig.repo.UserStoriesRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class StoryService{
        S3Client r2Client;
        R2Config r2Config;
        CacheManager cacheManager;
        UserStoriesRepo userStoriesRepo;

        @NonFinal
        @Value("${app.services.profile:http://localhost:8081/profile/}")
        String profileUrl;

        @NonFinal
        @Value("${app.services.identity:http://localhost:8080/identity/user}")
        String identityUrl;


    public UserStoriesDTO PostStory(String userId, MultipartFile media){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        try {
            Map<String, Object> userInfor = getProfileById(userId);
            LocalDateTime date = LocalDateTime.now();
            StoryDTO storyDTO = new StoryDTO();
            storyDTO.setUrlMedia(uploadMediaToS3(userId, media));
            storyDTO.setCreateAt(date);
            storyDTO.setUserId(userId);
            storyDTO.setLiked(0l);

            Cache userStoriesCache = cacheManager.getCache("Stories");
            Cache.ValueWrapper userStoriesWrapper = userStoriesCache.get(userId);

            if(userStoriesWrapper == null) {
                UserStoriesDTO userStoriesDTO = new UserStoriesDTO();
                userStoriesDTO.setFirstName(valueAsString(userInfor.get("firstName")));
                userStoriesDTO.setLastName(valueAsString(userInfor.get("lastName")));
                userStoriesDTO.setAvatar(valueAsString(userInfor.get("avatar")));
                List<StoryDTO> listStories = new ArrayList<>();
                listStories.add(storyDTO);
                userStoriesDTO.setUserId(userId);
                userStoriesDTO.setStories(listStories);
                userStoriesCache.put(userId, userStoriesDTO);
                return userStoriesDTO;
            }
            else{
                UserStoriesDTO userStoriesDTO = (UserStoriesDTO) userStoriesWrapper.get();
                if(userStoriesDTO.getStories() == null || userStoriesDTO.getStories().isEmpty()){
                    List<StoryDTO> listStories = new ArrayList<>();
                    listStories.add(storyDTO);
                    userStoriesDTO.setUserId(userId);
                    userStoriesDTO.setStories(listStories);
                    userStoriesCache.put(userId, userStoriesDTO);
                    return userStoriesDTO;
                }
                else{
                    userStoriesDTO.getStories().add(storyDTO);
                    userStoriesCache.put(userId, userStoriesDTO);
                    return userStoriesDTO;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<UserStoriesDTO> getStory(HttpServletRequest request){
        String authHeader = request.getHeader("Authorization");
        List<UserStoriesDTO> userStoriesDTOList = new ArrayList<>();
        List<String> userFollowedId = getUserIdFollowed(authHeader);
        Cache cache = cacheManager.getCache("Stories");
        for(String followedId: userFollowedId){
            Cache.ValueWrapper userStoryValueWrapper = cache.get(followedId);
            if(userStoryValueWrapper != null){
                UserStoriesDTO userStoriesDTO = (UserStoriesDTO) userStoryValueWrapper.get();
                userStoriesDTOList.add(userStoriesDTO);
            }
        }
        return userStoriesDTOList;
    }


    private String uploadMediaToS3(String userId,MultipartFile media) throws IOException {
        UUID uuid = UUID.randomUUID();
        String fileName = uuid.toString() + media.getName();
        String dirName = String.format("stories/%s/%s", userId, fileName);

        PutObjectRequest request = PutObjectRequest.builder().bucket(r2Config.getBucketName())
                .key(dirName)
                .contentType(media.getContentType())
                .contentLength(media.getSize())
                .build();
        r2Client.putObject(request, RequestBody.fromInputStream(media.getInputStream(), media.getSize()));
        return r2Config.getPublicUrl() + "/" + dirName;
    }

    public UserStoriesDTO getStoriesByUserId(String userId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Cache storyCache = cacheManager.getCache("Stories");
        Cache.ValueWrapper storyWrapper = storyCache.get(userId);
        if(storyWrapper != null) {
            UserStoriesDTO userStoriesDTO = (UserStoriesDTO) storyWrapper.get();
            List<StoryDTO> newStories = userStoriesDTO.getStories().stream().filter((item) -> item.getCreateAt().isBefore(item.getCreateAt().plusHours(24))).toList();
            userStoriesDTO.setStories(newStories);
            storyCache.put(userId, userStoriesDTO);
            return userStoriesDTO;
        }
        else{
            UserStoriesDTO response = new UserStoriesDTO();
            response.setUserId(userId);
            response.setStories(new ArrayList<>());
            return response;
        }
    }

    public UserStoriesDTO getStoriesHighlight(String userId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Optional<UserStories> userStoriesOptional = userStoriesRepo.findById(userId);
        if(userStoriesOptional.isPresent()){
            UserStories userStories = userStoriesOptional.get();
            UserStoriesDTO userStoriesDTO = new UserStoriesDTO();
            userStoriesDTO.setUserId(userId);
            userStoriesDTO.setAvatar(userStories.getAvatar());
            userStoriesDTO.setFirstName(userStories.getFirstName());
            userStoriesDTO.setLastName(userStories.getLastName());
            userStoriesDTO.setStories(userStories.getListStories().stream().map((story -> {
                StoryDTO storyDTO = new StoryDTO();
                storyDTO.setUrlMedia(story.getUrlMedia());
                storyDTO.setLiked(story.getLiked());
                //storyDTO.setCreateAt(story.getCreateAt());
                storyDTO.setDescription(story.getDescription());
                storyDTO.setUserId(story.getUserId());
                return storyDTO;
            })).toList());
            return userStoriesDTO;
        }
        else{
            return null;
        }
    }

    public void ClearRedis(){
        Cache cacheUserStories = cacheManager.getCache("Stories");
        if(cacheUserStories != null){
            cacheUserStories.clear();
        }
    }

    public void makeHighLightStory(HighlightStory highlightStory) {
        if(!CheckUserExisted(highlightStory.getUserId())){
            throw new UserNotFoundException(highlightStory.getUserId());
        }
        else{
            Map<String, Object> userInfor = getProfileById(highlightStory.getUserId());
            Optional<UserStories> userStoriesOptional = userStoriesRepo.findById(highlightStory.getUserId());
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            UserStories userStories = new UserStories();
            userStories.setAvatar(valueAsString(userInfor.get("avatar")));
            userStories.setLastName(valueAsString("lastName"));
            userStories.setFirstName(valueAsString(userInfor.get("firstName")));
            if(userStoriesOptional.isPresent()){
                userStories = userStoriesOptional.get();
            }
            else{
                userStories.setListStories(new ArrayList<>());
            }

            UUID uuid = UUID.randomUUID();

            Story story = new Story();
            story.setId(highlightStory.getUserId() + uuid.toString());
            story.setUserId(highlightStory.getUserId());
            story.setUrlMedia(highlightStory.getUrlMedia());
            story.setLiked(highlightStory.getLike());
            story.setDescription(highlightStory.getDescription());
            //story.setCreateAt(highlightStory.getCreateAt());
            story.setLiked(0l);
            userStories.getListStories().add(story);
            userStories.setUserId(highlightStory.getUserId());
            userStoriesRepo.save(userStories);
        }
    }

    public boolean CheckUserExisted(String userId){
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(identityUrl + "/getById?userId=" + userId)).GET().build();
        String codeIdentityService = "1002";
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper mapper = new ObjectMapper();
            Map dataRes = mapper.readValue(response.body(), Map.class);
            codeIdentityService = dataRes.get("code").toString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return codeIdentityService.equals("1000");
    }

    public List<String> getUserIdFollowed(String authorizationHeader){
        List<String> listUserId = new ArrayList<>();
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
//            log.warn("Missing Authorization header when calling profile service");
            return listUserId;
        }

        HttpClient httpClient = HttpClient.newHttpClient();
        String authHeader = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader
                : "Bearer " + authorizationHeader.trim();
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(profileUrl + "getFollowed"))
                .header("Authorization", authHeader)
                .GET()
                .build();

        System.out.println("Request headers: " + httpRequest.headers());
        try{
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (response.statusCode() != 200) {
//                log.warn("Profile service returned non-200 status: {}", response.statusCode());
                return listUserId;
            }

            Map<String, Object> datares = mapper.readValue(response.body(), Map.class);
            System.out.println("data " + response.body());

            Object resultObj = datares.get("result");
            if (!(resultObj instanceof List<?> result)) {
//                log.warn("Profile service returned empty or invalid result: {}", response.body());
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
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return listUserId;
    }

    private Map<String, Object> getProfileById(String userId){
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(profileUrl + "info/internal/profile/" + userId)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Cannot get profile for userId: " + userId);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> dataRes = mapper.readValue(response.body(), Map.class);
            Object result = dataRes.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                throw new RuntimeException("Profile response format is invalid");
            }
            return (Map<String, Object>) resultMap;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }

}
