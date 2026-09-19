package com.example.springbootbasiclogin.service.mail;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.model.AuthEmail;
import com.example.springbootbasiclogin.exception.CustomException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@ConditionalOnProperty(value = "auth.mail-config.provider", havingValue = "local")
@Slf4j
public class LocalhostEmailService implements EmailService {

    private final ApplicationPropertiesConfig.MailConfig mailConfigConfig;
    private final JavaMailSender mailSender;

    public LocalhostEmailService(ApplicationPropertiesConfig applicationProperties, JavaMailSender mailSender) {
        this.mailConfigConfig = applicationProperties.getAuth().getMailConfig();
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerifyLink(AuthEmail authEmail, String verifyLink) {
        sendEmail(authEmail, "verification");
    }

    @Override
    public void sendResetPasswordLink(AuthEmail authEmail, String resetLink) {
        sendEmail(authEmail, "reset password");
    }

    private void sendEmail(AuthEmail authEmail, String emailType) {
        log.info("Start sending {} email to {}", emailType, authEmail.getEmail());
        String fromAddress = mailConfigConfig.getFrom();
        String senderName = mailConfigConfig.getName();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);

        try {
            helper.setFrom(fromAddress, senderName);
            helper.setTo(authEmail.getEmail());
            helper.setSubject(authEmail.getSubject());
            helper.setText(authEmail.getContent(), true);

            mailSender.send(message);
        } catch (MailException | UnsupportedEncodingException | MessagingException e) {
            log.error("Failed to send {} email to {}: {}", emailType, authEmail.getEmail(), e.getMessage());
            throw new CustomException(AuthResponseCode.AUTH_000201_FAILED_SEND_EMAIL, e);
        } finally {
            log.info("Finish sending {} email", emailType);
        }
    }
}
