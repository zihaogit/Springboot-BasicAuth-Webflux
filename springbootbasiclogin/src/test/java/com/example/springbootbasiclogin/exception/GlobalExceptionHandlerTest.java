package com.example.springbootbasiclogin.exception;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.dao.exception.ErrorResponse;
import com.example.springbootbasiclogin.util.MessageUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageUtil messageUtil;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    @DisplayName("Handle CustomException without message args")
    void testHandleCustomExceptionWithoutArgs() {
        when(messageUtil.getMessage(AuthResponseCode.AUTH_000401_UNAUTHORIZED.getMessageKey()))
                .thenReturn("Unauthorized access");

        CustomException ex = new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED);
        ResponseEntity<Object> response = globalExceptionHandler.handleCustomException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody() instanceof ErrorResponse);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertEquals(AuthResponseCode.AUTH_000401_UNAUTHORIZED.getCode(), body.getResultCode());
        assertEquals("Unauthorized access", body.getResultMsg());
    }

    @Test
    @DisplayName("Handle CustomException with message args")
    void testHandleCustomExceptionWithArgs() {
        when(messageUtil.getMessageWithArgs(eq(AuthResponseCode.AUTH_000111_FAILED_ACCESS_MEMBER_CLASS.getMessageKey()), any()))
                .thenReturn("Failed access member class Users");

        CustomException ex = new CustomException(
                AuthResponseCode.AUTH_000111_FAILED_ACCESS_MEMBER_CLASS,
                new RuntimeException(),
                "Users"
        );
        ResponseEntity<Object> response = globalExceptionHandler.handleCustomException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody() instanceof ErrorResponse);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertEquals(AuthResponseCode.AUTH_000111_FAILED_ACCESS_MEMBER_CLASS.getCode(), body.getResultCode());
        assertEquals("Failed access member class Users", body.getResultMsg());
    }

    @Test
    @DisplayName("Handle Generic Exception")
    void testHandleGenericException() {
        Exception ex = new RuntimeException("Generic error");
        StepVerifier.create(globalExceptionHandler.handleMissedException(ex))
                .assertNext(response -> {
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals("Generic error", response.getBody().getResultMsg());
                })
                .verifyComplete();
    }
}
