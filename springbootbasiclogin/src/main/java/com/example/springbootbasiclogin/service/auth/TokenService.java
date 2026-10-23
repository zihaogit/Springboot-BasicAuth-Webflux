package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dto.auth.RefreshTokenRequest;
import com.example.springbootbasiclogin.dto.auth.TokenResponse;
import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.service.jwt.JwtService;
import com.example.springbootbasiclogin.util.SanitizeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenService {

    private static final String REVOKED_TOKEN_PREFIX = "token:revoked:hash:";
    private static final String REVOKED_USER_PREFIX = "token:revoked:user:";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final ApplicationPropertiesConfig applicationProperties;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public TokenResponse buildTokenResponse(Users user, List<String> roles) {
        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());
        long expiresIn = applicationProperties.getAuth().getJwt().getAccessTokenTtl().toSeconds();

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .username(user.getUsername())
                .build();
    }

    public Mono<TokenResponse> generateTokensForUser(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000110_USER_NOT_REGISTERED)))
                .flatMap(user -> {
                    if (!user.isVerified()) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000202_INVALID_VERIFICATION_LINK));
                    }
                    if (!user.isActive()) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED));
                    }
                    return roleRepository.findByUserId(user.getId())
                            .map(Roles::getRole)
                            .collectList()
                            .map(roles -> buildTokenResponse(user, roles));
                });
    }

    public Mono<TokenResponse> refreshToken(RefreshTokenRequest request) {
        return Mono.fromCallable(() -> jwtService.parseAndValidateToken(request.getRefreshToken()))
                .onErrorMap(e -> new CustomException(AuthResponseCode.AUTH_000115_INVALID_REFRESH_TOKEN, e))
                .flatMap(claims -> {
                    if (!jwtService.isTokenType(claims, "REFRESH")) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000115_INVALID_REFRESH_TOKEN));
                    }
                    String username = claims.getSubject();
                    Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
                    return isTokenRevoked(request.getRefreshToken(), username, issuedAt)
                            .flatMap(revoked -> {
                                if (Boolean.TRUE.equals(revoked)) {
                                    log.warn("Attempt to use revoked refresh token for user '{}'", SanitizeUtil.forLog(username));
                                    return Mono.error(new CustomException(AuthResponseCode.AUTH_000115_INVALID_REFRESH_TOKEN));
                                }
                                Duration ttl = applicationProperties.getAuth().getJwt().getRefreshTokenTtl();
                                return revokeToken(request.getRefreshToken(), ttl)
                                        .then(generateTokensForUser(username));
                            });
                });
    }

    public Mono<Void> revokeToken(String token, Duration ttl) {
        String tokenKey = REVOKED_TOKEN_PREFIX + hashToken(token);
        return redisTemplate.opsForValue().set(tokenKey, "1", ttl).then();
    }

    public Mono<Void> revokeUserTokens(String username) {
        String userKey = REVOKED_USER_PREFIX + username.toLowerCase(Locale.ROOT).trim();
        long now = Instant.now().toEpochMilli();
        Duration maxTtl = applicationProperties.getAuth().getJwt().getRefreshTokenTtl();
        return redisTemplate.opsForValue().set(userKey, String.valueOf(now), maxTtl).then();
    }

    public Mono<Boolean> isTokenRevoked(String token, String username, Instant issuedAt) {
        String tokenKey = REVOKED_TOKEN_PREFIX + hashToken(token);
        String userKey = REVOKED_USER_PREFIX + username.toLowerCase(Locale.ROOT).trim();

        return redisTemplate.hasKey(tokenKey)
                .flatMap(tokenRevoked -> {
                    if (Boolean.TRUE.equals(tokenRevoked)) {
                        return Mono.just(true);
                    }
                    return redisTemplate.opsForValue().get(userKey)
                            .map(cutoffStr -> {
                                try {
                                    long cutoff = Long.parseLong(cutoffStr);
                                    return issuedAt != null && issuedAt.toEpochMilli() <= cutoff;
                                } catch (NumberFormatException e) {
                                    log.debug("Non-numeric revocation cutoff for user '{}', treating as not revoked", SanitizeUtil.forLog(username));
                                    return false;
                                }
                            })
                            .defaultIfEmpty(false);
                });
    }

    public String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(AuthResponseCode.AUTH_000500_SERVER_ERROR, e);
        }
    }
}
