package com.example.springbootbasiclogin.dao.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    @Size(max = 255)
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String role;

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "Email that wants to be registered", example = "user@example.com")
    private String email;


}
