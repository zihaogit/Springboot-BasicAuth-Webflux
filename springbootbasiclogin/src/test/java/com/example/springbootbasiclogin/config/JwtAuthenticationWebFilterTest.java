package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.service.jwt.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationWebFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private WebFilterChain chain;

    @Mock
    private Claims claims;

    @InjectMocks
    private JwtAuthenticationWebFilter filter;

    @BeforeEach
    void setUp() {
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Filter valid Bearer Access Token sets SecurityContext")
    void testFilterValidBearerAccessToken() {
        String token = "valid.jwt.access.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/users/all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.parseAndValidateToken(token)).thenReturn(claims);
        when(jwtService.isTokenType(claims, "ACCESS")).thenReturn(true);
        when(claims.getSubject()).thenReturn("admin");
        doReturn(List.of("ADMIN", "USER")).when(claims).get(eq("roles"), eq(List.class));

        // Stub chain filter to verify context is set inside downstream subscriber
        when(chain.filter(any())).thenReturn(
                ReactiveSecurityContextHolder.getContext()
                        .map(SecurityContext::getAuthentication)
                        .doOnNext(auth -> {
                            assertNotNull(auth);
                            assertEquals("admin", auth.getName());
                            assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
                            assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
                        })
                        .then()
        );

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(jwtService).parseAndValidateToken(token);
    }

    @Test
    @DisplayName("Filter Refresh Token does not set SecurityContext")
    void testFilterRefreshTokenIgnored() {
        String token = "valid.jwt.refresh.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/users/all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.parseAndValidateToken(token)).thenReturn(claims);
        when(jwtService.isTokenType(claims, "ACCESS")).thenReturn(false);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Filter invalid Bearer token continues filter chain safely")
    void testFilterInvalidBearerToken() {
        String token = "invalid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/users/all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.parseAndValidateToken(token)).thenThrow(new RuntimeException("Token expired"));

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Filter without Authorization header proceeds without auth")
    void testFilterNoAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/users/all").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(jwtService);
    }
}
