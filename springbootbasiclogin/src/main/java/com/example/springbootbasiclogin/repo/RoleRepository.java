package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.Roles;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface  RoleRepository extends ReactiveCrudRepository<Roles, Integer> {

    Flux<Roles> findByUserId(int userId);

    Mono<Void> deleteByUserId(int userId);
}