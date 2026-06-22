package com.profile_service.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.profile_service.profile.dto.request.ProfileUpdateRequest;
import com.profile_service.profile.dto.response.ProfileFullInfoResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.service.impl.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Blackbox test cho UserProfileController:
 * kiểm tra API response (JSON, HTTP code) mà không quan tâm logic bên trong service.
 */
@WebMvcTest(controllers = UserProfileController.class)
@AutoConfigureMockMvc(addFilters = true)
@DisplayName("UserProfileController - Blackbox")
class UserProfileControllerBlackboxTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserProfileService userProfileService;

    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("GET /info/getProfile: trả về ApiResponse<ProfileFullInfoResponse>")
    void getProfileById_returnsWrappedFullInfo() throws Exception {
        ProfileFullInfoResponse profile = ProfileFullInfoResponse.builder()
                .userId("u1")
                .userName("user1")
                .firstName("First")
                .lastName("Last")
                .build();

        Mockito.when(userProfileService.getUserProfile(any())).thenReturn(profile);

        mockMvc.perform(get("/info/getProfile").with(user("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.message", is("Get profile user")))
                .andExpect(jsonPath("$.result.userId", is("u1")))
                .andExpect(jsonPath("$.result.userName", is("user1")));
    }

    @Test
    @DisplayName("PUT /info/updateInfo: trả về ApiResponse<UserProfileResponse>")
    void updateInfoUser_returnsUpdatedProfile() throws Exception {
        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .firstName("NewFirst")
                .lastName("NewLast")
                .dob(LocalDate.of(2000, 1, 1))
                .address("HN")
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .userId("u1")
                .firstName("NewFirst")
                .lastName("NewLast")
                .dob(request.getDob())
                .address(request.getAddress())
                .build();

        Mockito.when(userProfileService.updateProfile(any(ProfileUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/info/updateInfo")
                        .with(user("u1"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.message", is("Update info user")))
                .andExpect(jsonPath("$.result.userId", is("u1")))
                .andExpect(jsonPath("$.result.firstName", is("NewFirst")));
    }

    @Test
    @DisplayName("POST /info/updateAvatar: upload multipart file và nhận ApiResponse<UserProfileResponse>")
    void updateAvatarUser_returnsUpdatedAvatar() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "dummy".getBytes()
        );

        UserProfileResponse response = UserProfileResponse.builder()
                .userId("u1")
                .avatar("https://cdn.example.com/u1/avatar.png")
                .build();

        Mockito.when(userProfileService.updateAvatar(any())).thenReturn(response);

        mockMvc.perform(multipart("/info/updateAvatar")
                        .file(file)
                        .with(user("u1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.message", is("Update avatar success")))
                .andExpect(jsonPath("$.result.avatar", is("https://cdn.example.com/u1/avatar.png")));
    }

    @Test
    @DisplayName("GET /info/search/{userName}: trả về danh sách user trong ApiResponse")
    void getUserSearchByUserName_returnsListOfProfiles() throws Exception {
        UserProfileResponse r1 = UserProfileResponse.builder().userId("u1").userName("alice").build();
        UserProfileResponse r2 = UserProfileResponse.builder().userId("u2").userName("bob").build();

        Mockito.when(userProfileService.searchUserByUserName(eq("a"))).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/info/search/{userName}", "a").with(user("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.message", is("User searched")))
                .andExpect(jsonPath("$.result", hasSize(2)))
                .andExpect(jsonPath("$.result[0].userId", is("u1")))
                .andExpect(jsonPath("$.result[1].userId", is("u2")));
    }
}

