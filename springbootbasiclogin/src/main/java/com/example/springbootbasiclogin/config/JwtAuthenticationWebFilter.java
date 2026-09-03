package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.service.jwt.JwtService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtService jwtService;

    @Autowired
    public JwtAuthenticationWebFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = jwtService.parseAndValidateToken(token);
                if (jwtService.isTokenType(claims, "ACCESS")) {
                    String username = claims.getSubject();
                    @SuppressWarnings("unchecked")
                    List<String> roles = claims.get("roles", List.class);
                    if (roles == null) {
                        roles = Collections.emptyList();
                    }

                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> {
                                String r = role.toUpperCase();
                                return r.startsWith("ROLE_") ? r : "ROLE_" + r;
                            })
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);

                    log.debug("Authenticated JWT user: {} with authorities: {}", username, authorities);

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                }
            } catch (Exception e) {
                log.warn("JWT authentication failed: {}", e.getMessage());
            }
        }

        return chain.filter(exchange);
    }
}
