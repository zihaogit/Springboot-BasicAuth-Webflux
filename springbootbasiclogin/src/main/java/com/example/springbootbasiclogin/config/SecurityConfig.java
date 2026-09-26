package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.exception.ErrorResponse;
import com.example.springbootbasiclogin.util.MessageUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(
            ReactiveUserDetailsService reactiveUserDetailsService, PasswordEncoder passwordEncoder) {
        UserDetailsRepositoryReactiveAuthenticationManager manager =
                new UserDetailsRepositoryReactiveAuthenticationManager(reactiveUserDetailsService);
        manager.setPasswordEncoder(passwordEncoder);
        return manager;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper,
            MessageUtil messageUtil, ReactiveAuthenticationManager authenticationManager,
            JwtAuthenticationWebFilter jwtAuthenticationWebFilter) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authenticationManager(authenticationManager)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/auths/register", "/auths/verify-email", "/auths/verify-email/**",
                                "/auths/fp", "/auths/reset-password", "/auths/login", "/auths/refresh",
                                "/auths/social/**", "/webhooks/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .httpBasic(basic -> basic.authenticationEntryPoint((exchange, e) -> Mono.defer(() -> {
                    AuthResponseCode code = AuthResponseCode.AUTH_000401_UNAUTHORIZED;
                    String message = messageUtil.getMessage(code.getMessageKey());

                    ErrorResponse response = ErrorResponse.builder()
                            .resultCode(code.getCode())
                            .resultMsg(message)
                            .build();

                    try {
                        byte[] bytes = objectMapper.writeValueAsBytes(response);
                        if (bytes == null) {
                            throw new IllegalStateException("Failed to serialize ErrorResponse");
                        }
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                        return exchange.getResponse().writeWith(Mono.just(buffer));
                    } catch (JsonProcessingException ex) {
                        return Mono.error(ex);
                    }
                })))
                // CSRF is disabled because this is a stateless REST API using JWT Bearer tokens
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
