package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.VerificationOTP;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends ReactiveCrudRepository<VerificationOTP, UUID> {
    Mono<VerificationOTP> findByToken(String token);

    Mono<VerificationOTP> findByOtp(int otp);

    Mono<VerificationOTP> findByUserId(int userId);

    Mono<Void> deleteByUserId(int userId);
}