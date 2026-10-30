package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.dto.bulk.BulkJobMessage;
import com.example.springbootbasiclogin.exception.CustomException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaBulkJobConsumer {

    private final BulkJobProcessor bulkJobProcessor;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            exclude = {
                    ValidationException.class,
                    DuplicateKeyException.class,
                    CustomException.class,
                    IllegalArgumentException.class
            },
            dltTopicSuffix = "-dead-letter",
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "${app.kafka.topics.bulk-jobs:bulk-jobs}", groupId = "${spring.kafka.consumer.group-id:bulk-job-group}")
    public void processBulkJob(BulkJobMessage message) {
        log.info("Kafka consumer received bulk job: {}", message.getJobId());
        bulkJobProcessor.processJob(message.getJobId(), message.getRequest()).block();
    }

    @DltHandler
    public void handleDlt(BulkJobMessage message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Bulk job {} ended up in Dead Letter Topic (DLT): {}", message.getJobId(), topic);
    }
}
