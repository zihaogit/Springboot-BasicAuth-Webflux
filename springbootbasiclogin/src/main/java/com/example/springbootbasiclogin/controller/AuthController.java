package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.auth.LoginRequest;
import com.example.springbootbasiclogin.dao.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dao.auth.RegisterRequest;
import com.example.springbootbasiclogin.dao.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.dao.auth.SocialCallbackRequest;
import com.example.springbootbasiclogin.dao.auth.TokenResponse;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.service.auth.AuthService;
import com.example.springbootbasiclogin.service.auth.SocialAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;

import java.security.Principal;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/auths")
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final ApplicationPropertiesConfig applicationProperties;

    public AuthController(
            AuthService authService,
            SocialAuthService socialAuthService,
            ApplicationPropertiesConfig applicationProperties
    ) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.applicationProperties = applicationProperties;
    }

    @PostMapping("/login")
    public Mono<TokenResponse> loginUser(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody(required = false) LoginRequest loginRequest,
            ServerWebExchange exchange
    ) {
        return extractCredentials(authHeader, loginRequest)
                .flatMap(authService::loginUser)
                .doOnNext(tokens -> setAuthCookies(exchange, tokens));
    }

    private Mono<LoginRequest> extractCredentials(String authHeader, LoginRequest loginRequest) {
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring(6).trim();
                String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    return Mono.just(new LoginRequest(parts[0], parts[1]));
                }
                return Mono.error(new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION));
            } catch (Exception e) {
                return Mono.error(new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION, e));
            }
        }

        if (loginRequest != null && loginRequest.getUsername() != null && !loginRequest.getUsername().isBlank()
                && loginRequest.getPassword() != null && !loginRequest.getPassword().isBlank()) {
            return Mono.just(loginRequest);
        }

        return Mono.error(new CustomException(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER));
    }

    @PostMapping("/refresh")
    public Mono<TokenResponse> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
            ServerWebExchange exchange
    ) {
        String token = null;
        if (refreshTokenRequest != null && refreshTokenRequest.getRefreshToken() != null && !refreshTokenRequest.getRefreshToken().isBlank()) {
            token = refreshTokenRequest.getRefreshToken();
        } else if (exchange != null) {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("refresh_token");
            if (cookie != null && !cookie.getValue().isBlank()) {
                token = cookie.getValue();
            }
        }

        if (token == null || token.isBlank()) {
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER));
        }

        return authService.refreshToken(new RefreshTokenRequest(token))
                .doOnNext(tokens -> setAuthCookies(exchange, tokens));
    }

    @PostMapping("/register")
    public Mono<Users> registerUser(@RequestBody @Valid RegisterRequest registerRequest) {
        return authService.registerUser(registerRequest);
    }

    @GetMapping("/verify-email")
    public Mono<String> verifyEmail(
            @RequestParam(name = "verifyOTP") int verifyOTP,
            @RequestParam(name = "email", required = false) String email
    ) {
        return authService.verifyEmail(verifyOTP, email);
    }

    @PostMapping("/fp")
    public Mono<String> forgetPassword(@RequestParam String email) {
        return authService.forgetPassword(email);
    }

    @PostMapping("/reset-password")
    public Mono<String> resetPassword(@RequestBody @Valid ResetPasswordRequest resetPasswordRequest) {
        return authService.resetPassword(resetPasswordRequest);
    }

    @GetMapping("/social/login")
    public Mono<ResponseEntity<Void>> socialLogin(@RequestParam(name = "provider", required = false) String provider) {
        return socialAuthService.generateAndSaveOAuthState()
                .map(state -> {
                    String redirectUrl = socialAuthService.getAuthorizeUrl(provider, state);
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(redirectUrl))
                            .build();
                });
    }

    @GetMapping("/social/url")
    public Mono<Map<String, String>> getSocialLoginUrl(@RequestParam(name = "provider", required = false) String provider) {
        return socialAuthService.generateAndSaveOAuthState()
                .map(state -> Map.of(
                        "state", state,
                        "authorizeUrl", socialAuthService.getAuthorizeUrl(provider, state)
                ));
    }

    @GetMapping("/social/callback")
    public Mono<TokenResponse> socialCallbackGet(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "state", required = false) String state,
            ServerWebExchange exchange
    ) {
        return socialAuthService.handleCallback(code, state)
                .doOnNext(tokens -> setAuthCookies(exchange, tokens));
    }

    @PostMapping("/social/callback")
    public Mono<TokenResponse> socialCallbackPost(
            @RequestBody @Valid SocialCallbackRequest request,
            ServerWebExchange exchange
    ) {
        return socialAuthService.handleCallback(request.getCode(), request.getState())
                .doOnNext(tokens -> setAuthCookies(exchange, tokens));
    }

    @Authenticated(roles = {"ADMIN", "USER"})
    @PostMapping("/logout")
    public Mono<String> logout(Principal principal, ServerWebExchange exchange) {
        if (principal == null || principal.getName() == null) {
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED));
        }
        clearAuthCookies(exchange);
        return authService.logout(principal.getName());
    }

    private void setAuthCookies(ServerWebExchange exchange, TokenResponse tokens) {
        if (tokens == null || exchange == null) {
            return;
        }
        long accessTtl = applicationProperties.getAuth().getJwt().getAccessTokenTtl().getSeconds();
        long refreshTtl = applicationProperties.getAuth().getJwt().getRefreshTokenTtl().getSeconds();

        ResponseCookie accessCookie = ResponseCookie.from("access_token", tokens.getAccessToken())
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTtl)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", tokens.getRefreshToken())
                .httpOnly(true)
                .sameSite("Strict")
                .path("/auths")
                .maxAge(refreshTtl)
                .build();

        exchange.getResponse().addCookie(accessCookie);
        exchange.getResponse().addCookie(refreshCookie);
    }

    private void clearAuthCookies(ServerWebExchange exchange) {
        if (exchange == null) {
            return;
        }
        ResponseCookie clearAccess = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie clearRefresh = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path("/auths")
                .maxAge(0)
                .build();

        exchange.getResponse().addCookie(clearAccess);
        exchange.getResponse().addCookie(clearRefresh);
    }
}