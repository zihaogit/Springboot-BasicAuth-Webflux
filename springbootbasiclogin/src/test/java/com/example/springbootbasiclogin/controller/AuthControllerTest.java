package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.auth.LoginRequest;
import com.example.springbootbasiclogin.dao.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dao.auth.TokenResponse;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private TokenResponse mockTokenResponse;

    @BeforeEach
    void setUp() {
        mockTokenResponse = TokenResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .tokenType("Bearer")
                .expiresIn(7200)
                .username("admin")
                .build();
    }

    @Test
    @DisplayName("Basic Auth Header Login Success")
    void testLoginWithBasicAuthHeaderSuccess() {
        String rawCredentials = "admin:admin12345";
        String encodedCredentials = Base64.getEncoder().encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + encodedCredentials;

        when(authService.loginUser(any(LoginRequest.class))).thenReturn(Mono.just(mockTokenResponse));

        Mono<TokenResponse> response = authController.loginUser(authHeader, null, null);

        StepVerifier.create(response)
                .expectNextMatches(token -> token.getAccessToken().equals("mock-access-token")
                        && token.getTokenType().equals("Bearer")
                        && token.getUsername().equals("admin"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Basic Auth Header Malformed Base64 Failure")
    void testLoginWithMalformedBasicAuthHeader() {
        String authHeader = "Basic invalid_base64_###";

        Mono<TokenResponse> response = authController.loginUser(authHeader, null, null);

        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof CustomException
                        && ((CustomException) throwable).getAuthResponseCode() == AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION)
                .verify();
    }

    @Test
    @DisplayName("JSON Body Login Success")
    void testLoginWithJsonBodySuccess() {
        LoginRequest loginRequest = new LoginRequest("admin", "admin12345");
        when(authService.loginUser(loginRequest)).thenReturn(Mono.just(mockTokenResponse));

        Mono<TokenResponse> response = authController.loginUser(null, loginRequest, null);

        StepVerifier.create(response)
                .expectNext(mockTokenResponse)
                .verifyComplete();
    }

    @Test
    @DisplayName("Principal Login Success")
    void testLoginWithPrincipalSuccess() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("admin");
        when(authService.generateTokensForUser("admin")).thenReturn(Mono.just(mockTokenResponse));

        Mono<TokenResponse> response = authController.loginUser(null, null, principal);

        StepVerifier.create(response)
                .expectNext(mockTokenResponse)
                .verifyComplete();
    }

    @Test
    @DisplayName("Login Missing All Credentials Failure")
    void testLoginMissingAllCredentials() {
        Mono<TokenResponse> response = authController.loginUser(null, null, null);

        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof CustomException
                        && ((CustomException) throwable).getAuthResponseCode() == AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER)
                .verify();
    }

    @Test
    @DisplayName("Refresh Token Success")
    void testRefreshTokenSuccess() {
        RefreshTokenRequest request = new RefreshTokenRequest("mock-refresh-token");
        when(authService.refreshToken(request)).thenReturn(Mono.just(mockTokenResponse));

        Mono<TokenResponse> response = authController.refreshToken(request);

        StepVerifier.create(response)
                .expectNext(mockTokenResponse)
                .verifyComplete();
    }

    @Test
    @DisplayName("Logout Success")
    void testLogoutSuccess() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("admin");
        when(authService.logout("admin")).thenReturn(Mono.just("Logout successful"));

        Mono<String> response = authController.logout(principal);

        StepVerifier.create(response)
                .expectNext("Logout successful")
                .verifyComplete();
    }

    @Test
    @DisplayName("Logout Without Principal Failure")
    void testLogoutWithoutPrincipal() {
        Mono<String> response = authController.logout(null);

        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof CustomException
                        && ((CustomException) throwable).getAuthResponseCode() == AuthResponseCode.AUTH_000401_UNAUTHORIZED)
                .verify();
    }
}
