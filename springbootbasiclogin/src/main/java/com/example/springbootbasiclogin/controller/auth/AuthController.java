package com.example.springbootbasiclogin.controller.auth;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dto.BaseResponse;
import com.example.springbootbasiclogin.dto.auth.LoginRequest;
import com.example.springbootbasiclogin.dto.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dto.auth.RegisterRequest;
import com.example.springbootbasiclogin.dto.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.dto.auth.SocialCallbackRequest;
import com.example.springbootbasiclogin.dto.auth.TokenResponse;
import com.example.springbootbasiclogin.dto.user.UserResponse;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.service.auth.AuthCookieService;
import com.example.springbootbasiclogin.service.auth.AuthService;
import com.example.springbootbasiclogin.service.auth.SocialAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/auths")
@Validated
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final AuthCookieService authCookieService;

    public AuthController(
            AuthService authService,
            SocialAuthService socialAuthService,
            AuthCookieService authCookieService
    ) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public Mono<BaseResponse<TokenResponse>> loginUser(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody(required = false) LoginRequest loginRequest,
            ServerWebExchange exchange
    ) {
        return extractCredentials(authHeader, loginRequest)
                .flatMap(authService::loginUser)
                .doOnNext(tokens -> authCookieService.setAuthCookies(exchange, tokens.getAccessToken(), tokens.getRefreshToken()))
                .map(BaseResponse::success);
    }

    private Mono<LoginRequest> extractCredentials(String authHeader, LoginRequest loginRequest) {
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            return Mono.fromCallable(() -> {
                String base64Credentials = authHeader.substring(6).trim();
                String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    return new LoginRequest(parts[0], parts[1]);
                }
                throw new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION);
            }).onErrorMap(e -> e instanceof CustomException ? e : new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION, e));
        }

        if (loginRequest != null && loginRequest.getUsername() != null && !loginRequest.getUsername().isBlank()
                && loginRequest.getPassword() != null && !loginRequest.getPassword().isBlank()) {
            return Mono.just(loginRequest);
        }

        return Mono.error(new CustomException(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER));
    }

    @PostMapping("/refresh")
    public Mono<BaseResponse<TokenResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
            ServerWebExchange exchange
    ) {
        String token = null;
        if (refreshTokenRequest != null && refreshTokenRequest.getRefreshToken() != null && !refreshTokenRequest.getRefreshToken().isBlank()) {
            token = refreshTokenRequest.getRefreshToken();
        } else if (exchange != null) {
            token = authCookieService.extractRefreshToken(exchange).orElse(null);
        }

        if (token == null || token.isBlank()) {
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER));
        }

        return authService.refreshToken(new RefreshTokenRequest(token))
                .doOnNext(tokens -> authCookieService.setAuthCookies(exchange, tokens.getAccessToken(), tokens.getRefreshToken()))
                .map(BaseResponse::success);
    }

    @PostMapping("/register")
    public Mono<BaseResponse<UserResponse>> registerUser(@RequestBody @Valid RegisterRequest registerRequest) {
        return authService.registerUser(registerRequest)
                .map(user -> BaseResponse.success(UserResponse.from(user)));
    }

    @GetMapping("/verify-email")
    public Mono<BaseResponse<String>> verifyEmail(
            @RequestParam(name = "verifyOTP") int verifyOTP,
            @RequestParam(name = "email", required = false) String email
    ) {
        return authService.verifyEmail(verifyOTP, email)
                .map(BaseResponse::success);
    }

    @PostMapping("/fp")
    public Mono<BaseResponse<String>> forgetPassword(@RequestParam String email) {
        return authService.forgetPassword(email)
                .map(BaseResponse::success);
    }

    @PostMapping("/reset-password")
    public Mono<BaseResponse<String>> resetPassword(@RequestBody @Valid ResetPasswordRequest resetPasswordRequest) {
        return authService.resetPassword(resetPasswordRequest)
                .map(BaseResponse::success);
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
    public Mono<BaseResponse<Map<String, String>>> getSocialLoginUrl(@RequestParam(name = "provider", required = false) String provider) {
        return socialAuthService.generateAndSaveOAuthState()
                .map(state -> Map.of(
                        "state", state,
                        "authorizeUrl", socialAuthService.getAuthorizeUrl(provider, state)
                ))
                .map(BaseResponse::success);
    }

    @GetMapping("/social/callback")
    public Mono<BaseResponse<TokenResponse>> socialCallbackGet(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "state", required = false) String state,
            ServerWebExchange exchange
    ) {
        return socialAuthService.handleCallback(code, state)
                .doOnNext(tokens -> authCookieService.setAuthCookies(exchange, tokens.getAccessToken(), tokens.getRefreshToken()))
                .map(BaseResponse::success);
    }

    @PostMapping("/social/callback")
    public Mono<BaseResponse<TokenResponse>> socialCallbackPost(
            @RequestBody @Valid SocialCallbackRequest request,
            ServerWebExchange exchange
    ) {
        return socialAuthService.handleCallback(request.getCode(), request.getState())
                .doOnNext(tokens -> authCookieService.setAuthCookies(exchange, tokens.getAccessToken(), tokens.getRefreshToken()))
                .map(BaseResponse::success);
    }

    @Authenticated(roles = {"ADMIN", "USER"})
    @PostMapping("/logout")
    public Mono<BaseResponse<String>> logout(Principal principal, ServerWebExchange exchange) {
        if (principal == null || principal.getName() == null) {
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED));
        }
        authCookieService.clearAuthCookies(exchange);
        return authService.logout(principal.getName())
                .map(BaseResponse::success);
    }
}
