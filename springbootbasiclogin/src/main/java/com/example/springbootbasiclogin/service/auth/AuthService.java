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

import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService implements ReactiveUserDetailsService {

    private static final int OTP_MIN = 100_000;
    private static final int OTP_RANGE = 900_000;
    private static final String REVOKED_TOKEN_PREFIX = "token:revoked:hash:";
    private static final String REVOKED_USER_PREFIX = "token:revoked:user:";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final ApplicationPropertiesConfig applicationProperties;
    private final OtpRedisService otpRedisService;
    private final LoginRateLimiterService loginRateLimiterService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

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
        return loginRateLimiterService.isLocked(request.getUsername())
                .flatMap(locked -> {
                    if (locked) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000403_ACCESS_DENIED));
                    }
                    return userRepository.findByUsername(request.getUsername())
                            .switchIfEmpty(loginRateLimiterService.recordFailedAttempt(request.getUsername())
                                    .then(Mono.error(new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION))))
                            .flatMap(user -> {
                                if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                                    return loginRateLimiterService.recordFailedAttempt(request.getUsername())
                                            .then(Mono.error(new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION)));
                                }
                                if (!user.isVerified()) {
                                    return Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK));
                                }
                                return loginRateLimiterService.resetAttempts(request.getUsername())
                                        .then(Mono.defer(() -> {
                                            user.setActive(true);
                                            return userRepository.save(user)
                                                    .flatMap(savedUser -> roleRepository.findByUserId(savedUser.getId())
                                                             .map(Roles::getRole)
                                                             .collectList()
                                                             .map(roles -> buildTokenResponse(savedUser, roles)));
                                        }));
                            });
                });
    }

    public Mono<TokenResponse> generateTokensForUser(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000110_USER_NOT_REGISTERED)))
                .flatMap(user -> {
                    if (!user.isVerified()) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED));
                    }
                    return roleRepository.findByUserId(user.getId())
                            .map(Roles::getRole)
                            .collectList()
                            .map(roles -> buildTokenResponse(user, roles));
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
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000115_INVALID_REFRESH_TOKEN));
                    }
                    String username = claims.getSubject();
                    Date issuedAt = claims.getIssuedAt();
                    return isTokenRevoked(request.getRefreshToken(), username, issuedAt)
                            .flatMap(revoked -> {
                                if (Boolean.TRUE.equals(revoked)) {
                                    log.warn("Attempt to use revoked refresh token for user '{}'", username);
                                    return Mono.error(new CustomException(AuthResponseCode.AUTH_000115_INVALID_REFRESH_TOKEN));
                                }
                                Duration ttl = applicationProperties.getAuth().getJwt().getRefreshTokenTtl();
                                return revokeToken(request.getRefreshToken(), ttl)
                                        .then(generateTokensForUser(username));
                            });
                });
    }

    private String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(token.hashCode());
        }
    }

    private Mono<Boolean> isTokenRevoked(String token, String username, Date issuedAt) {
        String tokenKey = REVOKED_TOKEN_PREFIX + hashToken(token);
        String userKey = REVOKED_USER_PREFIX + username.toLowerCase().trim();

        return redisTemplate.hasKey(tokenKey)
                .flatMap(tokenRevoked -> {
                    if (Boolean.TRUE.equals(tokenRevoked)) {
                        return Mono.just(true);
                    }
                    return redisTemplate.opsForValue().get(userKey)
                            .map(cutoffStr -> {
                                try {
                                    long cutoff = Long.parseLong(cutoffStr);
                                    return issuedAt != null && issuedAt.getTime() <= cutoff;
                                } catch (NumberFormatException e) {
                                    return false;
                                }
                            })
                            .defaultIfEmpty(false);
                });
    }

    private Mono<Void> revokeToken(String token, Duration ttl) {
        String tokenKey = REVOKED_TOKEN_PREFIX + hashToken(token);
        return redisTemplate.opsForValue().set(tokenKey, "1", ttl).then();
    }

    private Mono<Void> revokeUserTokens(String username) {
        String userKey = REVOKED_USER_PREFIX + username.toLowerCase().trim();
        long now = System.currentTimeMillis();
        Duration maxTtl = applicationProperties.getAuth().getJwt().getRefreshTokenTtl();
        return redisTemplate.opsForValue().set(userKey, String.valueOf(now), maxTtl).then();
    }

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

    private void sendVerificationEmail(String email, int otp) {
        String subject = "Email Verification";
        String baseUrl = applicationProperties.getApp().getBaseUrl();
        String encodedEmail = java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
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
                            user.setVerified(true); // Set the verified flag to true
                            return userRepository.save(user);
                        })
                        .thenReturn("Email verified successfully."))
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK)));
    }

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
                            .then(revokeUserTokens(username))
                            .thenReturn("Logout successful");
                });
    }

    private static int generateOTP() {
        return OTP_MIN + secureRandom.nextInt(OTP_RANGE);
    }
}
