package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.VerificationOTP;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface VerificationTokenRepository extends ReactiveCrudRepository<VerificationOTP, Long> {
    Mono<VerificationOTP> findByToken(String token);

    Mono<VerificationOTP> findByOtp(int otp);

    Mono<VerificationOTP> findByUserId(int userId);

    Mono<Void> deleteByUserId(int userId);
}