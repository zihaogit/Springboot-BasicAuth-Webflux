package com.example.springbootbasiclogin.repo;

import com.example.springbootbasiclogin.entity.BulkJobErrorEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface BulkJobErrorRepository extends ReactiveCrudRepository<BulkJobErrorEntity, Integer> {

    Flux<BulkJobErrorEntity> findByJobIdOrderByRecordIndexAsc(String jobId);
}
