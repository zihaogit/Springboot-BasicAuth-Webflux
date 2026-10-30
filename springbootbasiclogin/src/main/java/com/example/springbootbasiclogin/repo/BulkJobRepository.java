package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.BulkJob;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;

@Repository
public interface BulkJobRepository extends ReactiveCrudRepository<BulkJob, String> {

    Mono<BulkJob> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT * FROM bulk_jobs WHERE status = 'PROCESSING' AND submitted_at < :cutoff")
    Flux<BulkJob> findStuckProcessingJobs(ZonedDateTime cutoff);

    @Modifying
    @Query("UPDATE bulk_jobs SET status = 'FAILED', completed_at = :now WHERE status = 'PROCESSING' AND submitted_at < :cutoff")
    Mono<Long> markStuckJobsAsFailed(ZonedDateTime cutoff, ZonedDateTime now);
}
