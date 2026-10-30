package com.example.springbootbasiclogin.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkOptions {
    private boolean continueOnError = true;
}
