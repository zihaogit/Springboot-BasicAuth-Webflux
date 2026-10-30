package com.example.springbootbasiclogin.service.jwt;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private ApplicationPropertiesConfig propertiesConfig;

    @BeforeEach
    void setUp() {
        propertiesConfig = new ApplicationPropertiesConfig();
        ApplicationPropertiesConfig.JwtConfig jwtConfig = propertiesConfig.getAuth().getJwt();
        jwtConfig.setSecret("my-super-secret-key-that-is-at-least-32-bytes-long!!");
        jwtConfig.setIssuer("springboot-basiclogin");
        jwtConfig.setAccessTokenTtl(Duration.ofHours(2));
        jwtConfig.setRefreshTokenTtl(Duration.ofHours(24));

        jwtService = new JwtService(propertiesConfig);
    }

    @Test
    @DisplayName("Should generate and validate access token successfully")
    void shouldGenerateAndValidateAccessToken() {
        String token = jwtService.generateAccessToken("testuser", "test@example.com", List.of("ROLE_USER"));
        assertNotNull(token);

        Claims claims = jwtService.parseAndValidateToken(token);
        assertEquals("testuser", claims.getSubject());
        assertEquals("test@example.com", claims.get("email", String.class));
        assertEquals("ACCESS", claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class));
        assertTrue(jwtService.isTokenType(claims, "ACCESS"));
        assertFalse(jwtService.isTokenType(claims, "REFRESH"));
    }

    @Test
    @DisplayName("Should generate and validate refresh token successfully")
    void shouldGenerateAndValidateRefreshToken() {
        String token = jwtService.generateRefreshToken("testuser");
        assertNotNull(token);

        Claims claims = jwtService.parseAndValidateToken(token);
        assertEquals("testuser", claims.getSubject());
        assertEquals("REFRESH", claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class));
        assertTrue(jwtService.isTokenType(claims, "REFRESH"));
        assertFalse(jwtService.isTokenType(claims, "ACCESS"));
    }
}
