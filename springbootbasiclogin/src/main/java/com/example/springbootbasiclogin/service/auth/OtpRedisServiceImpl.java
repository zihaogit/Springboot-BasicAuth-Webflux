package com.example.springbootbasiclogin.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpRedisServiceImpl implements OtpRedisService {

    private static final String OTP_VERIFY_PREFIX = "otp:verify:";
    private static final String TOKEN_RESET_PREFIX = "token:reset:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Boolean> saveVerificationOtp(int otp, int userId, Duration ttl) {
        String key = OTP_VERIFY_PREFIX + otp;
        String value = String.valueOf(userId);
        log.debug("Saving verification OTP for userId={} ttl={}s", userId, ttl.toSeconds());
        return redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public Mono<Integer> getAndEvictVerificationOtp(int otp) {
        String key = OTP_VERIFY_PREFIX + otp;
        // getAndDelete is the reactive equivalent of GETDEL (atomic get + delete)
        return redisTemplate.opsForValue().getAndDelete(key)
                .flatMap(this::safeParseInteger)
                .doOnNext(userId -> log.debug("Evicted verification OTP for userId={}", userId));
    }

    @Override
    public Mono<Boolean> savePasswordResetToken(String token, int userId, Duration ttl) {
        String key = TOKEN_RESET_PREFIX + token;
        String value = String.valueOf(userId);
        log.debug("Saving password reset token for userId={} ttl={}s", userId, ttl.toSeconds());
        return redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public Mono<Integer> getAndEvictPasswordResetToken(String token) {
        String key = TOKEN_RESET_PREFIX + token;
        return redisTemplate.opsForValue().getAndDelete(key)
                .flatMap(this::safeParseInteger)
                .doOnNext(userId -> log.debug("Evicted password reset token for userId={}", userId));
    }

    private Mono<Integer> safeParseInteger(String value) {
        try {
            return Mono.just(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            log.error("Failed to parse integer from Redis value: {}", e.getMessage());
            return Mono.empty();
        }
    }
}
