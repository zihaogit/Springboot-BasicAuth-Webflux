package com.example.springbootbasiclogin.dto.bulk;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRegisterRequest {

    @Builder.Default
    private BulkOptions options = new BulkOptions();

    @NotNull(message = "Records list cannot be null")
    @NotEmpty(message = "Records list cannot be empty")
    @Size(max = 500, message = "Maximum batch size is 500 records")
    @Valid
    private List<BulkRegisterItem> records;
}
