package com.example.springbootbasiclogin.exception;

import lombok.Getter;
import lombok.Setter;
import org.flywaydb.core.api.ErrorDetails;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class CustomException extends RuntimeException{

    private final HttpStatus status;
    private final ErrorDetails details;

    public CustomException(String message, HttpStatus status, ErrorDetails details) {
        super(message);
        this.status = status;
        this.details = details;
    }
}
