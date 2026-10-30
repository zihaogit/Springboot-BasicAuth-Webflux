package com.example.springbootbasiclogin.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkJobMessage implements Serializable {
    private String jobId;
    private BulkRegisterRequest request;
    private String submittedBy;
}
