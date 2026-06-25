package com.notification.notification_service.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "OTP_EMAIL_SERVICE")
public class OtpEmailService {

    private static final int OTP_EXPIRY_MINUTES = 10;

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.mail.fail-open:true}")
    private boolean mailFailOpen;

    public void sendVerificationOtp(String toEmail, String userName, String otp) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.error("spring.mail.username is not configured — OTP for {}: {}", toEmail, otp);
            if (!mailFailOpen) {
                throw new IllegalStateException("Mail not configured");
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
            log.info("OTP email sent via Kafka consumer to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            log.error("===== OTP cho {} : {} =====", toEmail, otp);
            if (!mailFailOpen) {
                throw e;
            }
        }
    }
}
