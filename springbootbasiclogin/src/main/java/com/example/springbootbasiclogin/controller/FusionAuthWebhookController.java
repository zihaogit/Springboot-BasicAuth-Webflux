package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.helper.RoleSyncHelper;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.service.webhook.WebhookSecurityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhooks")
@Slf4j
public class FusionAuthWebhookController {

    private final WebhookSecurityService webhookSecurityService;
    private final UserRepository userRepository;
    private final RoleSyncHelper roleSyncHelper;
    private final ObjectMapper objectMapper;

    public FusionAuthWebhookController(WebhookSecurityService webhookSecurityService,
            UserRepository userRepository,
            RoleSyncHelper roleSyncHelper,
            ObjectMapper objectMapper) {
        this.webhookSecurityService = webhookSecurityService;
        this.userRepository = userRepository;
        this.roleSyncHelper = roleSyncHelper;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles incoming FusionAuth webhook events.
     *
     * @param rawPayload       the raw JSON body as a string
     * @param webhookSecret    the X-Webhook-Secret header value (optional)
     * @param fusionAuthSig    the X-FusionAuth-Signature header value (raw
     *                         HMAC-SHA256, optional)
     * @param fusionAuthJwtSig the X-FusionAuth-Signature-JWT header value (signed
     *                         JWT, optional)
     * @return 200 OK on success, 401 on security failure, 400 on bad request
     */
    @PostMapping("/fusionauth")
    public Mono<ResponseEntity<Map<String, String>>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(name = "X-Webhook-Secret", required = false) String webhookSecret,
            @RequestHeader(name = "X-FusionAuth-Signature", required = false) String fusionAuthSig,
            @RequestHeader(name = "X-FusionAuth-Signature-JWT", required = false) String fusionAuthJwtSig) {

        // ── 1. Rate Limiting ──────────────────────────────────────────────────
        if (!webhookSecurityService.checkRateLimit()) {
            log.warn("Webhook rate limit exceeded");
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded")));
        }

        // ── 2. Shared Secret Verification ─────────────────────────────────────
        if (!webhookSecurityService.verifySecret(webhookSecret)) {
            log.warn("Webhook secret verification failed");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook secret")));
        }

        // ── 3. Cryptographic Signature Verification (JWT or raw HMAC) ──────────
        if (!webhookSecurityService.verifySignature(rawPayload, fusionAuthSig, fusionAuthJwtSig)) {
            log.warn("Webhook signature verification failed");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature")));
        }

        // ── Parse JSON payload ────────────────────────────────────────────────
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload", e);
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid JSON payload")));
        }

        JsonNode eventNode = root.path("event");
        if (eventNode.isMissingNode()) {
            log.warn("Webhook payload missing 'event' field");
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'event' field")));
        }

        String eventId = eventNode.path("id").asText(null);
        String eventType = eventNode.path("type").asText(null);
        long createInstant = eventNode.path("createInstant").asLong(0);
        JsonNode userNode = eventNode.path("user");

        if (eventId == null || eventType == null) {
            log.warn("Webhook event missing 'id' or 'type' field");
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing event 'id' or 'type'")));
        }

        // ── 4. Timestamp Replay Protection ────────────────────────────────────
        if (createInstant > 0 && !webhookSecurityService.verifyTimestamp(createInstant)) {
            log.warn("Webhook event {} rejected: timestamp too old or in the future", eventId);
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Event timestamp outside acceptable window")));
        }

        // ── 5. Idempotency Check ──────────────────────────────────────────────
        return webhookSecurityService.isEventAlreadyProcessed(eventId)
                .flatMap(alreadyProcessed -> {
                    if (alreadyProcessed) {
                        log.info("Webhook event {} already processed (idempotent), returning 200", eventId);
                        return Mono.just(ResponseEntity.ok(
                                Map.of("status", "ok", "message", "Event already processed")));
                    }

                    // ── Process the event ─────────────────────────────────────
                    return processEvent(eventType, userNode, eventId)
                            .then(webhookSecurityService.recordProcessedEvent(eventId, eventType))
                            .then(Mono.fromCallable(() -> {
                                log.info("Webhook event {} ({}) processed successfully", eventId, eventType);
                                return ResponseEntity.ok(Map.of("status", "ok"));
                            }))
                            .onErrorResume(e -> {
                                log.error("Error processing webhook event {} ({})", eventId, eventType, e);
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Internal processing error")));
                            });
                });
    }

    /**
     * Routes the webhook event to the appropriate handler based on event type.
     */
    private Mono<Void> processEvent(String eventType, JsonNode userNode, String eventId) {
        if (userNode == null || userNode.isMissingNode()) {
            log.warn("Event {} missing 'user' node, skipping", eventId);
            return Mono.empty();
        }

        String userId = userNode.path("id").asText(null);
        String email = userNode.path("email").asText(null);

        if ((userId == null || userId.isBlank()) && (email == null || email.isBlank())) {
            log.warn("Event {} user has neither id nor email, skipping", eventId);
            return Mono.empty();
        }

        return switch (eventType) {
            case "user.delete", "user.deactivate" -> handleUserDeleteOrDeactivate(userId, email, eventType);
            case "user.reactivate" -> handleUserReactivate(userId, email);
            case "user.update" -> handleUserUpdate(userNode, userId, email);
            default -> {
                log.info("Ignoring unhandled webhook event type: {}", eventType);
                yield Mono.empty();
            }
        };
    }

    /**
     * Finds a local user by FusionAuth UUID first, falling back to email.
     * Links the UUID if found by email.
     */
    private Mono<Users> findLocalUser(String fusionAuthUserId, String email) {
        Mono<Users> byId = (fusionAuthUserId != null && !fusionAuthUserId.isBlank())
                ? userRepository.findByFusionAuthUserId(fusionAuthUserId)
                : Mono.empty();

        return byId.switchIfEmpty(Mono.defer(() ->
                (email != null && !email.isBlank())
                        ? userRepository.findByEmail(email).flatMap(user -> {
                            if (fusionAuthUserId != null && !fusionAuthUserId.isBlank()) {
                                user.setFusionAuthUserId(fusionAuthUserId);
                                return userRepository.save(user);
                            }
                            return Mono.just(user);
                        })
                        : Mono.empty()));
    }

    /**
     * Handles user.delete and user.deactivate events.
     * Soft-deletes the user by setting active=false and verified=false.
     * Does NOT hard-delete to preserve referential integrity.
     */
    private Mono<Void> handleUserDeleteOrDeactivate(String userId, String email, String eventType) {
        log.info("Processing {} for user id: {}, email: {}", eventType, userId, email);
        return findLocalUser(userId, email)
                .flatMap(user -> {
                    user.setActive(false);
                    user.setVerified(false);
                    return userRepository.save(user);
                })
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("User {} soft-deleted via {} webhook", user.getUsername(), eventType);
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("User (id: {}, email: {}) not found in local DB for {} event, no action taken",
                            userId, email, eventType);
                    return Mono.empty();
                }))
                .then();
    }

    /**
     * Handles user.reactivate events.
     * Reactivates the user by setting active=true.
     */
    private Mono<Void> handleUserReactivate(String userId, String email) {
        log.info("Processing user.reactivate for user id: {}, email: {}", userId, email);
        return findLocalUser(userId, email)
                .flatMap(user -> {
                    user.setActive(true);
                    return userRepository.save(user);
                })
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("User {} reactivated via webhook", user.getUsername());
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("User (id: {}, email: {}) not found in local DB for reactivate event, no action taken",
                            userId, email);
                    return Mono.empty();
                }))
                .then();
    }

    /**
     * Handles user.update events.
     * Updates user attributes (email, firstName, lastName, username, roles) from
     * the FusionAuth payload.
     */
    private Mono<Void> handleUserUpdate(JsonNode userNode, String userId, String email) {
        log.info("Processing user.update for user id: {}, email: {}", userId, email);
        return findLocalUser(userId, email)
                .flatMap(user -> {
                    boolean changed = false;

                    String newFirstName = userNode.path("firstName").asText(null);
                    if (newFirstName != null && !newFirstName.equals(user.getFirstName())) {
                        user.setFirstName(newFirstName);
                        changed = true;
                    }

                    String newLastName = userNode.path("lastName").asText(null);
                    if (newLastName != null && !newLastName.equals(user.getLastName())) {
                        user.setLastName(newLastName);
                        changed = true;
                    }

                    String newEmail = userNode.path("email").asText(null);
                    if (newEmail != null && !newEmail.equals(user.getEmail())) {
                        user.setEmail(newEmail);
                        changed = true;
                    }

                    String newUsername = userNode.path("username").asText(null);
                    if (newUsername != null && !newUsername.equals(user.getUsername())) {
                        user.setUsername(newUsername);
                        changed = true;
                    }

                    // Extract roles from registrations if present
                    List<String> targetRoles = extractRolesFromRegistrations(userNode);

                    Mono<Users> saveMono = changed ? userRepository.save(user) : Mono.just(user);

                    return saveMono.flatMap(savedUser -> {
                        if (!targetRoles.isEmpty()) {
                            return roleSyncHelper.syncRoles(savedUser.getId(), targetRoles).thenReturn(savedUser);
                        }
                        return Mono.just(savedUser);
                    });
                })
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("User {} updated via webhook", user.getUsername());
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("User (id: {}, email: {}) not found in local DB for update event, no action taken",
                            userId, email);
                    return Mono.empty();
                }))
                .then();
    }

    private List<String> extractRolesFromRegistrations(JsonNode userNode) {
        List<String> roles = new ArrayList<>();
        JsonNode registrationsNode = userNode.path("registrations");
        if (registrationsNode.isArray()) {
            for (JsonNode reg : registrationsNode) {
                JsonNode rolesNode = reg.path("roles");
                if (rolesNode.isArray()) {
                    for (JsonNode r : rolesNode) {
                        String role = r.asText();
                        if (role != null && !role.isBlank() && !roles.contains(role)) {
                            roles.add(role);
                        }
                    }
                }
            }
        }
        return roles;
    }
}
