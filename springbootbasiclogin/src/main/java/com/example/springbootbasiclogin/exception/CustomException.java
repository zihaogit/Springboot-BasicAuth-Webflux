package com.example.springbootbasiclogin.exception;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import lombok.Getter;

import java.io.Serializable;

@Getter
public class CustomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AuthResponseCode authResponseCode;
    private final Serializable[] messageArgs;

    public CustomException(AuthResponseCode authResponseCode) {
        this.authResponseCode = authResponseCode;
        this.messageArgs = new Serializable[0];
    }

    public CustomException(AuthResponseCode authResponseCode, String message) {
        super(message);
        this.authResponseCode = authResponseCode;
        this.messageArgs = message != null ? new Serializable[]{message} : new Serializable[0];
    }

    public CustomException(AuthResponseCode authResponseCode, Object... messageArgs) {
        this.authResponseCode = authResponseCode;
        this.messageArgs = toSerializableArray(messageArgs);
    }

    public CustomException(AuthResponseCode authResponseCode, Throwable cause) {
        super(cause);
        this.authResponseCode = authResponseCode;
        this.messageArgs = new Serializable[0];
    }

    public CustomException(AuthResponseCode authResponseCode, Throwable cause, Object... messageArgs) {
        super(cause);
        this.authResponseCode = authResponseCode;
        this.messageArgs = toSerializableArray(messageArgs);
    }

    public Object[] getMessageArgs() {
        return messageArgs != null ? messageArgs.clone() : new Object[0];
    }

    private static Serializable[] toSerializableArray(Object... args) {
        if (args == null || args.length == 0) {
            return new Serializable[0];
        }
        Serializable[] result = new Serializable[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Serializable s) {
                result[i] = s;
            } else if (args[i] != null) {
                result[i] = String.valueOf(args[i]);
            } else {
                result[i] = null;
            }
        }
        return result;
    }
}
