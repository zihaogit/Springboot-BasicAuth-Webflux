package com.example.springbootbasiclogin.exception;

import com.example.springbootbasiclogin.dao.ErrorResponse;
import com.example.springbootbasiclogin.util.MessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MessageUtil messageUtil;

    public GlobalExceptionHandler(MessageUtil messageUtil) {
        this.messageUtil = messageUtil;
    }

    @ExceptionHandler(value = {CustomException.class})
    public ResponseEntity<Object> handleCustomException(CustomException ex) {
        log.error("handleCustomException", ex);

        ErrorResponse response = ErrorResponse.builder()
                .resultCode(ex.getAuthResponseCode().getCode())
                .resultMsg(messageUtil.getMessage(ex.getAuthResponseCode().getMessageKey()))
                .errorDetails(ErrorResponse.ErrorDetailsBody.builder()
                        .stackTraces(ex.getStackTrace())
                        .build())
                .build();

        return new ResponseEntity<>(response, ex.getAuthResponseCode().getHttpStatus());
    }
}