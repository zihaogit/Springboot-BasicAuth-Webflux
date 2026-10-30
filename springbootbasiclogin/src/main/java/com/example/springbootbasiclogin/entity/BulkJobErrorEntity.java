package com.example.springbootbasiclogin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Table("bulk_job_errors")
public class BulkJobErrorEntity {

    @Id
    @Column("id")
    private Integer id;

    @Column("job_id")
    private String jobId;

    @Column("record_index")
    private Integer recordIndex;

    @Column("client_ref_id")
    private String clientRefId;

    @Column("field")
    private String field;

    @Column("message")
    private String message;
}
