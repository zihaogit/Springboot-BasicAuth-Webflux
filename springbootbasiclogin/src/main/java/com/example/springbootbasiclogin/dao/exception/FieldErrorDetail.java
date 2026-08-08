package com.example.springbootbasiclogin.dao.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class FieldErrorDetail {
    private String field;
    private String message;
}