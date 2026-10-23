package com.example.springbootbasiclogin.controller.user;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.dto.BaseResponse;
import com.example.springbootbasiclogin.dto.user.ChangePasswordRequest;
import com.example.springbootbasiclogin.dto.user.UserProfileUpdateRequest;
import com.example.springbootbasiclogin.dto.user.UserResponse;
import com.example.springbootbasiclogin.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/all")
    @Authenticated(roles = {"ADMIN"})
    public Flux<UserResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return userService.findAll(page, size).map(UserResponse::from);
    }

    @GetMapping
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<BaseResponse<UserResponse>> getUser(@RequestParam int userId, Principal principal) {
        return userService.findByIdAuthorized(userId, principal)
                .map(user -> BaseResponse.success(UserResponse.from(user)));
    }

    @PutMapping("/update")
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<BaseResponse<UserResponse>> updateUser(
            @RequestParam int userId,
            @RequestBody @Valid UserProfileUpdateRequest updateRequest,
            Principal principal
    ) {
        return userService.updateProfile(userId, updateRequest, principal)
                .map(user -> BaseResponse.success(UserResponse.from(user)));
    }

    @PutMapping("/change-password")
    @Authenticated(roles = {"USER", "ADMIN"})
    public Mono<BaseResponse<String>> changePassword(
            @RequestParam int userId,
            @RequestBody @Valid ChangePasswordRequest passwordRequest,
            Principal principal
    ) {
        return userService.changePassword(userId, passwordRequest, principal)
                .map(BaseResponse::success);
    }

    @DeleteMapping
    @Authenticated(roles = {"ADMIN"})
    public Mono<BaseResponse<String>> deleteUser(@RequestParam int userId) {
        return userService.deleteById(userId)
                .map(BaseResponse::success);
    }
}
