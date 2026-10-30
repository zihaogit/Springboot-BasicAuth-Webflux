package com.example.springbootbasiclogin.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusResponse {
    private String jobId;
    private String status;
    private String submittedBy;
    private ZonedDateTime submittedAt;
    private ZonedDateTime completedAt;
    private Integer totalRecords;
    private Integer successCount;
    private Integer failCount;
    private List<BulkJobError> errors;
}
