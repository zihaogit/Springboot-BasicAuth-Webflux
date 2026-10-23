package com.example.springbootbasiclogin.service.user;

import com.example.springbootbasiclogin.dto.user.ChangePasswordRequest;
import com.example.springbootbasiclogin.dto.user.UserProfileUpdateRequest;
import com.example.springbootbasiclogin.entity.Users;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;

public interface UserService {

    /* Managing User Profile */
    Flux<Users> findAll();

    Flux<Users> findAll(int page, int size);

    Mono<Users> findById(int theId);

    Mono<Users> findByIdAuthorized(int theId, Principal principal);

    Mono<Users> update(int id, Users theUser);

    Mono<Users> updateProfile(int id, UserProfileUpdateRequest request, Principal principal);

    Mono<String> changePassword(int id, ChangePasswordRequest request, Principal principal);

    Mono<String> deleteById(int theId);
}