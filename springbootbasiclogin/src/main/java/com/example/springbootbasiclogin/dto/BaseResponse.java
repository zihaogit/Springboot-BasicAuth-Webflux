package com.example.springbootbasiclogin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {

    @Builder.Default
    private String resultCode = "0";

    @Builder.Default
    private String resultMsg = "success";

    private String traceId;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private T result;

    private Object errorDetails;

    public static <T> BaseResponse<T> success() {
        return BaseResponse.<T>builder()
                .resultCode("0")
                .resultMsg("success")
                .timestamp(Instant.now())
                .build();
    }

    public static <T> BaseResponse<T> success(T result) {
        return BaseResponse.<T>builder()
                .resultCode("0")
                .resultMsg("success")
                .timestamp(Instant.now())
                .result(result)
                .build();
    }

    public static <T> BaseResponse<T> success(String resultMsg, T result) {
        return BaseResponse.<T>builder()
                .resultCode("0")
                .resultMsg(resultMsg)
                .timestamp(Instant.now())
                .result(result)
                .build();
    }

    public static <T> BaseResponse<T> error(String resultCode, String resultMsg) {
        return BaseResponse.<T>builder()
                .resultCode(resultCode)
                .resultMsg(resultMsg)
                .timestamp(Instant.now())
                .build();
    }
}
