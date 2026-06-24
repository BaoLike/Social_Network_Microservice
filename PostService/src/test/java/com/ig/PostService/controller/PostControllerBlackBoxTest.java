package com.ig.PostService.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.service.PostService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PostControllerBlackBoxTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PostService postService = mock(PostService.class);
    private final MockMvc mockMvc;

    PostControllerBlackBoxTest() {
        PostController controller = new PostController();
        ReflectionTestUtils.setField(controller, "postService", postService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createPost_multipart_returns200_andDelegatesToService() throws Exception {
        // Keep createAt null to avoid requiring JavaTimeModule configuration in this standalone blackbox test.
        PostRequest postRequest = new PostRequest("u1", "hello", null, 0L, null);
        MockMultipartFile dataPart = new MockMultipartFile(
                "data",
                "data.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(postRequest)
        );
        MockMultipartFile mediaPart = new MockMultipartFile(
                "media",
                "image.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image".getBytes()
        );

        PostResponse serviceResponse = new PostResponse();
        serviceResponse.setId("p1");
        when(postService.CreateNewPost(any(PostRequest.class), any())).thenReturn(serviceResponse);

        mockMvc.perform(multipart("/create").file(dataPart).file(mediaPart))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("p1"));

        ArgumentCaptor<PostRequest> requestCaptor = ArgumentCaptor.forClass(PostRequest.class);
        verify(postService).CreateNewPost(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getUserId()).isEqualTo("u1");
        assertThat(requestCaptor.getValue().getDescription()).isEqualTo("hello");
    }

    @Test
    void getPostInUserProfile_returns200_andBodyFromService() throws Exception {
        UserPostProfileResponse response = new UserPostProfileResponse();
        response.setUserId("u1");
        when(postService.GetPostInUserProfile("u1")).thenReturn(response);

        mockMvc.perform(get("/profile/u1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("u1"));
    }

    @Test
    void deletePost_returns200_andDelegates() throws Exception {
        mockMvc.perform(delete("/delete/u1/p1"))
                .andExpect(status().isOk());
        verify(postService).DeletePost("u1", "p1");
    }

    @Test
    void likePost_readsAuthorizationHeader_andDelegates() throws Exception {
        mockMvc.perform(put("/like/p1").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(postService).LikePost("p1", "Bearer token");
    }

    @Test
    void unlikePost_readsAuthorizationHeader_andDelegates() throws Exception {
        mockMvc.perform(put("/unlike/p1").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(postService).unlikePost("p1", "Bearer token");
    }

    @Test
    void commentPost_returns200_andDelegates() throws Exception {
        CommentRequest req = new CommentRequest("u1", "nice", null);

        mockMvc.perform(
                        put("/comment/p1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(req))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").exists());

        verify(postService).commentPost(eq("p1"), any(CommentRequest.class));
    }

    @Test
    void clearCache_returns200_andDelegates() throws Exception {
        mockMvc.perform(post("/clear-cache"))
                .andExpect(status().isOk());
        verify(postService).clearCache();
    }

    @Test
    void getPost_returns200_andBodyFromService() throws Exception {
        PostResponse p = new PostResponse();
        p.setId("p1");
        when(postService.getPost(any())).thenReturn(List.of(p));

        mockMvc.perform(get("/get-post").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("p1"));
    }
}

