package com.example.springbootbasiclogin.service.jwt;

import com.example.springbootbasiclogin.config.AuthPropertiesConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class JwtService {

    private final AuthPropertiesConfig authPropertiesConfig;
    private final SecretKey secretKey;

    @Autowired
    public JwtService(AuthPropertiesConfig authPropertiesConfig) {
        this.authPropertiesConfig = authPropertiesConfig;
        String secret = authPropertiesConfig.getJwt().getSecret();
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            byte[] padded = Arrays.copyOf(secretBytes, 32);
            this.secretKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        }
    }

    public String generateAccessToken(String username, String email, List<String> roles) {
        Date now = new Date();
        long ttlMillis = authPropertiesConfig.getJwt().getAccessTokenTtl().toMillis();
        Date expiry = new Date(now.getTime() + ttlMillis);

        var builder = Jwts.builder()
                .subject(username)
                .claim("token_type", "ACCESS")
                .claim("roles", roles);

        if (email != null) {
            builder.claim("email", email);
        }

        return builder
                .issuer(authPropertiesConfig.getJwt().getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        long ttlMillis = authPropertiesConfig.getJwt().getRefreshTokenTtl().toMillis();
        Date expiry = new Date(now.getTime() + ttlMillis);

        return Jwts.builder()
                .subject(username)
                .claim("token_type", "REFRESH")
                .issuer(authPropertiesConfig.getJwt().getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw e;
        }
    }

    public boolean isTokenType(Claims claims, String expectedTokenType) {
        String tokenType = claims.get("token_type", String.class);
        return expectedTokenType.equalsIgnoreCase(tokenType);
    }
}
