package com.example.springbootbasiclogin.helper;

import com.example.springbootbasiclogin.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Slf4j
public class BasicAuthHelper {

    private final AuthService authService;

    @Autowired
    public BasicAuthHelper(AuthService authService) {
        this.authService = authService;
    }

    public Mono<Boolean> checkAuthentication(ServerWebExchange exchange) {
        // Access headers through exchange object
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authorizationHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            log.info("Invalid Authorization Header (missing or not Basic)");
            return Mono.just(false);
        }

        try {
            // Remove "Basic " Prefix and decode the authorization header
            String base64Credentials = authorizationHeader.substring(6).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);

            // Split username and password
            String[] parts = credentials.split(":", 2);
            if (parts.length == 2) {
                String username = parts[0];
                String password = parts[1];

                // check valid password or username here
                return authService.loginUser(username, password);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode Basic auth credentials: {}", e.getMessage());
        }
        return Mono.just(false);
    }
}
