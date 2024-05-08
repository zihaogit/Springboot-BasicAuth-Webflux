package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.entity.Roles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RoleService {

    Flux<Roles> findAll();

    Mono<Roles> findById(int theId);

    Mono<Roles> save(Roles theRoles);

    Mono<Roles> update(int id, Roles theRoles);

    Mono<Void> deleteById(int theId);
}