package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dto.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.model.AuthEmail;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.service.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ApplicationPropertiesConfig applicationProperties;
    private final OtpRedisService otpRedisService;

    public Mono<String> forgetPassword(String email) {
        final String genericResponse = "If an account with that email exists, a password reset link has been dispatched.";
        Duration resetTokenExpiry = applicationProperties.getAuth().getResetTokenExpiry();

        return userRepository.findByEmail(email)
                .flatMap(user -> {
                    String resetToken = UUID.randomUUID().toString();
                    return otpRedisService.savePasswordResetToken(resetToken, user.getId(), resetTokenExpiry)
                            .flatMap(saved -> {
                                String subject = "Reset Password";
                                String baseUrl = applicationProperties.getApp().getBaseUrl();
                                String resetURL = baseUrl + "/reset-password/" + resetToken;
                                String content = "Forget Password? Please click the link below to change your password:<br>"
                                        + "<h3>" + resetURL + "</h3>"
                                        + "Bye Bye, Regards from:<br>"
                                        + "Your company name.";

                                AuthEmail authEmail = new AuthEmail(user.getEmail(), subject, content);
                                return Mono.fromRunnable(() -> emailService.sendResetPasswordLink(authEmail, resetToken))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .thenReturn(genericResponse);
                            });
                })
                .defaultIfEmpty(genericResponse);
    }

    public Mono<String> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        return otpRedisService.getAndEvictPasswordResetToken(resetPasswordRequest.getVerificationToken())
                .flatMap(userId -> userRepository.findById(userId)
                        .flatMap(user -> {
                            user.setPassword(passwordEncoder.encode(resetPasswordRequest.getPassword()));
                            return userRepository.save(user);
                        })
                        .thenReturn("Password Reset successfully."))
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK)));
    }
}
