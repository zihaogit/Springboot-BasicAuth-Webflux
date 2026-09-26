package com.example.springbootbasiclogin.service.user;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        return userRepository.findAll();
    }

    @Override
    public Mono<Users> findById(int theId) {
        return userRepository.findById(theId)
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000404_NOT_FOUND)));
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
            // Ensure password is encrypted if not already hashed
            String newPassword = theUser.getPassword();
            if (!newPassword.startsWith("{") && !newPassword.startsWith("$2a$") && !newPassword.startsWith("$2b$")) {
                newPassword = passwordEncoder.encode(newPassword);
            }
            existingUser.setPassword(newPassword);
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
