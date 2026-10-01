package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.service.jwt.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
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

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;

    public JwtAuthenticationWebFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = jwtService.parseAndValidateToken(token);
                if (jwtService.isTokenType(claims, "ACCESS")) {
                    String username = claims.getSubject();
                    List<String> roles = extractRoles(claims);

                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> {
                                String r = role.toUpperCase();
                                return r.startsWith(ROLE_PREFIX) ? r : ROLE_PREFIX + r;
                            })
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);

                    log.debug("Authenticated JWT user: {} with authorities: {}", username, authorities);

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("JWT authentication failed: {}", e.getMessage());
            }
        }

        return chain.filter(exchange);
    }

    private List<String> extractRoles(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return Collections.emptyList();
    }
}
