package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.constant.BulkJobStatus;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterItem;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import com.example.springbootbasiclogin.entity.BulkJob;
import com.example.springbootbasiclogin.entity.BulkJobErrorEntity;
import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.repo.BulkJobErrorRepository;
import com.example.springbootbasiclogin.repo.BulkJobRepository;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkJobProcessor {

    private final BulkJobRepository bulkJobRepository;
    private final BulkJobErrorRepository bulkJobErrorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<BulkJob> processJob(String jobId, BulkRegisterRequest request) {
        log.info("Starting processing for bulk job {}", jobId);

        return bulkJobRepository.findById(jobId)
                .flatMap(job -> {
                    job.setStatus(BulkJobStatus.PROCESSING.name());
                    return bulkJobRepository.save(job);
                })
                .flatMap(job -> {
                    boolean continueOnError = request.getOptions() != null && request.getOptions().isContinueOnError();
                    AtomicInteger successCount = new AtomicInteger(0);
                    AtomicInteger failCount = new AtomicInteger(0);
                    AtomicBoolean stopProcessing = new AtomicBoolean(false);

                    return Flux.range(0, request.getRecords().size())
                            .concatMap(index -> {
                                if (stopProcessing.get()) {
                                    return Mono.empty();
                                }

                                BulkRegisterItem item = request.getRecords().get(index);
                                return processRecord(item)
                                        .doOnSuccess(v -> successCount.incrementAndGet())
                                        .onErrorResume(e -> {
                                            failCount.incrementAndGet();
                                            if (!continueOnError) {
                                                stopProcessing.set(true);
                                            }
                                            return recordError(jobId, index, item, e);
                                        });
                            })
                            .then(Mono.defer(() -> {
                                int totalSuccess = successCount.get();
                                int totalFail = failCount.get();

                                String finalStatus;
                                if (totalFail == 0) {
                                    finalStatus = BulkJobStatus.COMPLETED.name();
                                } else if (totalSuccess > 0) {
                                    finalStatus = BulkJobStatus.PARTIALLY_COMPLETED.name();
                                } else {
                                    finalStatus = BulkJobStatus.FAILED.name();
                                }

                                job.setStatus(finalStatus);
                                job.setSuccessCount(totalSuccess);
                                job.setFailCount(totalFail);
                                job.setCompletedAt(ZonedDateTime.now());

                                log.info("Bulk job {} finished: status={}, success={}, failed={}",
                                        jobId, finalStatus, totalSuccess, totalFail);
                                return bulkJobRepository.save(job);
                            }));
                });
    }

    private Mono<Void> processRecord(BulkRegisterItem item) {
        return userRepository.findByUsername(item.getUsername())
                .flatMap(existing -> Mono.<Void>error(new IllegalArgumentException("Username '" + item.getUsername() + "' already exists")))
                .switchIfEmpty(Mono.defer(() ->
                        userRepository.findByEmail(item.getEmail())
                                .flatMap(existing -> Mono.<Void>error(new IllegalArgumentException("Email '" + item.getEmail() + "' already exists")))
                                .switchIfEmpty(Mono.defer(() -> {
                                    Users user = new Users();
                                    user.setUsername(item.getUsername());
                                    user.setEmail(item.getEmail());
                                    user.setPassword(passwordEncoder.encode(item.getPassword()));
                                    user.setActive(true);
                                    user.setVerified(true);

                                    return userRepository.save(user)
                                            .flatMap(savedUser -> {
                                                Roles role = new Roles();
                                                role.setUserId(savedUser.getId());
                                                role.setRole("USER");
                                                return roleRepository.save(role);
                                            })
                                            .then();
                                }))
                ));
    }

    private Mono<Void> recordError(String jobId, int index, BulkRegisterItem item, Throwable error) {
        String field = "unknown";
        String message = error.getMessage() != null ? error.getMessage() : "Processing error";

        if (message.toLowerCase().contains("username")) {
            field = "username";
        } else if (message.toLowerCase().contains("email")) {
            field = "email";
        } else if (message.toLowerCase().contains("password")) {
            field = "password";
        }

        BulkJobErrorEntity errorEntity = BulkJobErrorEntity.builder()
                .jobId(jobId)
                .recordIndex(index)
                .clientRefId(item.getClientRefId())
                .field(field)
                .message(message)
                .build();

        return bulkJobErrorRepository.save(errorEntity).then();
    }
}
