package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.constant.BulkJobStatus;
import com.example.springbootbasiclogin.dto.bulk.BulkOptions;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterItem;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import com.example.springbootbasiclogin.dto.bulk.JobStatusResponse;
import com.example.springbootbasiclogin.entity.BulkJob;
import com.example.springbootbasiclogin.entity.BulkJobErrorEntity;
import com.example.springbootbasiclogin.repo.BulkJobErrorRepository;
import com.example.springbootbasiclogin.repo.BulkJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkJobServiceTest {

    @Mock
    private BulkJobRepository bulkJobRepository;

    @Mock
    private BulkJobErrorRepository bulkJobErrorRepository;

    @Mock
    private BulkJobProducer bulkJobProducer;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BulkJobService bulkJobService;

    private BulkRegisterRequest request;

    @BeforeEach
    void setUp() {
        request = BulkRegisterRequest.builder()
                .options(new BulkOptions(true))
                .records(List.of(
                        new BulkRegisterItem("REF-1", "user1", "pass123", "user1@example.com")
                ))
                .build();
    }

    @Test
    @DisplayName("Should submit bulk job, save ACCEPTED status, send to producer, and return jobId")
    void shouldSubmitBulkJobSuccessfully() {
        when(bulkJobRepository.save(any(BulkJob.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(bulkJobProducer.sendJob(anyString(), any(BulkRegisterRequest.class), eq("admin_test"))).thenReturn(Mono.empty());

        StepVerifier.create(bulkJobService.submit(request, "admin_test"))
                .assertNext(jobId -> {
                    assertNotNull(jobId);
                    assertFalse(jobId.isBlank());
                })
                .verifyComplete();

        verify(bulkJobRepository).save(any(BulkJob.class));
        verify(bulkJobProducer).sendJob(anyString(), eq(request), eq("admin_test"));
    }

    @Test
    @DisplayName("Should return existing jobId when idempotencyKey already exists")
    void shouldReturnExistingJobIdOnIdempotencyKey() {
        BulkJob existing = new BulkJob(
                "job-existing",
                "idem-key-123",
                BulkJobStatus.ACCEPTED.name(),
                "admin_test",
                ZonedDateTime.now(),
                1,
                true,
                "{}"
        );

        when(bulkJobRepository.findByIdempotencyKey("idem-key-123")).thenReturn(Mono.just(existing));

        StepVerifier.create(bulkJobService.submit(request, "admin_test", "idem-key-123"))
                .expectNext("job-existing")
                .verifyComplete();

        verify(bulkJobRepository, never()).save(any(BulkJob.class));
        verify(bulkJobProducer, never()).sendJob(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Should return JobStatusResponse with errors for existing job")
    void shouldGetJobStatus() {
        BulkJob job = new BulkJob(
                "job-999",
                null,
                BulkJobStatus.PARTIALLY_COMPLETED.name(),
                "admin_test",
                ZonedDateTime.now().minusMinutes(5),
                ZonedDateTime.now(),
                2,
                1,
                1,
                true,
                "{}"
        );

        BulkJobErrorEntity error = BulkJobErrorEntity.builder()
                .id(1)
                .jobId("job-999")
                .recordIndex(1)
                .clientRefId("REF-2")
                .field("email")
                .message("Email already exists")
                .build();

        when(bulkJobRepository.findById("job-999")).thenReturn(Mono.just(job));
        when(bulkJobErrorRepository.findByJobIdOrderByRecordIndexAsc("job-999")).thenReturn(Flux.just(error));

        StepVerifier.create(bulkJobService.getStatus("job-999"))
                .assertNext(res -> {
                    assertEquals("job-999", res.getJobId());
                    assertEquals(BulkJobStatus.PARTIALLY_COMPLETED.name(), res.getStatus());
                    assertEquals("admin_test", res.getSubmittedBy());
                    assertEquals(2, res.getTotalRecords());
                    assertEquals(1, res.getSuccessCount());
                    assertEquals(1, res.getFailCount());
                    assertEquals(1, res.getErrors().size());
                    assertEquals("email", res.getErrors().get(0).field());
                    assertEquals("Email already exists", res.getErrors().get(0).message());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cleanup stuck jobs by marking them FAILED")
    void shouldCleanupStuckJobs() {
        when(bulkJobRepository.markStuckJobsAsFailed(any(ZonedDateTime.class), any(ZonedDateTime.class)))
                .thenReturn(Mono.just(3L));

        StepVerifier.create(bulkJobService.cleanupStuckJobs(Duration.ofMinutes(30)))
                .expectNext(3L)
                .verifyComplete();

        verify(bulkJobRepository).markStuckJobsAsFailed(any(ZonedDateTime.class), any(ZonedDateTime.class));
    }
}
