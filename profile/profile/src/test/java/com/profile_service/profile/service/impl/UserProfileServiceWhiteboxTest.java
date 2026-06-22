package com.profile_service.profile.service.impl;

import com.profile_service.profile.configuration.R2Config;
import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.request.ProfileUpdateRequest;
import com.profile_service.profile.dto.response.ProfileFullInfoResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.entity.UserProfile;
import com.profile_service.profile.exception.AppException;
import com.profile_service.profile.exception.ErrorCode;
import com.profile_service.profile.mapper.UserProfileMapper;
import com.profile_service.profile.repository.FollowRepository;
import com.profile_service.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Whitebox test cho UserProfileService:
 * kiểm tra logic nội bộ, mapping, tương tác với repository/S3.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService - Whitebox")
class UserProfileServiceWhiteboxTest {

    @Mock
    S3Client r2Client;

    @Mock
    UserProfileMapper userProfileMapper;

    @Mock
    UserProfileRepository userProfileRepository;

    @Mock
    R2Config r2Config;

    @Mock
    FollowRepository followRepository;

    @InjectMocks
    UserProfileService userProfileService;

    @Test
    @DisplayName("createProfile: mapper nhận request và save entity rồi map sang response")
    void createProfile_mapsRequestAndSavesEntity() {
        ProfileCreationRequest request = ProfileCreationRequest.builder()
                .userId("u1")
                .userName("user1")
                .firstName("A")
                .lastName("B")
                .gender("M")
                .dob(LocalDate.of(2000, 1, 1))
                .address("HN")
                .phone("0123")
                .build();

        UserProfile entity = UserProfile.builder()
                .userId("u1")
                .userName("user1")
                .build();

        UserProfile saved = UserProfile.builder()
                .id("node-1")
                .userId("u1")
                .userName("user1")
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .userId("u1")
                .userName("user1")
                .build();

        when(userProfileMapper.convertUserProfileFromRequest(request)).thenReturn(entity);
        when(userProfileRepository.save(entity)).thenReturn(saved);
        when(userProfileMapper.convertResponseFromUserProfile(saved)).thenReturn(response);

        UserProfileResponse result = userProfileService.createProfile(request);

        assertEquals("u1", result.getUserId());
        assertEquals("user1", result.getUserName());

        verify(userProfileMapper).convertUserProfileFromRequest(request);
        verify(userProfileRepository).save(entity);
        verify(userProfileMapper).convertResponseFromUserProfile(saved);
        verifyNoMoreInteractions(userProfileMapper, userProfileRepository);
    }

    @Test
    @DisplayName("updateProfile: lấy userId từ SecurityContext và cập nhật các field")
    void updateProfile_updatesFieldsFromRequest() {
        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .firstName("NewFirst")
                .lastName("NewLast")
                .dob(LocalDate.of(1999, 12, 31))
                .address("New address")
                .build();

        // giả lập user đang đăng nhập
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-123", "password")
        );

        UserProfile existing = UserProfile.builder()
                .userId("user-123")
                .firstName("OldFirst")
                .lastName("OldLast")
                .dob(LocalDate.of(1990, 1, 1))
                .address("Old address")
                .build();

        UserProfile saved = UserProfile.builder()
                .userId("user-123")
                .firstName("NewFirst")
                .lastName("NewLast")
                .dob(request.getDob())
                .address(request.getAddress())
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .userId("user-123")
                .firstName("NewFirst")
                .lastName("NewLast")
                .dob(request.getDob())
                .address(request.getAddress())
                .build();

        when(userProfileRepository.findByUserId("user-123")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(existing)).thenReturn(saved);
        when(userProfileMapper.convertResponseFromUserProfile(saved)).thenReturn(response);

        UserProfileResponse result = userProfileService.updateProfile(request);

        assertEquals("NewFirst", result.getFirstName());
        assertEquals("NewLast", result.getLastName());
        assertEquals(request.getDob(), result.getDob());
        assertEquals(request.getAddress(), result.getAddress());
    }

    @Test
    @DisplayName("updateProfile: ném AppException(PROFILE_NOT_FOUND) khi không tìm thấy profile")
    void updateProfile_throwsWhenProfileNotFound() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-404", "password")
        );

        when(userProfileRepository.findByUserId("user-404")).thenReturn(Optional.empty());

        AppException ex = assertThrows(
                AppException.class,
                () -> userProfileService.updateProfile(ProfileUpdateRequest.builder().build())
        );
        assertEquals(ErrorCode.PROFILE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateAvatar: upload lên S3 và set avatar url rồi lưu lại")
    void updateAvatar_uploadsToS3AndUpdatesAvatar() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-avatar", "password")
        );

        MultipartFile file = mock(MultipartFile.class);
        when(file.getName()).thenReturn("avatar.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(10L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));

        UserProfile existing = UserProfile.builder()
                .userId("user-avatar")
                .avatar(null)
                .build();

        when(userProfileRepository.findByUserId("user-avatar")).thenReturn(Optional.of(existing));
        when(r2Config.getBucketName()).thenReturn("bucket");
        when(r2Config.getPublicUrl()).thenReturn("https://cdn.example.com");

        // giả lập S3 không ném exception
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(mock(PutObjectResponse.class));

        UserProfile saved = UserProfile.builder()
                .userId("user-avatar")
                .avatar("https://cdn.example.com/post/user-avatar/some-file")
                .build();
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(saved);

        UserProfileResponse mapped = UserProfileResponse.builder()
                .userId("user-avatar")
                .avatar(saved.getAvatar())
                .build();
        when(userProfileMapper.convertResponseFromUserProfile(saved)).thenReturn(mapped);

        UserProfileResponse result = userProfileService.updateAvatar(file);

        assertEquals("user-avatar", result.getUserId());
        assertNotNull(result.getAvatar());

        verify(r2Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("getUserProfile: build ProfileFullInfoResponse với follower/followed")
    void getUserProfile_buildsFullInfoResponse() {
        UserProfile entity = UserProfile.builder()
                .userId("u1")
                .userName("user1")
                .avatar("a.png")
                .firstName("First")
                .lastName("Last")
                .gender("M")
                .dob(LocalDate.of(2000, 1, 1))
                .address("HN")
                .phone("123")
                .build();

        when(userProfileRepository.findByUserId("u1")).thenReturn(Optional.of(entity));
        when(followRepository.countFollowers("u1")).thenReturn(10L);
        when(followRepository.countFollowing("u1")).thenReturn(5L);

        ProfileFullInfoResponse response = userProfileService.getUserProfile("u1");

        assertEquals("u1", response.getUserId());
        assertEquals("user1", response.getUserName());
        assertEquals(10L, response.getFollower());
        assertEquals(5L, response.getFollowed());
    }

    @Test
    @DisplayName("searchUserByUserName: map danh sách entity sang response")
    void searchUserByUserName_mapsListToResponse() {
        UserProfile u1 = UserProfile.builder().userId("u1").userName("user1").build();
        UserProfile u2 = UserProfile.builder().userId("u2").userName("user2").build();

        when(userProfileRepository.findUserByUserName("user"))
                .thenReturn(List.of(u1, u2));

        UserProfileResponse r1 = UserProfileResponse.builder().userId("u1").userName("user1").build();
        UserProfileResponse r2 = UserProfileResponse.builder().userId("u2").userName("user2").build();

        when(userProfileMapper.convertResponseFromUserProfile(u1)).thenReturn(r1);
        when(userProfileMapper.convertResponseFromUserProfile(u2)).thenReturn(r2);

        var result = userProfileService.searchUserByUserName("user");

        assertEquals(2, result.size());
        assertEquals("u1", result.get(0).getUserId());
        assertEquals("u2", result.get(1).getUserId());
    }
}

