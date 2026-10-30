package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.dto.bulk.BulkOptions;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterItem;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import com.example.springbootbasiclogin.entity.BulkJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InMemoryBulkJobProducerTest {

    @Mock
    private BulkJobProcessor bulkJobProcessor;

    @Test
    @DisplayName("Should enqueue job and process via background worker")
    void shouldEnqueueAndProcessJob() throws InterruptedException {
        when(bulkJobProcessor.processJob(eq("mem-job-1"), any(BulkRegisterRequest.class)))
                .thenReturn(Mono.just(new BulkJob()));

        InMemoryBulkJobProducer producer = new InMemoryBulkJobProducer(bulkJobProcessor);
        producer.startWorker();

        BulkRegisterRequest request = BulkRegisterRequest.builder()
                .options(new BulkOptions(true))
                .records(List.of(new BulkRegisterItem("REF-1", "u1", "p1", "u1@example.com")))
                .build();

        StepVerifier.create(producer.sendJob("mem-job-1", request, "admin"))
                .verifyComplete();

        // Allow background thread to process
        Thread.sleep(200);

        verify(bulkJobProcessor, timeout(1000)).processJob(eq("mem-job-1"), any(BulkRegisterRequest.class));
    }
}
