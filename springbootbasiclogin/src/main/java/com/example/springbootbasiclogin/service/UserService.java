package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.entity.Users;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public interface UserService {

    /* Auth and Autz */
    Mono<Boolean> isUsernameMatching(int userId, String username);

    Mono<Users> findByUsername(String username);

    /* Managing User Profile */
    Flux<Users> findAll();

    Mono<Users> findById(int theId);

    Mono<Users> save(Users theUser);

    Mono<Users> update(int id, Users theUser);

    Mono<Void> deleteById(int theId);
}