package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.dao.user.ChangePasswordRequest;
import com.example.springbootbasiclogin.dao.user.UserProfileUpdateRequest;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/all")
    @Authenticated(roles = {"ADMIN"})
    public Flux<Users> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return userService.findAll(page, size);
    }

    @GetMapping
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<Users> getUser(@RequestParam int userId, Principal principal) {
        return userService.findByIdAuthorized(userId, principal);
    }

    @PutMapping("/update")
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<Users> updateUser(
            @RequestParam int userId,
            @RequestBody @Valid UserProfileUpdateRequest updateRequest,
            Principal principal
    ) {
        return userService.updateProfile(userId, updateRequest, principal);
    }

    @PutMapping("/change-password")
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<String> changePassword(
            @RequestParam int userId,
            @RequestBody @Valid ChangePasswordRequest passwordRequest,
            Principal principal
    ) {
        return userService.changePassword(userId, passwordRequest, principal);
    }

    @DeleteMapping
    @Authenticated(roles = {"ADMIN"})
    public Mono<String> deleteUser(@RequestParam int userId) {
        return userService.deleteById(userId);
    }
}