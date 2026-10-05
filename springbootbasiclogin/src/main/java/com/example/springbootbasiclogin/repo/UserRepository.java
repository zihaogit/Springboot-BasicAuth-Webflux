package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.Users;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends ReactiveCrudRepository<Users, Integer> {

    Mono<Users> findByUsername(String username);

    Mono<Users> findByEmail(String email);

    Mono<Users> findByFusionAuthUserId(String fusionAuthUserId);

    @Query("SELECT username FROM users WHERE id = :id")
    Mono<String> findUsernameById(int id);

    @Query("SELECT * FROM users WHERE username = :username AND verified = true")
    Mono<Users> findByUsernameAndStatus(String username);

    @Query("SELECT u.id FROM users u WHERE u.verified = false AND NOT EXISTS (SELECT 1 FROM verification_otp vo WHERE vo.user_id = u.id)")
    Flux<Integer> findAbandonedUnverifiedUserIds();
}
