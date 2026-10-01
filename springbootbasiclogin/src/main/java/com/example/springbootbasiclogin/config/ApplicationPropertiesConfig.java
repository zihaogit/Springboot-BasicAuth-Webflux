package com.example.springbootbasiclogin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Complete typed representation of application.yml.
 *
 * <p>The root class maps every top-level YAML section. Each parent object is
 * initialized and marked {@link Valid}, and every configured child field is
 * validated when the application starts.</p>
 */
@Configuration
@ConfigurationProperties
@Validated
@Data
public class ApplicationPropertiesConfig {

    @NotNull
    @Valid
    private SpringProperties spring = new SpringProperties();

    @NotNull
    @Valid
    private AppProperties app = new AppProperties();

    @NotNull
    @Valid
    private LoggingProperties logging = new LoggingProperties();

    @NotNull
    @Valid
    private SpringdocProperties springdoc = new SpringdocProperties();

    @NotNull
    @Valid
    private AuthProperties auth = new AuthProperties();

    @Bean
    public ZoneId zoneId() {
        return ZoneId.of(app.getTimezone());
    }

    @Data
    public static class SpringProperties {

        @NotNull
        @Valid
        private R2dbcProperties r2dbc = new R2dbcProperties();

        @NotNull
        @Valid
        private FlywayProperties flyway = new FlywayProperties();

        @NotNull
        @Valid
        private JacksonProperties jackson = new JacksonProperties();

        @NotNull
        @Valid
        private MainProperties main = new MainProperties();
    }

    @Data
    public static class R2dbcProperties {

        @NotNull
        @NotBlank
        private String username;

        @NotNull
        @NotBlank
        private String password;

        @NotNull
        @NotBlank
        private String url;
    }

    @Data
    public static class FlywayProperties {

        @NotNull
        @NotBlank
        private String username;

        @NotNull
        @NotBlank
        private String password;

        @NotNull
        @NotBlank
        private String url;

        @NotNull
        @NotEmpty
        private List<@NotNull @NotBlank String> locations;

        @NotNull
        private Boolean baselineOnMigrate;
    }

    @Data
    public static class JacksonProperties {

        @NotNull
        @NotBlank
        private String timeZone;
    }

    @Data
    public static class MainProperties {

        @NotNull
        @NotBlank
        private String bannerMode;
    }

    @Data
    public static class AppProperties {

        @NotNull
        @NotBlank
        private String timezone;

        @NotNull
        @NotBlank
        private String baseUrl;

        @NotNull
        @Valid
        private AppMailProperties mail = new AppMailProperties();
    }

    @Data
    public static class AppMailProperties {

        @NotNull
        @NotBlank
        private String host;

        @NotNull
        private Integer port;

        @NotNull
        @Valid
        private MailConnectionProperties properties = new MailConnectionProperties();
    }

    @Data
    public static class MailConnectionProperties {

        @NotNull
        @Valid
        private MailProtocolProperties mail = new MailProtocolProperties();
    }

    @Data
    public static class MailProtocolProperties {

        @NotNull
        @Valid
        private SmtpProperties smtp = new SmtpProperties();
    }

    @Data
    public static class SmtpProperties {

        @NotNull
        private Boolean auth;
    }

    @Data
    public static class LoggingProperties {

        @NotNull
        @Valid
        private LoggingLevelProperties level = new LoggingLevelProperties();
    }

    @Data
    public static class LoggingLevelProperties {

        @NotNull
        @NotBlank
        private String root;

        @NotNull
        @Valid
        private OrganizationProperties org = new OrganizationProperties();
    }

    @Data
    public static class OrganizationProperties {

        @NotNull
        @Valid
        private SpringFrameworkProperties springframework = new SpringFrameworkProperties();
    }

    @Data
    public static class SpringFrameworkProperties {

        @NotNull
        @NotBlank
        private String security;
    }

    @Data
    public static class SpringdocProperties {

        @NotNull
        @Valid
        private SwaggerUiProperties swaggerUi = new SwaggerUiProperties();
    }

    @Data
    public static class SwaggerUiProperties {

        @NotNull
        @NotBlank
        private String path;
    }

    @Data
    public static class AuthProperties {

        @NotNull
        private Duration otpExpiry;

        @NotNull
        private Duration resetTokenExpiry;

        @NotNull
        @Valid
        private MailConfig mailConfig = new MailConfig();

        @NotNull
        @Valid
        private JwtConfig jwt = new JwtConfig();

        @NotNull
        @Valid
        private FusionAuthProperties fusionauth = new FusionAuthProperties();

        @NotNull
        @Valid
        private WebhookConfig webhook = new WebhookConfig();
    }

    @Data
    public static class MailConfig {

        @NotNull
        @NotBlank
        private String provider;

        @NotNull
        @NotBlank
        private String from;

        @NotNull
        @NotBlank
        private String name;
    }

    @Data
    public static class JwtConfig {

        @NotNull
        @NotBlank
        private String secret;

        @NotNull
        @NotBlank
        private String issuer;

        @NotNull
        private Duration accessTokenTtl;

        @NotNull
        private Duration refreshTokenTtl;
    }

    @Data
    public static class FusionAuthProperties {

        @NotNull
        @NotBlank
        private String baseUrl;

        @NotNull
        @NotBlank
        private String clientId;

        @NotNull
        @NotBlank
        private String clientSecret;

        @NotNull
        @NotBlank
        private String redirectUri;

        @NotNull
        @NotBlank
        private String apiKey;

        @NotNull
        @NotEmpty
        private Map<@NotNull @NotBlank String, @NotNull @NotBlank String> idpIds;
    }

    @Data
    public static class WebhookConfig {

        @NotNull
        @NotBlank
        private String secret;

        @NotNull
        @NotBlank
        private String signatureKey;

        @NotNull
        private Long maxEventAgeSeconds;

        @NotNull
        @Valid
        private RateLimitConfig rateLimit = new RateLimitConfig();
    }

    @Data
    public static class RateLimitConfig {

        @NotNull
        private Integer maxAttempts;

        @NotNull
        private Integer windowSeconds;
    }
}
