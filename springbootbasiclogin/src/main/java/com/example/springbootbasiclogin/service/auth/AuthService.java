package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dto.auth.LoginRequest;
import com.example.springbootbasiclogin.dto.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dto.auth.RegisterRequest;
import com.example.springbootbasiclogin.dto.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.dto.auth.TokenResponse;
import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiterService loginRateLimiterService;
    private final TokenService tokenService;
    private final RegistrationService registrationService;
    private final PasswordService passwordService;

    // Find User + Roles using username required by basic auth and reactive user details service
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
                    if (Boolean.TRUE.equals(locked)) {
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
                                                            .map(roles -> tokenService.buildTokenResponse(savedUser, roles)));
                                        }));
                            });
                });
    }

    public Mono<TokenResponse> generateTokensForUser(String username) {
        return tokenService.generateTokensForUser(username);
    }

    public Mono<TokenResponse> refreshToken(RefreshTokenRequest request) {
        return tokenService.refreshToken(request);
    }

    public Mono<Users> registerUser(RegisterRequest registerRequest) {
        return registrationService.registerUser(registerRequest);
    }

    public Mono<String> verifyEmail(int verifyOTP) {
        return registrationService.verifyEmail(verifyOTP);
    }

    public Mono<String> verifyEmail(int verifyOTP, String email) {
        return registrationService.verifyEmail(verifyOTP, email);
    }

    public Mono<String> forgetPassword(String email) {
        return passwordService.forgetPassword(email);
    }

    public Mono<String> resetPassword(ResetPasswordRequest resetPasswordRequest) {
        return passwordService.resetPassword(resetPasswordRequest);
    }

    public Mono<String> logout(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000110_USER_NOT_REGISTERED)))
                .flatMap(user -> {
                    user.setActive(false);
                    return userRepository.save(user)
                            .then(tokenService.revokeUserTokens(username))
                            .thenReturn("Logout successful");
                });
    }
}
