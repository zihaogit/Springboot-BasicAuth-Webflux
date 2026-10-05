package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.ProcessedWebhookEvent;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;

@Repository
public interface ProcessedWebhookEventRepository extends ReactiveCrudRepository<ProcessedWebhookEvent, String> {
    Mono<Boolean> existsByEventId(String eventId);

    @Modifying
    @Query("DELETE FROM processed_webhook_events WHERE processed_at < :cutoffTime")
    Mono<Long> deleteEventsOlderThan(ZonedDateTime cutoffTime);
}
