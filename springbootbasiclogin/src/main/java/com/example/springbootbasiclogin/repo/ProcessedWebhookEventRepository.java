package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.ProcessedWebhookEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ProcessedWebhookEventRepository extends ReactiveCrudRepository<ProcessedWebhookEvent, String> {
    Mono<Boolean> existsByEventId(String eventId);
}
