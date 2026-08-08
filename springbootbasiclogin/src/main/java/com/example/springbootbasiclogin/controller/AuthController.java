package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.auth.LoginRequest;
import com.example.springbootbasiclogin.dao.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dao.auth.RegisterRequest;
import com.example.springbootbasiclogin.dao.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.dao.auth.TokenResponse;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

@RestController
@RequestMapping("/auths")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Mono<TokenResponse> loginUser(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody(required = false) LoginRequest loginRequest,
            Principal principal
    ) {
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring(6).trim();
                String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2) {
                    return authService.loginUser(new LoginRequest(parts[0], parts[1]));
                }
            } catch (Exception e) {
                return Mono.error(new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION, e));
            }
        }

        if (principal != null && principal.getName() != null) {
            return authService.generateTokensForUser(principal.getName());
        }

        if (loginRequest != null && loginRequest.getUsername() != null && loginRequest.getPassword() != null) {
            return authService.loginUser(loginRequest);
        }

        return Mono.error(new CustomException(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER));
    }

    @PostMapping("/refresh")
    public Mono<TokenResponse> refreshToken(@RequestBody @Valid RefreshTokenRequest refreshTokenRequest) {
        return authService.refreshToken(refreshTokenRequest);
    }

    @PostMapping("/register")
    public Mono<Users> registerUser(@RequestBody @Valid RegisterRequest registerRequest) {
        return authService.registerUser(registerRequest);
    }

    @GetMapping("/verify-email")
    public Mono<String> verifyEmail(@RequestParam(name = "verifyOTP") int verifyOTP) {
        return authService.verifyEmail(verifyOTP);
    }

    @PostMapping("/fp")
    public Mono<String> forgetPassword(@RequestParam String email) {
        return authService.forgetPassword(email);
    }

    @PostMapping("/reset-password")
    public Mono<String> resetPassword(@RequestBody @Valid ResetPasswordRequest resetPasswordRequest) {
        return authService.resetPassword(resetPasswordRequest);
    }

    @Authenticated(roles = {"ADMIN", "USER"})
    @GetMapping("/logout")
    public Mono<String> logout(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED));
        }
        return authService.logout(principal.getName());
    }
}