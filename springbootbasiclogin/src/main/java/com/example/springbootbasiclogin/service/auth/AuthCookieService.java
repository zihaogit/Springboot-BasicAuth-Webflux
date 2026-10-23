package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String ROOT_PATH = "/";
    private static final String AUTHS_PATH = "/auths";

    private final ApplicationPropertiesConfig applicationProperties;

    public AuthCookieService(ApplicationPropertiesConfig applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public ResponseCookie generateAccessTokenCookie(String token) {
        long accessTtl = applicationProperties.getAuth().getJwt().getAccessTokenTtl().getSeconds();
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, token != null ? token : "")
                .httpOnly(true)
                .sameSite("Lax")
                .path(ROOT_PATH)
                .maxAge(accessTtl)
                .build();
    }

    public ResponseCookie generateRefreshTokenCookie(String token) {
        long refreshTtl = applicationProperties.getAuth().getJwt().getRefreshTokenTtl().getSeconds();
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token != null ? token : "")
                .httpOnly(true)
                .sameSite("Strict")
                .path(AUTHS_PATH)
                .maxAge(refreshTtl)
                .build();
    }

    public ResponseCookie generateClearAccessTokenCookie() {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .path(ROOT_PATH)
                .maxAge(0)
                .build();
    }

    public ResponseCookie generateClearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .path(AUTHS_PATH)
                .maxAge(0)
                .build();
    }

    public void setAuthCookies(ServerWebExchange exchange, String accessToken, String refreshToken) {
        if (exchange == null) {
            return;
        }
        if (accessToken != null) {
            exchange.getResponse().addCookie(generateAccessTokenCookie(accessToken));
        }
        if (refreshToken != null) {
            exchange.getResponse().addCookie(generateRefreshTokenCookie(refreshToken));
        }
    }

    public void clearAuthCookies(ServerWebExchange exchange) {
        if (exchange == null) {
            return;
        }
        exchange.getResponse().addCookie(generateClearAccessTokenCookie());
        exchange.getResponse().addCookie(generateClearRefreshTokenCookie());
    }

    public Optional<String> extractAccessToken(ServerWebExchange exchange) {
        if (exchange == null) {
            return Optional.empty();
        }
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(ACCESS_TOKEN_COOKIE_NAME);
        return (cookie != null && !cookie.getValue().isBlank())
                ? Optional.of(cookie.getValue())
                : Optional.empty();
    }

    public Optional<String> extractRefreshToken(ServerWebExchange exchange) {
        if (exchange == null) {
            return Optional.empty();
        }
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(REFRESH_TOKEN_COOKIE_NAME);
        return (cookie != null && !cookie.getValue().isBlank())
                ? Optional.of(cookie.getValue())
                : Optional.empty();
    }
}
