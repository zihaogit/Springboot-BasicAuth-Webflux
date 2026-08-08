package com.example.springbootbasiclogin.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "auth")
@Data
@Validated
public class AuthPropertiesConfig {

    @NotNull
    private MailConfig mailConfig = new MailConfig();

    @NotNull
    private JwtConfig jwt = new JwtConfig();

    @Data
    public static class MailConfig {
        @NotNull
        private String provider;
        @NotNull
        private String from;
        @NotNull
        private String name;
    }

    @Data
    public static class JwtConfig {
        @NotNull
        private String secret;
        private String issuer = "springboot-basiclogin";
        private Duration accessTokenTtl = Duration.ofHours(2);
        private Duration refreshTokenTtl = Duration.ofHours(24);
    }
}
