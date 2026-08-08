package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.repo.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;

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
                    // Use reflection to get all fields
                    Field[] fields = Users.class.getDeclaredFields();

                    for (Field field : fields) {
                        field.setAccessible(true);
                        try {
                            // Get the value of the field from theUser
                            Object value = field.get(theUser);
                            // Update the field in existingUser only if the value is not null
                            if (value != null) {
                                field.set(existingUser, value);
                            }
                        } catch (IllegalAccessException e) {
                            String className = field.getDeclaringClass().getName();
                            throw new CustomException(AuthResponseCode.AUTH_000111_FAILED_ACCESS_MEMBER_CLASS, e, className);
                        }
                    }

                    // Set the ID before saving
                    existingUser.setId(id);
                    //update the data to db
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


