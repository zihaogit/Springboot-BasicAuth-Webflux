package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.auth.LoginRequest;
import com.example.springbootbasiclogin.dao.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dao.auth.RegisterRequest;
import com.example.springbootbasiclogin.dao.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.dao.auth.TokenResponse;
import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.model.AuthEmail;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.service.jwt.JwtService;
import com.example.springbootbasiclogin.service.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService implements ReactiveUserDetailsService {

    private static final int OTP_MIN = 100_000;
    private static final int OTP_RANGE = 900_000;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final ApplicationPropertiesConfig applicationProperties;
    private final OtpRedisService otpRedisService;

    private static final SecureRandom secureRandom = new SecureRandom();

    // find the User + Roles using the username when require by basic auth
    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository
                .findByUsername(username)
                .flatMap(user -> roleRepository
                        .findByUserId(user.getId())
                        .map(Roles::getRole)
                        .collectList()
                        .map(roles -> User
                                .withUsername(user.getUsername())
                                .password(user.getPassword())
                                .roles(roles.toArray(String[]::new))
                                .build()));
    }

    public Mono<Boolean> loginUser(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .flatMap(user -> {
                    user.setActive(true);
                    return userRepository.save(user)
                            .thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    public Mono<TokenResponse> loginUser(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION)))
                .flatMap(user -> {
                    user.setActive(true);
                    return userRepository.save(user)
                            .flatMap(savedUser -> roleRepository.findByUserId(savedUser.getId())
                                     .map(Roles::getRole)
                                     .collectList()
                                     .map(roles -> buildTokenResponse(savedUser, roles)));
                });
    }

    public Mono<TokenResponse> generateTokensForUser(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000110_USER_NOT_REGISTERED)))
                .flatMap(user -> {
                    user.setActive(true);
                    return userRepository.save(user)
                            .flatMap(savedUser -> roleRepository.findByUserId(savedUser.getId())
                                    .map(Roles::getRole)
                                    .collectList()
                                    .map(roles -> buildTokenResponse(savedUser, roles)));
                });
    }

    private TokenResponse buildTokenResponse(Users user, java.util.List<String> roles) {
        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());
        long expiresIn = applicationProperties.getAuth().getJwt().getAccessTokenTtl().toSeconds();

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .username(user.getUsername())
                .build();
    }

    public Mono<TokenResponse> refreshToken(RefreshTokenRequest request) {
        return Mono.fromCallable(() -> jwtService.parseAndValidateToken(request.getRefreshToken()))
                .onErrorMap(e -> new CustomException(AuthResponseCode.AUTH_000115_INVALID_REFRESH_TOKEN, e))
                .flatMap(claims -> {
                    if (!jwtService.isTokenType(claims, "REFRESH")) {
                        return Mono
                                .error(new CustomException(AuthResponseCode.AUTH_000115_INVALID_REFRESH_TOKEN));
                    }
                    String username = claims.getSubject();
                    return generateTokensForUser(username);
                });
    }

    public Mono<Users> registerUser(RegisterRequest registerRequest) {
        Duration otpExpiry = applicationProperties.getAuth().getOtpExpiry();

        return userRepository.findByUsername(registerRequest.getUsername())
                .flatMap(existingUser -> {
                    if (!existingUser.isVerified()) {
                        // Resend verification email — generate new OTP, store in Redis, send email
                        int otp = generateOTP();
                        return otpRedisService.saveVerificationOtp(otp, existingUser.getId(), otpExpiry)
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
                                                role.setRole(registerRequest.getRole());

                                                int otp = generateOTP();
                                                return roleRepository.save(role)
                                                        .then(otpRedisService.saveVerificationOtp(otp, savedUser.getId(), otpExpiry))
                                                        .then(Mono.fromRunnable(() -> sendVerificationEmail(savedUser.getEmail(), otp))
                                                                .subscribeOn(Schedulers.boundedElastic())
                                                                .thenReturn(savedUser));
                                            });
                                }))
                ));
    }

    private void sendVerificationEmail(String email, int otp) {
        String subject = "Email Verification";
        String baseUrl = applicationProperties.getApp().getBaseUrl();
        String verifyURL = baseUrl + "/auths/verify-email?verifyOTP=" + otp;
        String content = "Please click the link below to verify your registration:<br>"
                + "<h3><a href=\"" + verifyURL + "\">VERIFY</a></h3>"
                + "Thank you,<br>Your company name.";

        AuthEmail authEmail = new AuthEmail(email, subject, content);
        emailService.sendVerifyLink(authEmail, verifyURL);
    }

    public Mono<String> verifyEmail(int verifyOTP) {
        return otpRedisService.getAndEvictVerificationOtp(verifyOTP)
                .flatMap(userId -> userRepository.findById(userId)
                        .flatMap(user -> {
                            user.setVerified(true); // Set the verified flag to true
                            return userRepository.save(user);
                        })
                        .thenReturn("Email verified successfully."))
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK)));
    }

    public Mono<String> forgetPassword(String email) {
        Duration resetTokenExpiry = applicationProperties.getAuth().getResetTokenExpiry();

        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000110_USER_NOT_REGISTERED)))
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
                                        .thenReturn("Do check your email for resetting password");
                            });
                });
    }

    public Mono<String> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        return otpRedisService.getAndEvictPasswordResetToken(resetPasswordRequest.getVerificationToken())
                .flatMap(userId -> userRepository.findById(userId)
                        .flatMap(user -> {
                            // Updated the password
                            user.setPassword(passwordEncoder.encode(resetPasswordRequest.getPassword()));
                            return userRepository.save(user);
                        })
                        .thenReturn("Password Reset successfully."))
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK)));
    }

    public Mono<String> logout(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000110_USER_NOT_REGISTERED)))
                .flatMap(user -> {
                    user.setActive(false);
                    return userRepository.save(user)
                            .thenReturn("Logout successful");
                });
    }

    private static int generateOTP() {
        return OTP_MIN + secureRandom.nextInt(OTP_RANGE);
    }
}
