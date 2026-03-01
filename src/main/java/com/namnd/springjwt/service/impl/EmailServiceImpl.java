package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${namnd.app.passwordResetBaseUrl}")
    private String passwordResetBaseUrl;

    @Value("${namnd.app.activationBaseUrl}")
    private String activationBaseUrl;

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        String resetLink = passwordResetBaseUrl + "?token=" + token;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Password Reset Request");
            message.setText(
                "You have requested to reset your password.\n\n"
                + "Click the link below to reset your password:\n"
                + resetLink + "\n\n"
                + "This link will expire in 30 minutes.\n"
                + "If you did not request this, please ignore this email."
            );

            mailSender.send(message);
            logger.info("Password reset email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Override
    public void sendActivationEmail(String to, String token) {
        String activationLink = activationBaseUrl + "?token=" + token;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Activate Your Account");
            message.setText(
                "Welcome! Please activate your account.\n\n"
                + "Click the link below to activate:\n"
                + activationLink + "\n\n"
                + "This link will expire in 24 hours.\n"
                + "If you did not register, please ignore this email."
            );
            mailSender.send(message);
            logger.info("Activation email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            logger.error("Failed to send activation email to {}: {}", maskEmail(to), e.getMessage());
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
