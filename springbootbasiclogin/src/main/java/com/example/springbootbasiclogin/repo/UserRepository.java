package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.Users;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends ReactiveCrudRepository<Users, Integer> {

    Mono<Users> findByUsername(String username);

    Mono<Users> findByEmail(String email);

    @Query("SELECT username FROM users WHERE id = :id")
    Mono<String> findUsernameById(@Param("id") int id);
}