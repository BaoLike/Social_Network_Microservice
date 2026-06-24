package com.identity_service.identity.service.impl;

import com.identity_service.identity.dto.request.ProfileCreationRequest;
import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.mapper.UserMapper;
import com.identity_service.identity.mapper.UserProfileMapper;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.repository.httpclient.ProfileClient;
import com.identity_service.identity.service.IUserService;
import com.identity_service.identity.service.impl.EmailOtpService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j(topic = "USER_SERVICE")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class UserService implements IUserService {
    UserMapper userMapper;
    UserRepository userRepository;
    UserProfileMapper profileMapper;
    ProfileClient profileClient;
    EmailOtpService emailOtpService;
    @Override
    @Transactional
    public UserResponse createUser(UserCreationRequest request) {

        User user = userMapper.convertUserFromRequest(request);
        if(userRepository.existsByUserName(request.getUserName()) || userRepository.existsByEmail(request.getEmail())){
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        var u = userRepository.save(user);

        emailOtpService.sendVerificationOtp(u);

        //Goi den profile service
        ProfileCreationRequest profileRequest = profileMapper.convertFromUserCreationRequest(request);
        profileRequest.setUserId(u.getUserId());

        profileClient.createProfile(profileRequest);

        return userMapper.convertResponseFromUser(u) ;
    }

    @Override
    public UserResponse getUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));
        return userMapper.convertResponseFromUser(user);
    }

    @Override
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));
        user.setUserStatus(UserStatus.DELETE);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void lockUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));
        user.setLocked(true);
        userRepository.save(user);
        log.info("Tài khoản {} đã bị khóa do vi phạm chính sách cộng đồng", userId);
    }


}
