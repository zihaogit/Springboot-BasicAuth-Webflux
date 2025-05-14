package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.dao.auth.RegisterRequest;
import com.example.springbootbasiclogin.dao.auth.ResetPasswordRequest;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /* Auth and Autz */
    @Authenticated(roles = {"ADMIN", "USER"})
    @PostMapping("/login")
    public Mono<String> loginUser(ServerWebExchange exchange) {
        return Mono.just("Success Login! Yeah");
    }

    @PostMapping("/register")
    public Mono<Users> registerUser(@RequestBody @Valid RegisterRequest registerRequest) {
        return authService.registerUser(registerRequest);
    }

    @GetMapping("/verify-email/{verificationToken}")
    public Mono<String> verifyEmail(@PathVariable String verificationToken) {
        return authService.verifyEmail(verificationToken);
    }

    @PostMapping("/fp")
    public Mono<String> forgetPassword(@RequestParam String email) {
        return authService.forgetPassword(email);
    }

    @PostMapping("/reset-password")
    public Mono<String> resetPassword(
            @RequestBody @Valid ResetPasswordRequest resetPasswordRequest
    ) {
        return authService.resetPassword(resetPasswordRequest);
    }

    @GetMapping("/logout")
    public Mono<String> logout(@AuthenticationPrincipal UserDetails auth) {
        return authService.logout(auth.getUsername());
    }
}