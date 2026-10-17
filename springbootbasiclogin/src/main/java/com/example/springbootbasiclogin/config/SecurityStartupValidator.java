package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Validates critical security settings at application startup.
 * Enforces that required cryptographic secrets are configured and meet security baselines (C-02).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityStartupValidator implements ApplicationRunner {

    private final Environment environment;
    private final ApplicationPropertiesConfig applicationProperties;

    @Override
    public void run(ApplicationArguments args) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isProd = activeProfiles.contains("prod") || activeProfiles.contains("production");

        String jwtSecret = applicationProperties.getAuth().getJwt().getSecret();
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.trim().length() < 32) {
            throw new CustomException(
                    AuthResponseCode.AUTH_000105_INVALID_SECRET_KEY,
                    "CRITICAL SECURITY VIOLATION: JWT_SECRET must be set via environment variable and be at least 32 characters long!"
            );
        }

        if (isProd) {
            String webhookSecret = applicationProperties.getAuth().getWebhook().getSecret();
            if (webhookSecret == null || webhookSecret.isBlank()) {
                throw new CustomException(
                        AuthResponseCode.AUTH_000105_INVALID_SECRET_KEY,
                        "CRITICAL SECURITY VIOLATION: FUSIONAUTH_WEBHOOK_SECRET must be set via environment variable in production!"
                );
            }

            String webhookSignatureKey = applicationProperties.getAuth().getWebhook().getSignatureKey();
            if (webhookSignatureKey == null || webhookSignatureKey.isBlank()) {
                throw new CustomException(
                        AuthResponseCode.AUTH_000105_INVALID_SECRET_KEY,
                        "CRITICAL SECURITY VIOLATION: FUSIONAUTH_WEBHOOK_SIGNATURE_KEY must be set via environment variable in production!"
                );
            }
        }
    }
}
