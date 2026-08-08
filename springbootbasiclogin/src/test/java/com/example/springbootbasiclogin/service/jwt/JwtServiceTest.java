package com.example.springbootbasiclogin.service.jwt;

import com.example.springbootbasiclogin.config.AuthPropertiesConfig;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthPropertiesConfig config = new AuthPropertiesConfig();
        config.getJwt().setSecret("very-secret-key-that-is-at-least-32-characters-long-12345");
        config.getJwt().setIssuer("test-issuer");
        jwtService = new JwtService(config);
    }

    @Test
    void testGenerateAndValidateAccessToken() {
        String token = jwtService.generateAccessToken("testuser", "test@example.com", List.of("USER", "ADMIN"));
        assertNotNull(token);

        Claims claims = jwtService.parseAndValidateToken(token);
        assertEquals("testuser", claims.getSubject());
        assertEquals("test@example.com", claims.get("email", String.class));
        assertTrue(jwtService.isTokenType(claims, "ACCESS"));

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertEquals(2, roles.size());
        assertTrue(roles.contains("USER"));
        assertTrue(roles.contains("ADMIN"));
    }

    @Test
    void testGenerateAndValidateRefreshToken() {
        String token = jwtService.generateRefreshToken("testuser");
        assertNotNull(token);

        Claims claims = jwtService.parseAndValidateToken(token);
        assertEquals("testuser", claims.getSubject());
        assertTrue(jwtService.isTokenType(claims, "REFRESH"));
        assertFalse(jwtService.isTokenType(claims, "ACCESS"));
    }
}
