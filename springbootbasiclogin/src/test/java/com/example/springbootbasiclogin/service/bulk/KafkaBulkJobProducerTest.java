package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.dto.bulk.BulkOptions;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterItem;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaBulkJobProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaBulkJobProducer kafkaBulkJobProducer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaBulkJobProducer, "bulkJobsTopic", "bulk-jobs");
    }

    @Test
    @DisplayName("Should publish bulk job message to Kafka topic")
    void shouldPublishJobToKafka() {
        BulkRegisterRequest request = BulkRegisterRequest.builder()
                .options(new BulkOptions(true))
                .records(List.of(new BulkRegisterItem("REF-1", "user1", "pass123", "u1@example.com")))
                .build();

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("bulk-jobs"), eq("job-kafka-1"), any())).thenReturn(future);

        StepVerifier.create(kafkaBulkJobProducer.sendJob("job-kafka-1", request, "admin_user"))
                .verifyComplete();

        verify(kafkaTemplate).send(eq("bulk-jobs"), eq("job-kafka-1"), any());
    }
}
