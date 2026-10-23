package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dto.auth.RegisterRequest;
import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.model.AuthEmail;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.service.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationService {

    private static final int OTP_MIN = 100_000;
    private static final int OTP_RANGE = 900_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ApplicationPropertiesConfig applicationProperties;
    private final OtpRedisService otpRedisService;

    public Mono<Users> registerUser(RegisterRequest registerRequest) {
        Duration otpExpiry = applicationProperties.getAuth().getOtpExpiry();

        return userRepository.findByUsername(registerRequest.getUsername())
                .flatMap(existingUser -> {
                    if (!existingUser.isVerified()) {
                        // Resend verification email — generate new OTP, store in Redis, send email
                        int otp = generateOTP();
                        return otpRedisService.saveVerificationOtp(existingUser.getEmail(), otp, existingUser.getId(), otpExpiry)
                                .then(Mono.fromRunnable(() -> sendVerificationEmail(existingUser.getEmail(), otp))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .thenReturn(existingUser));
                    } else {
                        // User is already verified
                        log.error("User is already registered and verified.");
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000109_USER_IS_REGISTERED));
                    }
                })
                .switchIfEmpty(Mono.defer(() ->
                        userRepository.findByEmail(registerRequest.getEmail())
                                .flatMap(existingEmailUser -> Mono.<Users>error(new CustomException(AuthResponseCode.AUTH_000107_EMAIL_REGISTERED_BEFORE)))
                                .switchIfEmpty(Mono.defer(() -> {
                                    // Create new user
                                    Users newUser = new Users();
                                    newUser.setUsername(registerRequest.getUsername());
                                    newUser.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
                                    newUser.setEmail(registerRequest.getEmail());
                                    newUser.setVerified(false);

                                    return userRepository.save(newUser)
                                            .flatMap(savedUser -> {
                                                Roles role = new Roles();
                                                role.setUserId(savedUser.getId());
                                                role.setRole("USER");

                                                int otp = generateOTP();
                                                return roleRepository.save(role)
                                                        .then(otpRedisService.saveVerificationOtp(savedUser.getEmail(), otp, savedUser.getId(), otpExpiry))
                                                        .then(Mono.fromRunnable(() -> sendVerificationEmail(savedUser.getEmail(), otp))
                                                                .subscribeOn(Schedulers.boundedElastic())
                                                                .thenReturn(savedUser));
                                            });
                                }))
                ));
    }

    public void sendVerificationEmail(String email, int otp) {
        String subject = "Email Verification";
        String baseUrl = applicationProperties.getApp().getBaseUrl();
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String verifyURL = baseUrl + "/auths/verify-email?email=" + encodedEmail + "&verifyOTP=" + otp;
        String content = "Please click the link below to verify your registration:<br>"
                + "<h3><a href=\"" + verifyURL + "\">VERIFY</a></h3>"
                + "Thank you,<br>Your company name.";

        AuthEmail authEmail = new AuthEmail(email, subject, content);
        emailService.sendVerifyLink(authEmail, verifyURL);
    }

    public Mono<String> verifyEmail(int verifyOTP) {
        return verifyEmail(verifyOTP, null);
    }

    public Mono<String> verifyEmail(int verifyOTP, String email) {
        return otpRedisService.getAndEvictVerificationOtp(email, verifyOTP)
                .flatMap(userId -> userRepository.findById(userId)
                        .flatMap(user -> {
                            user.setVerified(true);
                            return userRepository.save(user);
                        })
                        .thenReturn("Email verified successfully."))
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK)));
    }

    private static int generateOTP() {
        return OTP_MIN + SECURE_RANDOM.nextInt(OTP_RANGE);
    }
}
