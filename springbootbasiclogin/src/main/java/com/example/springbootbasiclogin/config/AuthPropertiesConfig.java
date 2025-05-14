package com.example.springbootbasiclogin.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "auth")
@Data
@Validated
public class AuthPropertiesConfig {

    @NotNull
    private MailConfig mailConfig = new MailConfig();

    @Data
    public static class MailConfig {
        @NotNull
        private String provider;
        @NotNull
        private String from;
        @NotNull
        private String name;
    }
}
