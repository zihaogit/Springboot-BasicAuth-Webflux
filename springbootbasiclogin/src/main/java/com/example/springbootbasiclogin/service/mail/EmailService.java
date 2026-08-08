package com.example.springbootbasiclogin.service.mail;

import com.example.springbootbasiclogin.model.AuthEmail;

public interface EmailService {
    void sendVerifyLink(AuthEmail authEmail, String verifyLink);

    void sendResetPasswordLink(AuthEmail authEmail, String resetLink);
}
