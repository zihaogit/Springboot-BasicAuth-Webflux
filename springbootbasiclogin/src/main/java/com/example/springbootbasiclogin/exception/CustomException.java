package com.example.springbootbasiclogin.exception;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final AuthResponseCode authResponseCode;
    private Object[] messageArgs;

    public CustomException(AuthResponseCode authResponseCode) {
        this.authResponseCode = authResponseCode;
    }

    public CustomException(AuthResponseCode authResponseCode, Throwable cause) {
        super(cause);
        this.authResponseCode = authResponseCode;
    }

    public CustomException(AuthResponseCode authResponseCode, Throwable cause, Object... messageArgs) {
        super(cause);
        this.authResponseCode = authResponseCode;
        this.messageArgs = messageArgs;
    }
}
