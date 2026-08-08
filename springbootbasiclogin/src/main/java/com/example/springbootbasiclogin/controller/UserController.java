package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/all")
    @Authenticated(roles = {"ADMIN"})
    public Flux<Users> findAll(ServerWebExchange serverWebExchange) {
        return userService.findAll();
    }

    @GetMapping
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<Users> getUser(ServerWebExchange serverWebExchange, @RequestParam int userId) {
        return userService.findById(userId);
    }

    @PutMapping("/update")
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<Users> updateUser(ServerWebExchange serverWebExchange, @RequestParam int userId, @RequestBody Users users) {
        return userService.update(userId, users);
    }

    @DeleteMapping
    @Authenticated(roles = {"ADMIN"})
    public Mono<String> deleteUser(ServerWebExchange serverWebExchange,
                                   @RequestParam int userId,
                                   @AuthenticationPrincipal UserDetails auth) {
        return userService.deleteById(userId);
    }
}