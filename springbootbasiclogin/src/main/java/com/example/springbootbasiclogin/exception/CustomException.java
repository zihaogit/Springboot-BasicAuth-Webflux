package com.example.springbootbasiclogin.exception;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final AuthResponseCode authResponseCode;

    public CustomException(AuthResponseCode authResponseCode) {
        this(authResponseCode, null);
    }

    public CustomException(AuthResponseCode authResponseCode, Throwable cause) {
        super(cause);
        this.authResponseCode = authResponseCode;
    }
}
