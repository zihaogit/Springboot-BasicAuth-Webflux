package com.example.springbootbasiclogin.controller;

import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private Users user1;
    private Users user2;

    @BeforeEach
    void setUp() {
        user1 = new Users();
        user1.setId(1);
        user1.setUsername("alice");
        user1.setEmail("alice@example.com");

        user2 = new Users();
        user2.setId(2);
        user2.setUsername("bob");
        user2.setEmail("bob@example.com");
    }

    @Test
    @DisplayName("Find all users success")
    void testFindAllUsers() {
        when(userService.findAll()).thenReturn(Flux.just(user1, user2));

        Flux<Users> result = userController.findAll(null);

        StepVerifier.create(result)
                .expectNext(user1)
                .expectNext(user2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Find user by ID success")
    void testGetUserById() {
        when(userService.findById(1)).thenReturn(Mono.just(user1));

        Mono<Users> result = userController.getUser(null, 1);

        StepVerifier.create(result)
                .expectNext(user1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Update user success")
    void testUpdateUser() {
        Users updateInfo = new Users();
        updateInfo.setFirstName("Alice");
        updateInfo.setLastName("Smith");

        when(userService.update(eq(1), eq(updateInfo))).thenReturn(Mono.just(user1));

        Mono<Users> result = userController.updateUser(null, 1, updateInfo);

        StepVerifier.create(result)
                .expectNext(user1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Delete user by ID success")
    void testDeleteUser() {
        when(userService.deleteById(1)).thenReturn(Mono.just("User deleted successfully"));

        Mono<String> result = userController.deleteUser(null, 1, null);

        StepVerifier.create(result)
                .expectNext("User deleted successfully")
                .verifyComplete();
    }
}
