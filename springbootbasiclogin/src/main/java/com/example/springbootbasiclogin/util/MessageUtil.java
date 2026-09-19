package com.example.springbootbasiclogin.util;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MessageUtil {

    private final MessageSource messageSource;

    public MessageUtil(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getMessage(String code) {
        return messageSource.getMessage(code, new Object[0], Locale.ENGLISH);
    }

    public String getMessageWithArgs(String code, Object[] args) {
        return messageSource.getMessage(code, args, Locale.ENGLISH);
    }
}
