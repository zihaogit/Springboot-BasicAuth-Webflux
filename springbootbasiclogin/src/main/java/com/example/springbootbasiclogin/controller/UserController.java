package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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
    public Flux<Users> findAll() {
        return userService.findAll();
    }

    @GetMapping
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<Users> getUser(@RequestParam int userId) {
        return userService.findById(userId);
    }

    @PutMapping("/update")
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<Users> updateUser(@RequestParam int userId, @RequestBody Users users) {
        return userService.update(userId, users);
    }

    @DeleteMapping
    @Authenticated(roles = {"ADMIN"})
    public Mono<String> deleteUser(@RequestParam int userId) {
        return userService.deleteById(userId);
    }
}