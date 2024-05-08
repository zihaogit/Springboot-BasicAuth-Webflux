package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.NoSuchElementException;

@Service
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /* Auth and Autz */
    @Override
    public Mono<Boolean> isUsernameMatching(int userId, String username) {
        return userRepository.findUsernameById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("No user found with the provided ID: " + userId)))
                .map(username::equals)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Users> findByUsername(String username) {
        return userRepository.findByUsername(username);
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
    public Mono<Users> save(Users theUser) {
        return userRepository.save(theUser);
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
                            return Mono.error(e); // Propagate the error as Mono.error
                        }
                    }

                    // Set the ID before saving
                    existingUser.setId(id);
                    //update the data to db
                    return userRepository.save(existingUser);
                });
    }


    @Override
    public Mono<Void> deleteById(int theId) {
        return userRepository.deleteById(theId);
    }

}


