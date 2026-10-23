package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.util.SanitizeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Distributed reactive rate limiter using Redis to protect against
 * brute-force authentication and credential stuffing attacks (M-04).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoginRateLimiterService {

    private static final String LOGIN_FAIL_PREFIX = "rate:login:failed:";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_WINDOW = Duration.ofMinutes(5);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public Mono<Boolean> isLocked(String username) {
        if (username == null || username.isBlank()) {
            return Mono.just(false);
        }
        String key = buildKey(username);
        return redisTemplate.opsForValue().get(key)
                .map(val -> {
                    try {
                        int attempts = Integer.parseInt(val);
                        boolean locked = attempts >= MAX_FAILED_ATTEMPTS;
                        if (locked) {
                            log.warn("Login attempt rejected for user '{}' — account temporarily rate-limited ({} attempts)", SanitizeUtil.forLog(username), attempts);
                        }
                        return locked;
                    } catch (NumberFormatException e) {
                        log.debug("Non-numeric rate-limit value for '{}', treating as unlocked", SanitizeUtil.forLog(username));
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    public Mono<Void> recordFailedAttempt(String username) {
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }
        String key = buildKey(username);
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    log.debug("Incremented failed login attempt counter for '{}' to {}", SanitizeUtil.forLog(username), count);
                    if (count != null && count == 1L) {
                        return redisTemplate.expire(key, LOCK_WINDOW).then();
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> resetAttempts(String username) {
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }
        String key = buildKey(username);
        return redisTemplate.delete(key)
                .doOnSuccess(deleted -> log.debug("Reset failed login attempts for '{}' (deleted: {})", SanitizeUtil.forLog(username), deleted))
                .then();
    }

    private String buildKey(String username) {
        return LOGIN_FAIL_PREFIX + username.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
