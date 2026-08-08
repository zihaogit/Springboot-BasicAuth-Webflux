package com.example.springbootbasiclogin.model;

import lombok.Data;

@Data
public class AuthEmail {

    private final String email;
    private final String subject;
    private final String content;
}
