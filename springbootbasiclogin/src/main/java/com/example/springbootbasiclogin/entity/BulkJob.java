package com.example.springbootbasiclogin.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

@NoArgsConstructor
@Getter
@Setter
@Table("bulk_jobs")
public class BulkJob implements Persistable<String> {

    @Id
    @Column("job_id")
    private String jobId;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("status")
    private String status;

    @Column("submitted_by")
    private String submittedBy;

    @Column("submitted_at")
    private ZonedDateTime submittedAt;

    @Column("completed_at")
    private ZonedDateTime completedAt;

    @Column("total_records")
    private int totalRecords;

    @Column("success_count")
    private int successCount;

    @Column("fail_count")
    private int failCount;

    @Column("continue_on_error")
    private boolean continueOnError = true;

    @Column("payload_json")
    private String payloadJson;

    @Transient
    private boolean newRecord = true;

    @PersistenceCreator
    public BulkJob(String jobId, String idempotencyKey, String status, String submittedBy, ZonedDateTime submittedAt,
                   ZonedDateTime completedAt, int totalRecords, int successCount, int failCount,
                   boolean continueOnError, String payloadJson) {
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.completedAt = completedAt;
        this.totalRecords = totalRecords;
        this.successCount = successCount;
        this.failCount = failCount;
        this.continueOnError = continueOnError;
        this.payloadJson = payloadJson;
        this.newRecord = false;
    }

    public BulkJob(String jobId, String idempotencyKey, String status, String submittedBy, ZonedDateTime submittedAt,
                   int totalRecords, boolean continueOnError, String payloadJson) {
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.totalRecords = totalRecords;
        this.continueOnError = continueOnError;
        this.payloadJson = payloadJson;
        this.newRecord = true;
    }

    @Override
    public String getId() {
        return jobId;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
