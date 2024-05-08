package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.VerificationToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface VerificationTokenRepository extends ReactiveCrudRepository<VerificationToken, Long> {
    Mono<VerificationToken> findByToken(String token);
    Mono<VerificationToken> findByUserId(int userId);

    Mono<Void> deleteByUserId(int userId);
}