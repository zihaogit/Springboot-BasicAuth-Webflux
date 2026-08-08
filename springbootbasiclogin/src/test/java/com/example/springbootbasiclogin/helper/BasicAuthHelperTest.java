package com.example.springbootbasiclogin.helper;

import com.example.springbootbasiclogin.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasicAuthHelperTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private BasicAuthHelper basicAuthHelper;

    @Test
    @DisplayName("Check authentication with valid Basic Auth header")
    void testCheckAuthenticationValidBasicAuthHeader() {
        String rawCredentials = "admin:admin12345";
        String encodedCredentials = Base64.getEncoder().encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + encodedCredentials;

        MockServerHttpRequest request = MockServerHttpRequest.get("/auths/login")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(authService.loginUser("admin", "admin12345")).thenReturn(Mono.just(true));

        Mono<Boolean> result = basicAuthHelper.checkAuthentication(exchange);

        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Check authentication without Basic Auth header returns false")
    void testCheckAuthenticationMissingHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auths/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Boolean> result = basicAuthHelper.checkAuthentication(exchange);

        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Check authentication with non-Basic header returns false")
    void testCheckAuthenticationNonBasicHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auths/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Boolean> result = basicAuthHelper.checkAuthentication(exchange);

        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }
}
