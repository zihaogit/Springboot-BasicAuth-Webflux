package com.example.springbootbasiclogin.helper;

import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.repo.RoleRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Shared helper for synchronizing user roles from external identity providers.
 * Used by both SocialAuthService and FusionAuthWebhookController.
 */
@Component
public class RoleSyncHelper {

    private final RoleRepository roleRepository;

    public RoleSyncHelper(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /**
     * Ensures the user has at least the given target roles.
     * Only adds missing roles — does not remove existing ones.
     */
    public Mono<Void> syncRoles(int userId, List<String> targetRoles) {
        List<String> effectiveRoles = (targetRoles != null && !targetRoles.isEmpty())
                ? targetRoles : List.of("USER");

        return roleRepository.findByUserId(userId)
                .map(Roles::getRole)
                .collectList()
                .flatMap(existingRoles -> {
                    List<Roles> toAdd = effectiveRoles.stream()
                            .filter(r -> !existingRoles.contains(r))
                            .map(r -> {
                                Roles role = new Roles();
                                role.setUserId(userId);
                                role.setRole(r);
                                return role;
                            })
                            .toList();
                    if (toAdd.isEmpty()) {
                        return Mono.empty();
                    }
                    return roleRepository.saveAll(toAdd).then();
                });
    }
}
