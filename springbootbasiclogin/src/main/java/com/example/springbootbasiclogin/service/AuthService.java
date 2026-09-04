package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.config.AuthPropertiesConfig;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.auth.LoginRequest;
import com.example.springbootbasiclogin.dao.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dao.auth.RegisterRequest;
import com.example.springbootbasiclogin.dao.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.dao.auth.TokenResponse;
import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.entity.VerificationOTP;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.model.AuthEmail;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.repo.VerificationTokenRepository;
import com.example.springbootbasiclogin.service.jwt.JwtService;
import com.example.springbootbasiclogin.service.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class AuthService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final AuthPropertiesConfig authPropertiesConfig;

    private static final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            VerificationTokenRepository verificationTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            JwtService jwtService,
            AuthPropertiesConfig authPropertiesConfig) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.authPropertiesConfig = authPropertiesConfig;
    }

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
                                .roles(roles.toArray(new String[0]))
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
                            .flatMap(savedUser -> generateTokensForUser(savedUser.getUsername()));
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
                                    .map(roles -> {
                                        String accessToken = jwtService.generateAccessToken(savedUser.getUsername(),
                                                savedUser.getEmail(), roles);
                                        String refreshToken = jwtService.generateRefreshToken(savedUser.getUsername());
                                        long expiresIn = authPropertiesConfig.getJwt().getAccessTokenTtl().toSeconds();

                                        return TokenResponse.builder()
                                                .accessToken(accessToken)
                                                .refreshToken(refreshToken)
                                                .tokenType("Bearer")
                                                .expiresIn(expiresIn)
                                                .username(savedUser.getUsername())
                                                .build();
                                    }));
                });
    }

    public Mono<TokenResponse> refreshToken(RefreshTokenRequest request) {
        return Mono.fromCallable(() -> jwtService.parseAndValidateToken(request.getRefreshToken()))
                .onErrorMap(e -> new CustomException(AuthResponseCode.AUTH_000108_INVALID_JWT_OR_ACCESS_TOKEN, e))
                .flatMap(claims -> {
                    if (!jwtService.isTokenType(claims, "REFRESH")) {
                        return Mono
                                .error(new CustomException(AuthResponseCode.AUTH_000108_INVALID_JWT_OR_ACCESS_TOKEN));
                    }
                    String username = claims.getSubject();
                    return generateTokensForUser(username);
                });
    }

    public Mono<Users> registerUser(RegisterRequest registerRequest) {
        return userRepository.findByUsername(registerRequest.getUsername())
                .flatMap(existingUser -> {
                    if (!existingUser.isVerified()) {
                        // Resend verification email
                        return generateToken(existingUser)
                                .doOnSuccess(token -> {
                                    String subject = "Email Verification";
                                    String content = "Please click the link below to verify your registration:<br>"
                                            + "<h3><a href=\"[[URL]]\">VERIFY</a></h3>"
                                            + "Thank you,<br>Your company name.";

                                    String verifyURL = "http://localhost:8080/auths/verify-email?verifyOTP=" + token.getOtp();
                                    content = content.replace("[[URL]]", verifyURL);

                                    AuthEmail authEmail = new AuthEmail(existingUser.getEmail(), subject, content);
                                    emailService.sendVerifyLink(authEmail, verifyURL);
                                })
                                .thenReturn(existingUser);
                    } else {
                        // User is already verified
                        log.error("User is already registered and verified.");
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000109_USER_IS_REGISTERED));
                    }
                })
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

                                Mono<VerificationOTP> tokenMono = generateToken(savedUser);

                                return roleRepository.save(role)
                                        .then(tokenMono.doOnSuccess(token -> {
                                            String subject = "Email Verification";
                                            String content = "Please click the link below to verify your registration:<br>"
                                                    + "<h3><a href=\"[[URL]]\">VERIFY</a></h3>"
                                                    + "Thank you,<br>Your company name.";

                                            String verifyURL = "http://localhost:8080/auths/verify-email?verifyOTP=" + token.getOtp();
                                            content = content.replace("[[URL]]", verifyURL);

                                            AuthEmail authEmail = new AuthEmail(savedUser.getEmail(), subject, content);
                                            emailService.sendVerifyLink(authEmail, verifyURL);
                                        }))
                                        .thenReturn(savedUser);
                            });
                }));
    }

    public Mono<String> verifyEmail(int verifyOTP) {
        return verificationTokenRepository.findByOtp(verifyOTP)
                .flatMap(token -> {
                    if (isTokenExpired(token.getCreatedAt())) {
                        return Mono.just("Verification token has expired.");
                    }

                    return userRepository.findById(token.getUserId())
                            .flatMap(user -> {
                                user.setVerified(true); // Set the verified flag to true
                                return userRepository.save(user);
                            })
                            .thenReturn("Email verified successfully.");
                });
    }

    public Mono<String> forgetPassword(String email) {
        return userRepository.findByEmail(email)
                .flatMap(user -> {
                    Mono<VerificationOTP> savedToken = generateToken(user);
                    return savedToken
                            .doOnSuccess(tokenSaved -> {
                                String subject = "Reset Password";
                                String content = "Forget Password? Please click the link below to to change your password:<br>"
                                        + "<h3>http://localhost:8080/reset-password/" + tokenSaved.getToken() + "</h3>"
                                        + "Bye Bye, Regards from:<br>"
                                        + "Your company name.";

                                AuthEmail authEmail = new AuthEmail(user.getEmail(), subject, content);
                                emailService.sendVerifyLink(authEmail, tokenSaved.getToken());
                            })
                            .thenReturn("Do check your email for resetting password")
                            .defaultIfEmpty("Error! cannot save Verification Token");
                })
                .defaultIfEmpty("Email is not registered");
    }

    public Mono<String> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        return verificationTokenRepository.findByToken(resetPasswordRequest.getVerificationToken())
                .flatMap(token -> {
                    if (isTokenExpired(token.getCreatedAt())) {
                        return Mono.just("Verification token has expired.");
                    }

                    return userRepository.findById(token.getUserId())
                            .flatMap(user -> {
                                // Updated the password
                                user.setPassword(passwordEncoder.encode(resetPasswordRequest.getPassword()));
                                return userRepository.save(user);
                            })
                            .thenReturn("Password Reset successfully.");
                });
    }

    public Mono<String> logout(String username) {
        return userRepository.findByUsername(username)
                .flatMap(user -> {
                    user.setActive(false);
                    return userRepository.save(user)
                            .doOnSuccess(success -> SecurityContextHolder.clearContext())
                            .thenReturn("Logout successful");
                })
                .defaultIfEmpty("No User found with username: " + username);
    }

    private Mono<VerificationOTP> generateToken(Users savedUser) {
        return verificationTokenRepository.findByUserId(savedUser.getId())
                .flatMap(existingToken -> {
                    existingToken.setToken(UUID.randomUUID().toString());
                    existingToken.setOtp(generateOTP());
                    existingToken.setUpdatedAt(LocalDateTime.now());
                    if (existingToken.getCreatedAt() == null) {
                        existingToken.setCreatedAt(LocalDateTime.now());
                    }
                    return verificationTokenRepository.save(existingToken);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    VerificationOTP verificationOTP = new VerificationOTP();
                    verificationOTP.setId(UUID.randomUUID());
                    verificationOTP.setToken(UUID.randomUUID().toString());
                    verificationOTP.setOtp(generateOTP());
                    verificationOTP.setUserId(savedUser.getId());
                    verificationOTP.setCreatedAt(LocalDateTime.now());
                    verificationOTP.setUpdatedAt(LocalDateTime.now());
                    return verificationTokenRepository.save(verificationOTP);
                }));
    }

    private static int generateOTP() {
        return 100000 + secureRandom.nextInt(900000);
    }

    private boolean isTokenExpired(LocalDateTime creationTime) {
        if (creationTime == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(creationTime, now);
        return duration.toMinutes() > 5; // Token expires after 5 minutes
    }
}