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

    @ExceptionHandler({org.springframework.web.server.ServerWebInputException.class, org.springframework.web.bind.support.WebExchangeBindException.class})
    public Mono<ResponseEntity<ErrorResponse>> handleBadRequestException(Exception ex) {
        log.warn("Bad request input validation error: {}", ex.getMessage());

        String message = messageUtil.getMessage(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER.getMessageKey());
        ErrorResponse response = ErrorResponse.builder()
                .resultCode(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER.getCode())
                .resultMsg(message != null ? message : ex.getMessage())
                .build();

        return Mono.just(ResponseEntity
                .status(AuthResponseCode.AUTH_000101_INVALID_OR_MISSING_PARAMETER.getHttpStatus())
                .body(response));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleMissedException(Exception ex) {
        log.error("handleMissedException", ex);

        ErrorResponse response = ErrorResponse.builder()
                .resultCode(AuthResponseCode.AUTH_000520_NOT_FOUND.getCode())
                .resultMsg(ex.getMessage())
                .build();

        return Mono.just(ResponseEntity
                .status(AuthResponseCode.AUTH_000520_NOT_FOUND.getHttpStatus())
                .body(response));
    }

    @ExceptionHandler(value = {CustomException.class})
    public Mono<ResponseEntity<ErrorResponse>> handleCustomException(CustomException ex) {
        if (ex.getAuthResponseCode().getHttpStatus().is5xxServerError()) {
            log.error("Server error handling request: [{}] {}", ex.getAuthResponseCode().getCode(), ex.getMessage(), ex);
        } else {
            log.warn("Client error: [{}] {}", ex.getAuthResponseCode().getCode(), ex.getMessage());
        }

        // Choose message based on presence of messageArgs
        String message = (ex.getMessageArgs() != null && ex.getMessageArgs().length > 0)
                ? messageUtil.getMessageWithArgs(ex.getAuthResponseCode().getMessageKey(), ex.getMessageArgs())
                : messageUtil.getMessage(ex.getAuthResponseCode().getMessageKey());

        ErrorResponse response = ErrorResponse.builder()
                .resultCode(ex.getAuthResponseCode().getCode())
                .resultMsg(message != null ? message : ex.getMessage())
                .build();

        return Mono.just(ResponseEntity
                .status(ex.getAuthResponseCode().getHttpStatus())
                .body(response));
    }
}