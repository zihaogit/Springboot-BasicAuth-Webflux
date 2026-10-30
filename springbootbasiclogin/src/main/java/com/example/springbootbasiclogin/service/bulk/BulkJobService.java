package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.constant.BulkJobStatus;
import com.example.springbootbasiclogin.dto.bulk.BulkJobError;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import com.example.springbootbasiclogin.dto.bulk.JobStatusResponse;
import com.example.springbootbasiclogin.entity.BulkJob;
import com.example.springbootbasiclogin.repo.BulkJobErrorRepository;
import com.example.springbootbasiclogin.repo.BulkJobRepository;
import com.example.springbootbasiclogin.util.SanitizeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkJobService {

    private final BulkJobRepository bulkJobRepository;
    private final BulkJobErrorRepository bulkJobErrorRepository;
    private final BulkJobProducer bulkJobProducer;
    private final ObjectMapper objectMapper;

    public Mono<String> submit(BulkRegisterRequest request, String adminUsername) {
        return submit(request, adminUsername, null);
    }

    public Mono<String> submit(BulkRegisterRequest request, String adminUsername, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return bulkJobRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> {
                        log.info("Idempotent submission detected for key '{}', returning existing jobId '{}'",
                                SanitizeUtil.forLog(idempotencyKey), existing.getJobId());
                        return existing.getJobId();
                    })
                    .switchIfEmpty(Mono.defer(() -> createAndEnqueueJob(request, adminUsername, idempotencyKey)));
        }

        return createAndEnqueueJob(request, adminUsername, null);
    }

    private Mono<String> createAndEnqueueJob(BulkRegisterRequest request, String adminUsername, String idempotencyKey) {
        String jobId = UUID.randomUUID().toString();
        String payloadJson = null;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize bulk request for audit: {}", e.getMessage());
        }

        boolean continueOnError = request.getOptions() != null && request.getOptions().isContinueOnError();
        BulkJob job = new BulkJob(
                jobId,
                idempotencyKey,
                BulkJobStatus.ACCEPTED.name(),
                adminUsername,
                ZonedDateTime.now(),
                request.getRecords() != null ? request.getRecords().size() : 0,
                continueOnError,
                payloadJson
        );

        return bulkJobRepository.save(job)
                .flatMap(savedJob -> bulkJobProducer.sendJob(jobId, request, adminUsername))
                .thenReturn(jobId);
    }

    public Mono<JobStatusResponse> getStatus(String jobId) {
        return bulkJobRepository.findById(jobId)
                .flatMap(job -> bulkJobErrorRepository.findByJobIdOrderByRecordIndexAsc(jobId)
                        .map(err -> new BulkJobError(
                                err.getRecordIndex(),
                                err.getClientRefId(),
                                err.getField(),
                                err.getMessage()
                        ))
                        .collectList()
                        .map(errorList -> JobStatusResponse.builder()
                                .jobId(job.getJobId())
                                .status(job.getStatus())
                                .submittedBy(job.getSubmittedBy())
                                .submittedAt(job.getSubmittedAt())
                                .completedAt(job.getCompletedAt())
                                .totalRecords(job.getTotalRecords())
                                .successCount(job.getSuccessCount())
                                .failCount(job.getFailCount())
                                .errors(errorList)
                                .build()
                        )
                );
    }

    public Mono<Long> cleanupStuckJobs(Duration maxDuration) {
        ZonedDateTime cutoff = ZonedDateTime.now().minus(maxDuration);
        ZonedDateTime now = ZonedDateTime.now();
        log.info("Running Sweeper/Reaper for stuck bulk jobs started before {}", cutoff);
        return bulkJobRepository.markStuckJobsAsFailed(cutoff, now)
                .doOnSuccess(count -> log.info("Sweeper/Reaper marked {} orphaned job(s) as FAILED", count));
    }
}
