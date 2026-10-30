package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import reactor.core.publisher.Mono;

public interface BulkJobProducer {
    Mono<Void> sendJob(String jobId, BulkRegisterRequest request, String submittedBy);
}
