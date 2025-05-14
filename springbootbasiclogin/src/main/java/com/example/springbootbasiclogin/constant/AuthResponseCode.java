package com.example.springbootbasiclogin.constant;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AuthResponseCode {

    AUTH_000000_SUCCESS("0", "auth.success.000000", HttpStatus.OK),
    AUTH_000101_INVALID_OR_MISSING_PARAMETER("AUTH-000101", "auth.error.000101", HttpStatus.BAD_REQUEST),
    AUTH_000102_PASSWORD_POLICY_VIOLATION("AUTH-000102", "auth.error.000102", HttpStatus.BAD_REQUEST),
    AUTH_000103_INVALID_ID_TOKEN("AUTH-000103", "auth.error.000103", HttpStatus.BAD_REQUEST),
    AUTH_000104_INVALID_AUTHENTICATION("AUTH-000104", "auth.error.000104", HttpStatus.BAD_REQUEST),
    AUTH_000105_INVALID_SECRET_KEY("AUTH-000105", "auth.error.000105", HttpStatus.BAD_REQUEST),
    AUTH_000106_INVALID_PHONE_NUMBER_FORMAT("AUTH-000106", "auth.error.000106", HttpStatus.BAD_REQUEST),
    AUTH_000107_EMAIL_REGISTERED_AS_ENTRA("AUTH-000107", "auth.error.000107", HttpStatus.BAD_REQUEST),
    AUTH_000108_INVALID_JWT_OR_ACCESS_TOKEN("AUTH-000108", "auth.error.000108", HttpStatus.BAD_REQUEST),
    AUTH_000109_USER_IS_REGISTERED("AUTH-000109", "auth.error.000109", HttpStatus.BAD_REQUEST),
    AUTH_000110_USER_NOT_REGISTERED("AUTH-000110", "auth.error.000110", HttpStatus.BAD_REQUEST),
    AUTH_000130_FAILED_DESERIALIZE_JSON("AUTH-000130", "auth.error.000130", HttpStatus.INTERNAL_SERVER_ERROR),
    AUTH_000150_EMAIL_IS_REQUIRED("AUTH-000150", "auth.error.000150", HttpStatus.BAD_REQUEST),
    AUTH_000151_EMAIL_IS_INVALID("AUTH-000151", "auth.error.000151", HttpStatus.BAD_REQUEST),
    AUTH_000201_FAILED_SEND_EMAIL("AUTH-000201", "auth.error.000201", HttpStatus.BAD_REQUEST),
    AUTH_000202_INVALID_VERIFICATION_LINK("AUTH-000202", "auth.error.000202", HttpStatus.BAD_REQUEST),
    AUTH_000203_EXPIRED_VERIFICATION_LINK("AUTH-000203", "auth.error.000203", HttpStatus.BAD_REQUEST),
    AUTH_000401_UNAUTHORIZED("AUTH-000401", "auth.error.000401", HttpStatus.UNAUTHORIZED),
    AUTH_000403_ACCESS_DENIED("AUTH-000403", "auth.error.000403", HttpStatus.FORBIDDEN),
    AUTH_000404_NOT_FOUND("AUTH-000404", "auth.error.000404", HttpStatus.NOT_FOUND),
    AUTH_000500_SERVER_ERROR("AUTH-000500", "auth.error.000500", HttpStatus.INTERNAL_SERVER_ERROR),
    AUTH_000520_NOT_FOUND("AUTH-000520", "auth.error.000520", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String messageKey;
    private final HttpStatus httpStatus;

    AuthResponseCode(String code, String messageKey, HttpStatus httpStatus) {
        this.code = code;
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }
}
