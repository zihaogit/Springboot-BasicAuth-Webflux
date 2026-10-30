package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.dto.bulk.BulkJobMessage;
import com.example.springbootbasiclogin.dto.bulk.BulkOptions;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterItem;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import com.example.springbootbasiclogin.entity.BulkJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaBulkJobConsumerTest {

    @Mock
    private BulkJobProcessor bulkJobProcessor;

    @InjectMocks
    private KafkaBulkJobConsumer kafkaBulkJobConsumer;

    @Test
    @DisplayName("Should invoke BulkJobProcessor when Kafka message is received")
    void shouldInvokeProcessorOnKafkaMessage() {
        BulkRegisterRequest request = BulkRegisterRequest.builder()
                .options(new BulkOptions(true))
                .records(List.of(new BulkRegisterItem("REF-1", "user1", "pass123", "u1@example.com")))
                .build();
        BulkJobMessage message = new BulkJobMessage("job-kafka-con-1", request, "admin_user");

        when(bulkJobProcessor.processJob(eq("job-kafka-con-1"), any(BulkRegisterRequest.class)))
                .thenReturn(Mono.just(new BulkJob()));

        kafkaBulkJobConsumer.processBulkJob(message);

        verify(bulkJobProcessor).processJob(eq("job-kafka-con-1"), eq(request));
    }

    @Test
    @DisplayName("Should handle DLT callback without error")
    void shouldHandleDltCallback() {
        BulkJobMessage message = new BulkJobMessage("job-dlq-1", new BulkRegisterRequest(), "admin_user");
        kafkaBulkJobConsumer.handleDlt(message, "bulk-jobs-dead-letter");
        // No exception thrown
    }
}
