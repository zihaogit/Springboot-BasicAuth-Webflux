package com.example.springbootbasiclogin.exception;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.ErrorResponse;
import com.example.springbootbasiclogin.util.MessageUtil;
import com.example.springbootbasiclogin.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MessageUtil messageUtil;
    private final ResponseUtil responseUtil;

    public GlobalExceptionHandler(MessageUtil messageUtil, ResponseUtil responseUtil) {
        this.messageUtil = messageUtil;
        this.responseUtil = responseUtil;
    }

    @ExceptionHandler(value = {CustomException.class})
    public ResponseEntity<Object> handleCustomException(CustomException ex) {
        log.error("handleCustomException", ex);

        ErrorResponse response = ErrorResponse.builder()
                .resultCode(AuthResponseCode.AUTH_000520_NOT_FOUND.getCode())
                .resultMsg(messageUtil.getMessage(AuthResponseCode.AUTH_000520_NOT_FOUND.getMessageKey()))
                .errorDetails(ErrorResponse.ErrorDetailsBody.builder()
                        .stackTraces(ex.getStackTrace())
                        .build())
                .build();
        responseUtil.addTraceId(response);

        return new ResponseEntity<>(response, AuthResponseCode.AUTH_000520_NOT_FOUND.getHttpStatus());
    }
}