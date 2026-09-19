package com.example.springbootbasiclogin.service.auth;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.auth.TokenResponse;
import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.helper.RoleSyncHelper;
import com.example.springbootbasiclogin.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SocialAuthService {

    private final WebClient fusionAuthWebClient;
    private final ApplicationPropertiesConfig applicationProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final RoleSyncHelper roleSyncHelper;

    public SocialAuthService(
            @Qualifier("fusionAuthWebClient") WebClient fusionAuthWebClient,
            ApplicationPropertiesConfig applicationProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            RoleSyncHelper roleSyncHelper) {
        this.fusionAuthWebClient = fusionAuthWebClient;
        this.applicationProperties = applicationProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.roleSyncHelper = roleSyncHelper;
    }

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Map<String, String> idpCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initIdpCache() {
        refreshIdpCache();
    }

    public void refreshIdpCache() {
        ApplicationPropertiesConfig.FusionAuthProperties config = applicationProperties.getAuth().getFusionauth();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            return;
        }
        try {
            fusionAuthWebClient.get()
                    .uri("/api/identity-provider")
                    .header(HttpHeaders.AUTHORIZATION, config.getApiKey())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(3))
                    .doOnNext(response -> {
                        Object listObj = response.get("identityProviders");
                        if (listObj instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof Map<?, ?> map) {
                                    String id = String.valueOf(map.get("id"));
                                    String type = map.get("type") != null ? String.valueOf(map.get("type")).toLowerCase() : null;
                                    String name = map.get("name") != null ? String.valueOf(map.get("name")).toLowerCase() : null;
                                    if (id != null && !id.isBlank()) {
                                        if (type != null) {
                                            idpCache.put(type, id);
                                        }
                                        if (name != null) {
                                            idpCache.put(name, id);
                                        }
                                    }
                                }
                            }
                            log.info("Loaded {} FusionAuth Identity Provider mappings: {}", idpCache.size(), idpCache.keySet());
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("Could not pre-fetch FusionAuth identity providers: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (Exception e) {
            log.warn("Failed to initiate Identity Provider lookup: {}", e.getMessage());
        }
    }

    public String resolveIdpHint(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        String p = provider.trim().toLowerCase();
        // If it's already a UUID, return as-is
        if (UUID_PATTERN.matcher(p).matches()) {
            return p;
        }
        // Check config map override
        Map<String, String> idpIds = applicationProperties.getAuth().getFusionauth().getIdpIds();
        if (idpIds != null) {
            String configValue = idpIds.get(p);
            if (configValue != null && !configValue.isBlank()) {
                return configValue;
            }
        }
        // Check dynamic cache, fallback to raw provider string
        return idpCache.getOrDefault(p, p);
    }

    /**
     * Build the FusionAuth OAuth2 authorize URL.
     * If a provider is specified (e.g. google, facebook, twitter), idp_hint is
     * attached with its resolved IdP UUID.
     */
    public String getAuthorizeUrl(String provider) {
        ApplicationPropertiesConfig.FusionAuthProperties config = applicationProperties.getAuth().getFusionauth();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                .path("/oauth2/authorize")
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile");

        if (provider != null && !provider.isBlank()) {
            String idpHint = resolveIdpHint(provider);
            builder.queryParam("idp_hint", idpHint);
        }

        return builder.encode().build().toUriString();
    }

    /**
     * Exchanges authorization code with FusionAuth, retrieves user profile,
     * provisions/updates the user in PostgreSQL, and issues application JWT tokens.
     */
    public Mono<TokenResponse> handleCallback(String code) {
        if (code == null || code.isBlank()) {
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER));
        }

        ApplicationPropertiesConfig.FusionAuthProperties config = applicationProperties.getAuth().getFusionauth();

        return fusionAuthWebClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("code", code)
                        .with("client_id", config.getClientId())
                        .with("client_secret", config.getClientSecret())
                        .with("redirect_uri", config.getRedirectUri()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .onErrorMap(e -> {
                    log.error("Failed to exchange authorization code with FusionAuth: {}", e.getMessage());
                    return new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION, e);
                })
                .flatMap(tokenResponse -> {
                    String accessToken = (String) tokenResponse.get("access_token");
                    if (accessToken == null || accessToken.isBlank()) {
                        log.error("FusionAuth token response missing access_token");
                        return Mono
                                .error(new CustomException(AuthResponseCode.AUTH_000108_INVALID_JWT_OR_ACCESS_TOKEN));
                    }
                    return fetchUserInfo(accessToken);
                })
                .flatMap(this::provisionAndGenerateTokens);
    }

    private Mono<Map<String, Object>> fetchUserInfo(String accessToken) {
        return fusionAuthWebClient.get()
                .uri("/oauth2/userinfo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .onErrorMap(e -> {
                    log.error("Failed to fetch userinfo from FusionAuth: {}", e.getMessage());
                    return new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION, e);
                });
    }

    /**
     * Provisions new user or updates existing user, then generates native JWT
     * tokens.
     */
    private Mono<TokenResponse> provisionAndGenerateTokens(Map<String, Object> userInfo) {
        String email = (String) userInfo.get("email");
        String sub = (String) userInfo.get("sub");
        String name = (String) userInfo.get("name");
        String givenName = (String) userInfo.get("given_name");
        String familyName = (String) userInfo.get("family_name");
        String preferredUsername = (String) userInfo.get("preferred_username");
        List<String> fusionAuthRoles = extractRoles(userInfo);

        // Fallback for providers that may not return email
        if (email == null || email.isBlank()) {
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                email = preferredUsername + "@social.local";
            } else if (sub != null) {
                email = "user_" + sub.substring(0, Math.min(sub.length(), 8)) + "@social.local";
            } else {
                return Mono.error(new CustomException(AuthResponseCode.AUTH_000150_EMAIL_IS_REQUIRED));
            }
        }

        final String finalEmail = email;
        final String finalSub = sub;
        final String finalFirstName = givenName != null ? givenName : (name != null ? name.split(" ")[0] : "");
        final String finalLastName = familyName != null ? familyName
                : (name != null && name.contains(" ") ? name.substring(name.indexOf(" ") + 1) : "");

        String defaultUsername = preferredUsername;
        if (defaultUsername == null || defaultUsername.isBlank()) {
            defaultUsername = finalEmail.contains("@") ? finalEmail.substring(0, finalEmail.indexOf("@"))
                    : "user_" + UUID.randomUUID().toString().substring(0, 8);
        }
        final String finalDefaultUsername = defaultUsername;

        // 1. Search by FusionAuth immutable UUID first
        Mono<Users> userMono = (finalSub != null && !finalSub.isBlank())
                ? userRepository.findByFusionAuthUserId(finalSub)
                : Mono.empty();

        return userMono
                // 2. If not found by UUID, look up by email and link fusionauth_user_id
                .switchIfEmpty(Mono.defer(() -> userRepository.findByEmail(finalEmail)
                        .flatMap(userByEmail -> {
                            if (finalSub != null && !finalSub.isBlank()) {
                                userByEmail.setFusionAuthUserId(finalSub);
                            }
                            return userRepository.save(userByEmail);
                        })))
                .flatMap(existingUser -> {
                    log.info("Returning social login for existing user: {}", existingUser.getUsername());
                    existingUser.setActive(true);
                    return userRepository.save(existingUser)
                            .flatMap(saved -> roleSyncHelper.syncRoles(saved.getId(), fusionAuthRoles)
                                    .then(authService.generateTokensForUser(saved.getUsername())));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 3. First-time social login: provision user
                    log.info("First time social login. Provisioning new user with email: {}", finalEmail);
                    return resolveUniqueUsername(finalDefaultUsername)
                            .flatMap(uniqueUsername -> {
                                Users newUser = new Users();
                                newUser.setUsername(uniqueUsername);
                                newUser.setEmail(finalEmail);
                                newUser.setFusionAuthUserId(finalSub);
                                newUser.setFirstName(finalFirstName);
                                newUser.setLastName(finalLastName);
                                newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                                newUser.setVerified(true);
                                newUser.setActive(true);

                                return userRepository.save(newUser)
                                        .flatMap(savedUser -> roleSyncHelper.syncRoles(savedUser.getId(), fusionAuthRoles)
                                                .then(authService.generateTokensForUser(savedUser.getUsername())));
                            });
                }));
    }

    private List<String> extractRoles(Map<String, Object> userInfo) {
        Set<String> roles = new LinkedHashSet<>();
        Object rolesObj = userInfo.get("roles");
        if (rolesObj instanceof Collection<?> list) {
            for (Object r : list) {
                if (r != null) {
                    roles.add(r.toString());
                }
            }
        }
        Object regObj = userInfo.get("registrations");
        if (regObj instanceof Collection<?> regList) {
            for (Object reg : regList) {
                if (reg instanceof Map<?, ?> regMap) {
                    Object regRoles = regMap.get("roles");
                    if (regRoles instanceof Collection<?> rList) {
                        for (Object r : rList) {
                            if (r != null) {
                                roles.add(r.toString());
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(roles);
    }

    private Mono<String> resolveUniqueUsername(String baseUsername) {
        return userRepository.findByUsername(baseUsername)
                .flatMap(existing -> {
                    String candidate = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 4);
                    return resolveUniqueUsername(candidate);
                })
                .switchIfEmpty(Mono.just(baseUsername));
    }
}
