package com.example.springbootbasiclogin.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MessageUtil {

    @Autowired
    MessageSource messageSource;

    public String getMessage(String code) {
        return messageSource.getMessage(code, new Object[0], Locale.ENGLISH);
    }

    public String getMessageWithArgs(String code, Object[] args) {
        return messageSource.getMessage(code, args, Locale.ENGLISH);
    }
}
