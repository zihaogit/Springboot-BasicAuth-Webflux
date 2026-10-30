package com.example.springbootbasiclogin.dto.bulk;

public record BulkJobError(
        Integer index,
        String clientRefId,
        String field,
        String message
) {}
