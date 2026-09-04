package com.example.springbootbasiclogin.exception;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.exception.ErrorResponse;
import com.example.springbootbasiclogin.util.MessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MessageUtil messageUtil;

    public GlobalExceptionHandler(MessageUtil messageUtil) {
        this.messageUtil = messageUtil;
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleMissedException(Exception ex) {
        log.error("handleMissedException", ex);

        ErrorResponse response = ErrorResponse.builder()
                .resultCode(AuthResponseCode.AUTH_000520_NOT_FOUND.getCode())
                .resultMsg(ex.getMessage())
                .errorDetails(ErrorResponse.ErrorDetailsBody.builder()
                        .stackTraces(ex.getStackTrace())
                        .build())
                .build();

        return Mono.just(ResponseEntity
                .status(AuthResponseCode.AUTH_000520_NOT_FOUND.getHttpStatus())
                .body(response));
    }

    @ExceptionHandler(value = {CustomException.class})
    public ResponseEntity<Object> handleCustomException(CustomException ex) {
        log.error("handleCustomException", ex);

        // Choose message based on presence of messageArgs
        String message = (ex.getMessageArgs() != null && ex.getMessageArgs().length > 0)
                ? messageUtil.getMessageWithArgs(ex.getAuthResponseCode().getMessageKey(), ex.getMessageArgs())
                : messageUtil.getMessage(ex.getAuthResponseCode().getMessageKey());

        ErrorResponse response = ErrorResponse.builder()
                .resultCode(ex.getAuthResponseCode().getCode())
                .resultMsg(message)
                .errorDetails(ErrorResponse.ErrorDetailsBody.builder()
                        .stackTraces(ex.getStackTrace())
                        .build())
                .build();

        return new ResponseEntity<>(response, ex.getAuthResponseCode().getHttpStatus());
    }
}