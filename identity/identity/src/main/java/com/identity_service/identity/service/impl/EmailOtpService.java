package com.identity_service.identity.service.impl;

import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.model.entity.EmailVerifyToken;
import com.identity_service.identity.model.entity.PasswordResetToken;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.PasswordResetTokenRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j(topic = "EMAIL_OTP_SERVICE")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailOtpService {

    static final int OTP_EXPIRY_MINUTES = 10;

    JavaMailSender mailSender;
    EmailVerifyTokenRepository emailVerifyTokenRepository;
    PasswordResetTokenRepository passwordResetTokenRepository;

    @NonFinal
    SecureRandom secureRandom = new SecureRandom();

    @NonFinal
    @Value("${spring.mail.username:}")
    String fromEmail;

    @NonFinal
    @Value("${app.mail.fail-open:false}")
    boolean mailFailOpen;

    /** Tạo và lưu OTP; trả về mã để publish Kafka (notification gửi mail async). */
    public String createVerificationOtp(User user) {
        emailVerifyTokenRepository.deleteByUsers_UserId(user.getUserId());

        String otp = generateOtp();
        EmailVerifyToken token = EmailVerifyToken.builder()
                .emailVerifyToken(otp)
                .users(user)
                .expiredAt(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .build();
        emailVerifyTokenRepository.save(token);
        return otp;
    }

    public void sendVerificationOtp(User user) {
        String otp = createVerificationOtp(user);
        sendOtpEmail(user.getEmail(), user.getUserName(), otp);
    }

    public void sendPasswordResetOtp(User user) {
        passwordResetTokenRepository.deleteByUsers_UserId(user.getUserId());

        String otp = generateOtp();
        PasswordResetToken token = PasswordResetToken.builder()
                .resetToken(otp)
                .users(user)
                .expiredAt(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .build();
        passwordResetTokenRepository.save(token);

        sendPasswordResetEmail(user.getEmail(), user.getUserName(), otp);
    }

    String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    void sendOtpEmail(String toEmail, String userName, String otp) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.error("spring.mail.username is not configured");
            log.error("===== OTP cho {} : {} (hết hạn sau {} phút) =====",
                    toEmail, otp, OTP_EXPIRY_MINUTES);
            if (!mailFailOpen) {
                throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
            }
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Mã xác thực tài khoản Social Network");
        message.setText(
                "Xin chào " + userName + ",\n\n"
                        + "Mã OTP xác thực tài khoản của bạn là: " + otp + "\n\n"
                        + "Mã có hiệu lực trong " + OTP_EXPIRY_MINUTES + " phút.\n"
                        + "Nếu bạn không đăng ký tài khoản, vui lòng bỏ qua email này."
        );

        try {
            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send OTP email to {}", toEmail, e);
            log.error("===== OTP cho {} : {} (hết hạn sau {} phút) =====",
                    toEmail, otp, OTP_EXPIRY_MINUTES);
            if (!mailFailOpen) {
                throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
            }
            log.warn("app.mail.fail-open=true — bỏ qua lỗi gửi mail, tiếp tục đăng ký");
        }
    }

    void sendPasswordResetEmail(String toEmail, String userName, String otp) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.error("spring.mail.username is not configured");
            log.error("===== OTP đặt lại mật khẩu cho {} : {} (hết hạn sau {} phút) =====",
                    toEmail, otp, OTP_EXPIRY_MINUTES);
            if (!mailFailOpen) {
                throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
            }
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Mã đặt lại mật khẩu Social Network");
        message.setText(
                "Xin chào " + userName + ",\n\n"
                        + "Mã OTP đặt lại mật khẩu của bạn là: " + otp + "\n\n"
                        + "Mã có hiệu lực trong " + OTP_EXPIRY_MINUTES + " phút.\n"
                        + "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này."
        );

        try {
            mailSender.send(message);
            log.info("Password reset OTP email sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send password reset OTP to {}", toEmail, e);
            log.error("===== OTP đặt lại mật khẩu cho {} : {} (hết hạn sau {} phút) =====",
                    toEmail, otp, OTP_EXPIRY_MINUTES);
            if (!mailFailOpen) {
                throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
            }
            log.warn("app.mail.fail-open=true — bỏ qua lỗi gửi mail đặt lại mật khẩu");
        }
    }
}
