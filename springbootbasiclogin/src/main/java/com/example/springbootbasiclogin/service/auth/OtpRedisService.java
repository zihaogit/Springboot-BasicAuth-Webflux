package com.example.springbootbasiclogin.service.auth;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive Redis-backed service for managing OTP (email verification)
 * and password-reset tokens with automatic TTL expiration.
 */
public interface OtpRedisService {

    /**
     * Store a verification OTP in Redis with automatic expiration.
     *
     * @param otp    the 6-digit OTP code
     * @param userId the user's database ID
     * @param ttl    time-to-live before the key auto-expires
     * @return {@code true} if the key was set successfully
     */
    Mono<Boolean> saveVerificationOtp(int otp, int userId, Duration ttl);

    /**
     * Atomically retrieve and delete a verification OTP (single-use).
     *
     * @param otp the 6-digit OTP code
     * @return the associated userId, or empty Mono if expired / not found
     */
    Mono<Integer> getAndEvictVerificationOtp(int otp);

    /**
     * Store a password-reset token in Redis with automatic expiration.
     *
     * @param token  the UUID reset token
     * @param userId the user's database ID
     * @param ttl    time-to-live before the key auto-expires
     * @return {@code true} if the key was set successfully
     */
    Mono<Boolean> savePasswordResetToken(String token, int userId, Duration ttl);

    /**
     * Atomically retrieve and delete a password-reset token (single-use).
     *
     * @param token the UUID reset token
     * @return the associated userId, or empty Mono if expired / not found
     */
    Mono<Integer> getAndEvictPasswordResetToken(String token);
}
