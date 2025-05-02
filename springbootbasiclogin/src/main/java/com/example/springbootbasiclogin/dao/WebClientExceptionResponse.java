package com.example.springbootbasiclogin.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebClientExceptionResponse {

    private final String resultCode;
    private final String resultMsg;
    private final WebClientExceptionDetailResponse errorDetails;
    private String traceId;
    private Instant timestamp;

    public WebClientExceptionResponse(
            String resultCode,
            String resultMsg,
            String clientResultCode,
            String clientResultMsg) {

        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
        if (clientResultCode != null || clientResultMsg != null) {
            this.errorDetails = new WebClientExceptionDetailResponse(clientResultCode, clientResultMsg);
        } else {
            this.errorDetails = null;
        }
    }
}
