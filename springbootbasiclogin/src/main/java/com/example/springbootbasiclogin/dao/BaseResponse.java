package com.example.springbootbasiclogin.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse {
    private String resultCode;
    private String resultMsg;
    private String traceId;
    private Instant timestamp;
    private Object result;
    private Object errorDetails;

    public static BaseResponse success() {
        return BaseResponse.builder().resultCode("0").resultMsg("success").build();
    }
}