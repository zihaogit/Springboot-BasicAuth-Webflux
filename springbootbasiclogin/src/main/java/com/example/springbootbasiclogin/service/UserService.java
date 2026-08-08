package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.entity.Users;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public interface UserService {

    /* Managing User Profile */
    Flux<Users> findAll();

    Mono<Users> findById(int theId);

    Mono<Users> update(int id, Users theUser);

    Mono<String> deleteById(int theId);
}