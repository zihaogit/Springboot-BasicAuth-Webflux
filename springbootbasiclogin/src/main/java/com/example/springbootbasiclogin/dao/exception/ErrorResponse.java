package com.example.springbootbasiclogin.dao.exception;

import com.example.springbootbasiclogin.dao.BaseResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ErrorResponse extends BaseResponse {

    @Data
    @Builder
    public static class ErrorDetailsBody {
        private StackTraceElement[] stackTraces;
        @Schema(description = "Details of what field has error and what is the error")
        private List<FieldErrorDetail> invalidFields;
    }
}