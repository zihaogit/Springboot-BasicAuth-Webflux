package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.dto.bulk.BulkJobMessage;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaBulkJobProducer implements BulkJobProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.bulk-jobs:bulk-jobs}")
    private String bulkJobsTopic;

    @Override
    public Mono<Void> sendJob(String jobId, BulkRegisterRequest request, String submittedBy) {
        return Mono.defer(() -> {
            BulkJobMessage message = new BulkJobMessage(jobId, request, submittedBy);
            log.info("Publishing bulk job {} to Kafka topic '{}'", jobId, bulkJobsTopic);
            return Mono.fromFuture(kafkaTemplate.send(bulkJobsTopic, jobId, message))
                    .doOnSuccess(result -> log.info("Successfully published bulk job {} to Kafka partition {}",
                            jobId, result != null && result.getRecordMetadata() != null ? result.getRecordMetadata().partition() : "unknown"))
                    .doOnError(err -> log.error("Failed to publish bulk job {} to Kafka", jobId, err))
                    .then();
        });
    }
}
