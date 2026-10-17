package com.example.springbootbasiclogin.service.user;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.user.ChangePasswordRequest;
import com.example.springbootbasiclogin.dao.user.UserProfileUpdateRequest;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /* Managing User Profile */
    @Override
    public Flux<Users> findAll() {
        return findAll(0, 50);
    }

    @Override
    public Flux<Users> findAll(int page, int size) {
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * limit;
        return userRepository.findAll().skip(offset).take(limit);
    }

    @Override
    public Mono<Users> findById(int theId) {
        return userRepository.findById(theId)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000404_NOT_FOUND)));
    }

    @Override
    public Mono<Users> findByIdAuthorized(int theId, Principal principal) {
        return findById(theId)
                .flatMap(user -> verifyOwnershipOrAdmin(user, principal).thenReturn(user));
    }

    @Override
    public Mono<Users> updateProfile(int id, UserProfileUpdateRequest request, Principal principal) {
        return findById(id)
                .flatMap(existingUser -> verifyOwnershipOrAdmin(existingUser, principal).thenReturn(existingUser))
                .flatMap(existingUser -> {
                    if (request.getFirstName() != null) existingUser.setFirstName(request.getFirstName());
                    if (request.getLastName() != null) existingUser.setLastName(request.getLastName());
                    if (request.getAbout() != null) existingUser.setAbout(request.getAbout());
                    if (request.getJobTitle() != null) existingUser.setJobTitle(request.getJobTitle());
                    if (request.getLanguages() != null) existingUser.setLanguages(request.getLanguages());
                    if (request.getSkills() != null) existingUser.setSkills(request.getSkills());
                    if (request.getProjectsAndExperiences() != null) existingUser.setProjectsAndExperiences(request.getProjectsAndExperiences());
                    if (request.getAssignments() != null) existingUser.setAssignments(request.getAssignments());
                    if (request.getProfilePic() != null) existingUser.setProfilePic(request.getProfilePic());
                    existingUser.setId(id);
                    return userRepository.save(existingUser);
                });
    }

    @Override
    public Mono<String> changePassword(int id, ChangePasswordRequest request, Principal principal) {
        return findById(id)
                .flatMap(user -> verifyOwnershipOrAdmin(user, principal).thenReturn(user))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION));
                    }
                    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                    return userRepository.save(user).thenReturn("Password updated successfully.");
                });
    }

    private Mono<Void> verifyOwnershipOrAdmin(Users targetUser, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED));
        }
        if (targetUser.getUsername() != null && targetUser.getUsername().equals(principal.getName())) {
            return Mono.empty();
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    boolean isAdmin = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
                    if (isAdmin) {
                        return Mono.empty();
                    }
                    return Mono.error(new CustomException(AuthResponseCode.AUTH_000403_ACCESS_DENIED));
                })
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000403_ACCESS_DENIED)))
                .then();
    }

    @Override
    public Mono<Users> update(int id, Users theUser) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000404_NOT_FOUND)))
                .flatMap(existingUser -> {
                    updateCredentials(existingUser, theUser);
                    updateBasicInfo(existingUser, theUser);
                    updateWorkAndSkills(existingUser, theUser);

                    // Preserve existing ID
                    existingUser.setId(id);
                    return userRepository.save(existingUser);
                });
    }

    private void updateCredentials(Users existingUser, Users theUser) {
        if (theUser.getUsername() != null && !theUser.getUsername().isBlank()) {
            existingUser.setUsername(theUser.getUsername());
        }
        if (theUser.getPassword() != null && !theUser.getPassword().isBlank()) {
            existingUser.setPassword(passwordEncoder.encode(theUser.getPassword()));
        }
        if (theUser.getEmail() != null && !theUser.getEmail().isBlank()) {
            existingUser.setEmail(theUser.getEmail());
        }
    }

    private void updateBasicInfo(Users existingUser, Users theUser) {
        if (theUser.getFirstName() != null) {
            existingUser.setFirstName(theUser.getFirstName());
        }
        if (theUser.getLastName() != null) {
            existingUser.setLastName(theUser.getLastName());
        }
        if (theUser.getAbout() != null) {
            existingUser.setAbout(theUser.getAbout());
        }
        if (theUser.getProfilePic() != null) {
            existingUser.setProfilePic(theUser.getProfilePic());
        }
    }

    private void updateWorkAndSkills(Users existingUser, Users theUser) {
        if (theUser.getJobTitle() != null) {
            existingUser.setJobTitle(theUser.getJobTitle());
        }
        if (theUser.getLanguages() != null) {
            existingUser.setLanguages(theUser.getLanguages());
        }
        if (theUser.getSkills() != null) {
            existingUser.setSkills(theUser.getSkills());
        }
        if (theUser.getProjectsAndExperiences() != null) {
            existingUser.setProjectsAndExperiences(theUser.getProjectsAndExperiences());
        }
        if (theUser.getAssignments() != null) {
            existingUser.setAssignments(theUser.getAssignments());
        }
    }

    @Override
    public Mono<String> deleteById(int userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000404_NOT_FOUND)))
                .flatMap(user -> roleRepository.deleteByUserId(userId)
                        .then(userRepository.deleteById(userId))
                        .thenReturn("Deleted user id - " + userId));
    }
}
