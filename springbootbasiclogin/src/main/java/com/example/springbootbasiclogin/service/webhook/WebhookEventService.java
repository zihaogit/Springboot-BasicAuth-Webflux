package com.example.springbootbasiclogin.service.webhook;

import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.helper.RoleSyncHelper;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookEventService {

    private final UserRepository userRepository;
    private final RoleSyncHelper roleSyncHelper;

    /**
     * Routes the webhook event to the appropriate handler based on event type.
     */
    public Mono<Void> processEvent(String eventType, JsonNode userNode, String eventId) {
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
     * Updates user attributes (email, firstName, lastName, username, roles) from the FusionAuth payload.
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
