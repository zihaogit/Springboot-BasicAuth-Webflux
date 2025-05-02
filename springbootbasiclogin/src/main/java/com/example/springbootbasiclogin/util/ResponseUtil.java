package com.example.springbootbasiclogin.util;

import brave.Span;
import brave.Tracer;
import com.example.springbootbasiclogin.dao.BaseResponse;
import com.example.springbootbasiclogin.dao.WebClientExceptionResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ResponseUtil {
    private final Tracer tracer;

    public ResponseUtil(Tracer tracer) {
        this.tracer = tracer;
    }

    public void addTraceId(BaseResponse baseResponse) {
        Span span = tracer.currentSpan();

        if (span != null) {
            baseResponse.setTraceId(span.context().traceIdString());
        }
        baseResponse.setTimestamp(Instant.now());
    }

    public void addTraceId(WebClientExceptionResponse webClientExceptionResponse) {
        Span span = tracer.currentSpan();

        if (span != null) {
            webClientExceptionResponse.setTraceId(span.context().traceIdString());
        }
        webClientExceptionResponse.setTimestamp(Instant.now());
    }
}
