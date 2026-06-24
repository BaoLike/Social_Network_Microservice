package com.ig.PostService.service;

import com.ig.PostService.mapper.Mapper;
import com.ig.PostService.model.Post;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.repo.CommentRepo;
import com.ig.PostService.repo.PostLikeRepo;
import com.ig.PostService.repo.PostRepo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceWhiteBoxTest {

    @Mock
    PostRepo postRepo;
    @Mock
    CommentRepo commentRepo;
    @Mock
    PostLikeRepo postLikeRepo;
    @Mock
    CacheManager cacheManager;
    @Mock
    Cache cache;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new PostService());

        ReflectionTestUtils.setField(service, "postRepo", postRepo);
        ReflectionTestUtils.setField(service, "commentRepo", commentRepo);
        ReflectionTestUtils.setField(service, "postLikeRepo", postLikeRepo);
        ReflectionTestUtils.setField(service, "cacheManager", cacheManager);
        ReflectionTestUtils.setField(service, "mapper", new Mapper());
    }

    @Test
    void extractUserIdFromAuthorizationHeader_acceptsBearerToken() {
        String jwt = fakeJwtWithSub("u1");
        String userId = service.extractUserIdFromAuthorizationHeader("Bearer " + jwt);
        assertThat(userId).isEqualTo("u1");
    }

    @Test
    void extractUserIdFromAuthorizationHeader_rejectsMissingHeader() {
        assertThatThrownBy(() -> service.extractUserIdFromAuthorizationHeader(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing Authorization header");
    }

    @Test
    void likePost_whenNotLikedYet_incrementsLike_andSavesLikeEntity() {
        doReturn(true).when(service).CheckUserExisted("u1");
        doReturn(false).when(service).checkRedisConnection();

        Post post = new Post();
        post.setId("p1");
        post.setUserId("owner");
        post.setLiked(0L);
        post.setCommentList(List.of());

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(postLikeRepo.existsByPostIdAndUserId("p1", "u1")).thenReturn(false);

        service.LikePost("p1", "Bearer " + fakeJwtWithSub("u1"));

        verify(postLikeRepo).save(any());
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepo, atLeastOnce()).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getLiked()).isEqualTo(1L);
    }

    @Test
    void likePost_whenAlreadyLiked_doesNothing() {
        doReturn(true).when(service).CheckUserExisted("u1");

        Post post = new Post();
        post.setId("p1");
        post.setUserId("owner");
        post.setLiked(5L);
        post.setCommentList(List.of());

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(postLikeRepo.existsByPostIdAndUserId("p1", "u1")).thenReturn(true);

        service.LikePost("p1", "Bearer " + fakeJwtWithSub("u1"));

        verify(postLikeRepo, never()).save(any());
        verify(postRepo, never()).save(any(Post.class));
    }

    @Test
    void unlikePost_whenNotLikedYet_doesNothing() {
        doReturn(true).when(service).CheckUserExisted("u1");

        Post post = new Post();
        post.setId("p1");
        post.setUserId("owner");
        post.setLiked(5L);
        post.setCommentList(List.of());

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(postLikeRepo.existsByPostIdAndUserId("p1", "u1")).thenReturn(false);

        service.unlikePost("p1", "Bearer " + fakeJwtWithSub("u1"));

        verify(postLikeRepo, never()).deleteByPostIdAndUserId(anyString(), anyString());
        verify(postRepo, never()).save(any(Post.class));
    }

    @Test
    void unlikePost_whenLiked_decrementsLike_andDeletesLikeEntity() {
        doReturn(true).when(service).CheckUserExisted("u1");
        doReturn(false).when(service).checkRedisConnection();

        Post post = new Post();
        post.setId("p1");
        post.setUserId("owner");
        post.setLiked(2L);
        post.setCommentList(List.of());

        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(postLikeRepo.existsByPostIdAndUserId("p1", "u1")).thenReturn(true);

        service.unlikePost("p1", "Bearer " + fakeJwtWithSub("u1"));

        verify(postLikeRepo).deleteByPostIdAndUserId("p1", "u1");
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepo).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getLiked()).isEqualTo(1L);
    }

    @Test
    void getPost_whenNoRedisAndNoFollowed_fallsBackToFindAll_andSetsLikedByUser() {
        doReturn(false).when(service).checkRedisConnection();
        doReturn(List.of()).when(service).getUserIdFollowed(anyString());

        Post p1 = new Post("p1", "owner", null, null, null, "u", "d", 0L, LocalDateTime.now(), "m", List.of());
        Post p2 = new Post("p2", "owner", null, null, null, "u", "d", 0L, LocalDateTime.now(), "m", List.of());
        when(postRepo.findAll()).thenReturn(List.of(p1, p2));
        when(postLikeRepo.findLikedPostIdsByUserIdAndPostIds(eq("u1"), anySet()))
                .thenReturn(Set.of("p2"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + fakeJwtWithSub("u1"));

        Set<PostResponse> out = service.getPost(req);

        assertThat(out).hasSize(2);
        assertThat(out.stream().filter(r -> "p2".equals(r.getId())).findFirst().orElseThrow().getLikedByUser())
                .isTrue();
        assertThat(out.stream().filter(r -> "p1".equals(r.getId())).findFirst().orElseThrow().getLikedByUser())
                .isFalse();
    }

    private static String fakeJwtWithSub(String sub) {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + sub + "\"}");
        return header + "." + payload + ".sig";
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}

