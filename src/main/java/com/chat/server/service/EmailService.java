package com.chat.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = "https://yourapp.com/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText("Click the link to reset your password: " + resetUrl + "\n\nThis link expires in 1 hour.");

        mailSender.send(message);
        log.info("Password reset email sent to: {}", to);
    }

    public void sendEmailVerification(String to, String token) {
        String verifyUrl = "https://yourapp.com/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Verify Your Email");
        message.setText("Click the link to verify your email: " + verifyUrl);

        mailSender.send(message);
        log.info("Email verification sent to: {}", to);
    }
}