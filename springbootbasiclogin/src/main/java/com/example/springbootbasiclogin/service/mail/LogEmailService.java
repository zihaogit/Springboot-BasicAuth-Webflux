package com.example.springbootbasiclogin.service.mail;

import com.example.springbootbasiclogin.model.AuthEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "auth.mail-config.provider", havingValue = "log")
@Slf4j
public class LogEmailService implements EmailService {

    @Override
    public void sendVerifyLink(AuthEmail authEmail, String verifyLink) {
        log.info("Start sending verification link to {}", authEmail.getEmail());
        log.info("Email subject: {}", authEmail.getSubject());
        System.out.println("[DEV EMAIL] Verification Link: " + verifyLink);
        log.info("Finish sending email");
    }

    @Override
    public void sendResetPasswordLink(AuthEmail authEmail, String resetLink) {
        log.info("Start sending reset password link to {}", authEmail.getEmail());
        log.info("Email subject: {}", authEmail.getSubject());
        System.out.println("[DEV EMAIL] Reset Password Link: " + resetLink);
        log.info("Finish sending email");
    }
}
