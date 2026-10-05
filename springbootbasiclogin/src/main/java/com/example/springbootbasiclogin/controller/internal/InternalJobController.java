package com.example.springbootbasiclogin.controller.internal;

import com.example.springbootbasiclogin.repo.ProcessedWebhookEventRepository;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.repo.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/internal/jobs")
@RequiredArgsConstructor
public class InternalJobController {

    private final VerificationTokenRepository tokenRepository;
    private final ProcessedWebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * Job 1: Clean up expired OTP tokens older than 24 hours.
     */
    @PostMapping("/cleanup-otps")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupExpiredOtps() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusHours(24);
        log.info("Job [cleanup-otps]: deleting OTPs created before {}", cutoff);

        return tokenRepository.deleteExpiredOtps(cutoff)
                .map(deletedCount -> {
                    log.info("Job [cleanup-otps]: Deleted {} expired OTP record(s)", deletedCount);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "status", "SUCCESS",
                            "job", "cleanup-otps",
                            "deletedRecords", deletedCount,
                            "cutoffTime", cutoff.toString()
                    ));
                })
                .defaultIfEmpty(ResponseEntity.ok(Map.<String, Object>of(
                        "status", "SUCCESS",
                        "job", "cleanup-otps",
                        "deletedRecords", 0,
                        "cutoffTime", cutoff.toString()
                )));
    }

    /**
     * Job 2: Purge processed webhook idempotency records older than 30 days.
     */
    @PostMapping("/purge-webhook-events")
    public Mono<ResponseEntity<Map<String, Object>>> purgeWebhookEvents() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(30);
        log.info("Job [purge-webhook-events]: purging webhook events processed before {}", cutoff);

        return webhookEventRepository.deleteEventsOlderThan(cutoff)
                .map(deletedCount -> {
                    log.info("Job [purge-webhook-events]: Purged {} old webhook event(s)", deletedCount);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "status", "SUCCESS",
                            "job", "purge-webhook-events",
                            "deletedRecords", deletedCount,
                            "cutoffTime", cutoff.toString()
                    ));
                })
                .defaultIfEmpty(ResponseEntity.ok(Map.<String, Object>of(
                        "status", "SUCCESS",
                        "job", "purge-webhook-events",
                        "deletedRecords", 0,
                        "cutoffTime", cutoff.toString()
                )));
    }

    /**
     * Job 3: Prune abandoned unverified accounts that have no active OTP left.
     */
    @PostMapping("/cleanup-unverified-users")
    @org.springframework.transaction.annotation.Transactional
    public Mono<ResponseEntity<Map<String, Object>>> cleanupUnverifiedUsers() {
        log.info("Job [cleanup-unverified-users]: searching for unverified users without active OTP");

        return userRepository.findAbandonedUnverifiedUserIds()
                .flatMap(userId -> {
                    log.debug("Job [cleanup-unverified-users]: purging unverified userId={}", userId);
                    return roleRepository.deleteByUserId(userId)
                            .then(userRepository.deleteById(userId))
                            .thenReturn(userId);
                }, 10)
                .count()
                .map(totalDeleted -> {
                    log.info("Job [cleanup-unverified-users]: Purged {} unverified user(s)", totalDeleted);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "status", "SUCCESS",
                            "job", "cleanup-unverified-users",
                            "deletedRecords", totalDeleted
                    ));
                });
    }
}
