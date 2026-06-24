package com.identity_service.identity.controller;

import com.identity_service.identity.dto.request.AuthRequest;
import com.identity_service.identity.dto.request.IntroSpectRequest;
import com.identity_service.identity.dto.request.LogOutRequest;
import com.identity_service.identity.dto.request.RefreshTokenRequest;
import com.identity_service.identity.dto.request.ForgotPasswordRequest;
import com.identity_service.identity.dto.request.ResetPasswordRequest;
import com.identity_service.identity.dto.request.ResendOtpRequest;
import com.identity_service.identity.dto.request.VerifyEmailOtpRequest;
import com.identity_service.identity.dto.response.ApiResponse;
import com.identity_service.identity.dto.response.AuthResponse;
import com.identity_service.identity.dto.response.IntroSpectResponse;
import com.identity_service.identity.service.IAuthService;
import com.nimbusds.jose.JOSEException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class AuthController {
    IAuthService authService;
    private final ClientHttpRequestFactorySettings clientHttpRequestFactorySettings;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> authenticated(@RequestBody AuthRequest request){

        return ApiResponse.<AuthResponse>builder()
                .message("Authenticated user successfully")
                .result(authService.authenticateUser(request))
                .build();
    }

    @PostMapping("/introspect")
    public ApiResponse<IntroSpectResponse> introspectToken(@RequestBody IntroSpectRequest request){

        return ApiResponse.<IntroSpectResponse>builder()
                .message("Introspect token success")
                .result(authService.introspectToken(request))
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refreshTokenAfterTimeOut(@RequestBody RefreshTokenRequest request) throws ParseException, JOSEException {

        return ApiResponse.<AuthResponse>builder()
                .message("Refresh token rotation success")
                .result(authService.refreshTokenAfterTimeOut(request))
                .build();
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logOut(@RequestBody LogOutRequest request) throws ParseException, JOSEException {
            authService.logOut(request);
        return ApiResponse.<Void>builder()
                .message("Log out successfully")
                .build();
    }

    @PostMapping("/verify_email")
    public ApiResponse<Void> verifyEmail(@RequestParam String token){
        authService.verifyEmail(token);
        return ApiResponse.<Void>
                builder()
                .build();
    }

    @PostMapping("/verify_email_otp")
    public ApiResponse<Void> verifyEmailOtp(@RequestBody VerifyEmailOtpRequest request) {
        authService.verifyEmailOtp(request);
        return ApiResponse.<Void>builder()
                .message("Xác thực email thành công")
                .build();
    }

    @PostMapping("/resend_otp")
    public ApiResponse<Void> resendOtp(@RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ApiResponse.<Void>builder()
                .message("Đã gửi lại mã OTP")
                .build();
    }

    @PostMapping("/forgot_password")
    public ApiResponse<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.<Void>builder()
                .message("Nếu email tồn tại, mã OTP đặt lại mật khẩu đã được gửi")
                .build();
    }

    @PostMapping("/reset_password")
    public ApiResponse<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.<Void>builder()
                .message("Đặt lại mật khẩu thành công")
                .build();
    }

}
