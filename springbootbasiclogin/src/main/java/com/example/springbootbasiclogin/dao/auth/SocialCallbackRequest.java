package com.example.springbootbasiclogin.dao.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialCallbackRequest {

    @NotBlank(message = "Authorization code cannot be blank")
    private String code;
}
