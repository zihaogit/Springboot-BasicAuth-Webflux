package com.example.springbootbasiclogin.controller.bulk;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.dto.auth.RegisterRequest;
import com.example.springbootbasiclogin.dto.bulk.BulkJobAccepted;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import com.example.springbootbasiclogin.dto.bulk.JobStatusResponse;
import com.example.springbootbasiclogin.dto.user.UserResponse;
import com.example.springbootbasiclogin.service.auth.RegistrationService;
import com.example.springbootbasiclogin.service.bulk.BulkJobService;
import com.example.springbootbasiclogin.util.SanitizeUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@Validated
@RequiredArgsConstructor
public class BulkJobController {

    private final BulkJobService bulkJobService;
    private final RegistrationService registrationService;

    /**
     * Single Load: Synchronous / Reactive pattern.
     * Returns 201 Created immediately or error response.
     */
    @PostMapping("/api/users")
    public Mono<ResponseEntity<UserResponse>> createUser(@RequestBody @Valid RegisterRequest request) {
        return registrationService.registerUser(request)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user)));
    }

    /**
     * Bulk Load: Asynchronous UUID pattern (Admin only).
     * Returns 202 Accepted with Job ID immediately.
     * Supports optional X-Idempotency-Key header for submission deduplication.
     */
    @PostMapping("/api/users/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Authenticated(roles = {"ADMIN"})
    public Mono<ResponseEntity<BulkJobAccepted>> bulkCreateUsers(
            @RequestBody @Valid BulkRegisterRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        String adminUsername = authentication != null ? authentication.getName() : "system";
        log.info("Received bulk user creation request with {} records from admin '{}' (idempotencyKey={})",
                request.getRecords() != null ? request.getRecords().size() : 0,
                SanitizeUtil.forLog(adminUsername),
                SanitizeUtil.forLog(idempotencyKey));

        return bulkJobService.submit(request, adminUsername, idempotencyKey)
                .map(jobId -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new BulkJobAccepted(jobId, "ACCEPTED")));
    }

    /**
     * Job Status Polling endpoint (Admin only).
     */
    @GetMapping("/api/jobs/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Authenticated(roles = {"ADMIN"})
    public Mono<ResponseEntity<JobStatusResponse>> getJobStatus(@PathVariable String jobId) {
        return bulkJobService.getStatus(jobId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
