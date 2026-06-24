package com.identity_service.identity.service;

import com.identity_service.identity.dto.request.AuthRequest;
import com.identity_service.identity.dto.request.IntroSpectRequest;
import com.identity_service.identity.dto.request.LogOutRequest;
import com.identity_service.identity.dto.request.RefreshTokenRequest;
import com.identity_service.identity.dto.request.ForgotPasswordRequest;
import com.identity_service.identity.dto.request.ResetPasswordRequest;
import com.identity_service.identity.dto.request.ResendOtpRequest;
import com.identity_service.identity.dto.request.VerifyEmailOtpRequest;
import com.identity_service.identity.dto.response.AuthResponse;
import com.identity_service.identity.dto.response.IntroSpectResponse;
import com.nimbusds.jose.JOSEException;

import java.text.ParseException;

public interface IAuthService {
    AuthResponse authenticateUser(AuthRequest request);
    IntroSpectResponse introspectToken(IntroSpectRequest request);
    AuthResponse refreshTokenAfterTimeOut(RefreshTokenRequest request) throws ParseException, JOSEException;
    void logOut(LogOutRequest logOutRequest) throws ParseException, JOSEException;
    void verifyEmail(String token);
    void verifyEmailOtp(VerifyEmailOtpRequest request);
    void resendOtp(ResendOtpRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
