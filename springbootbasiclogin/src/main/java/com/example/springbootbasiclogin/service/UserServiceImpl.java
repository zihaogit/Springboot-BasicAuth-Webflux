package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.repo.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository, VerificationTokenRepository verificationTokenRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.verificationTokenRepository = verificationTokenRepository;
    }

    /* Managing User Profile */
    @Override
    public Flux<Users> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Mono<Users> findById(int theId) {
        return userRepository.findById(theId);
    }

    @Override
    public Mono<Users> update(int id, Users theUser) {
        return userRepository.findById(id)
                .flatMap(existingUser -> {
                    if (theUser.getUsername() != null) {
                        existingUser.setUsername(theUser.getUsername());
                    }
                    if (theUser.getPassword() != null) {
                        existingUser.setPassword(theUser.getPassword());
                    }
                    if (theUser.getFirstName() != null) {
                        existingUser.setFirstName(theUser.getFirstName());
                    }
                    if (theUser.getLastName() != null) {
                        existingUser.setLastName(theUser.getLastName());
                    }
                    if (theUser.getEmail() != null) {
                        existingUser.setEmail(theUser.getEmail());
                    }
                    if (theUser.getAbout() != null) {
                        existingUser.setAbout(theUser.getAbout());
                    }
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
                    if (theUser.getProfilePic() != null) {
                        existingUser.setProfilePic(theUser.getProfilePic());
                    }

                    // Set the ID before saving
                    existingUser.setId(id);
                    // update the data to db
                    return userRepository.save(existingUser);
                });
    }

    @Override
    //Delete the Roles + VerificationToken + User using id
    public Mono<String> deleteById(int userId) {
        return roleRepository.deleteByUserId(userId)
                .then(verificationTokenRepository.deleteByUserId(userId))
                .then(userRepository.deleteById(userId)
                        .thenReturn("Deleted user id - " + userId)
                        .defaultIfEmpty("Error delete user id: " + userId + " caused by Cascade")
                );
    }

}


