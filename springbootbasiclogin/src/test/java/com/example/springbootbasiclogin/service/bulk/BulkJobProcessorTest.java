package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.constant.BulkJobStatus;
import com.example.springbootbasiclogin.dto.bulk.BulkOptions;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkJobProcessorTest {

    @Mock
    private BulkJobRepository bulkJobRepository;

    @Mock
    private BulkJobErrorRepository bulkJobErrorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private BulkJobProcessor bulkJobProcessor;

    private BulkJob sampleJob;

    @BeforeEach
    void setUp() {
        sampleJob = new BulkJob(
                "job-123",
                null,
                BulkJobStatus.ACCEPTED.name(),
                "admin_user",
                ZonedDateTime.now(),
                2,
                true,
                "{}"
        );
    }

    @Test
    @DisplayName("Should process all records successfully when no validation errors occur")
    void shouldProcessJobSuccessfully() {
        BulkRegisterRequest request = BulkRegisterRequest.builder()
                .options(new BulkOptions(true))
                .records(List.of(
                        new BulkRegisterItem("REF-1", "john_doe", "pass123", "john@example.com"),
                        new BulkRegisterItem("REF-2", "jane_doe", "pass456", "jane@example.com")
                ))
                .build();

        when(bulkJobRepository.findById("job-123")).thenReturn(Mono.just(sampleJob));
        when(bulkJobRepository.save(any(BulkJob.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(userRepository.findByUsername(anyString())).thenReturn(Mono.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
            Users u = invocation.getArgument(0);
            u.setId(101);
            return Mono.just(u);
        });
        when(roleRepository.save(any(Roles.class))).thenReturn(Mono.just(new Roles()));

        StepVerifier.create(bulkJobProcessor.processJob("job-123", request))
                .assertNext(completedJob -> {
                    assertEquals(BulkJobStatus.COMPLETED.name(), completedJob.getStatus());
                    assertEquals(2, completedJob.getSuccessCount());
                    assertEquals(0, completedJob.getFailCount());
                    assertNotNull(completedJob.getCompletedAt());
                })
                .verifyComplete();

        verify(userRepository, times(2)).save(any(Users.class));
        verify(roleRepository, times(2)).save(any(Roles.class));
        verify(bulkJobErrorRepository, never()).save(any(BulkJobErrorEntity.class));
    }

    @Test
    @DisplayName("Should mark PARTIALLY_COMPLETED when one record fails and continueOnError is true")
    void shouldHandlePartialFailureWithContinueOnError() {
        BulkRegisterRequest request = BulkRegisterRequest.builder()
                .options(new BulkOptions(true))
                .records(List.of(
                        new BulkRegisterItem("REF-1", "john_doe", "pass123", "john@example.com"),
                        new BulkRegisterItem("REF-2", "duplicate_user", "pass456", "existing@example.com")
                ))
                .build();

        when(bulkJobRepository.findById("job-123")).thenReturn(Mono.just(sampleJob));
        when(bulkJobRepository.save(any(BulkJob.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // First user succeeds
        when(userRepository.findByUsername("john_doe")).thenReturn(Mono.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Mono.empty());

        // Second user has existing email
        when(userRepository.findByUsername("duplicate_user")).thenReturn(Mono.empty());
        Users existingUser = new Users();
        existingUser.setEmail("existing@example.com");
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Mono.just(existingUser));

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
            Users u = invocation.getArgument(0);
            u.setId(101);
            return Mono.just(u);
        });
        when(roleRepository.save(any(Roles.class))).thenReturn(Mono.just(new Roles()));
        when(bulkJobErrorRepository.save(any(BulkJobErrorEntity.class))).thenReturn(Mono.just(new BulkJobErrorEntity()));

        StepVerifier.create(bulkJobProcessor.processJob("job-123", request))
                .assertNext(job -> {
                    assertEquals(BulkJobStatus.PARTIALLY_COMPLETED.name(), job.getStatus());
                    assertEquals(1, job.getSuccessCount());
                    assertEquals(1, job.getFailCount());
                })
                .verifyComplete();

        verify(bulkJobErrorRepository, times(1)).save(any(BulkJobErrorEntity.class));
    }

    @Test
    @DisplayName("Should abort remaining records when continueOnError is false")
    void shouldStopOnFirstErrorWhenContinueOnErrorIsFalse() {
        BulkRegisterRequest request = BulkRegisterRequest.builder()
                .options(new BulkOptions(false))
                .records(List.of(
                        new BulkRegisterItem("REF-1", "duplicate_user", "pass123", "dup@example.com"),
                        new BulkRegisterItem("REF-2", "should_not_run", "pass456", "notrun@example.com")
                ))
                .build();

        when(bulkJobRepository.findById("job-123")).thenReturn(Mono.just(sampleJob));
        when(bulkJobRepository.save(any(BulkJob.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Users existing = new Users();
        existing.setUsername("duplicate_user");
        when(userRepository.findByUsername("duplicate_user")).thenReturn(Mono.just(existing));
        when(bulkJobErrorRepository.save(any(BulkJobErrorEntity.class))).thenReturn(Mono.just(new BulkJobErrorEntity()));

        StepVerifier.create(bulkJobProcessor.processJob("job-123", request))
                .assertNext(job -> {
                    assertEquals(BulkJobStatus.FAILED.name(), job.getStatus());
                    assertEquals(0, job.getSuccessCount());
                    assertEquals(1, job.getFailCount());
                })
                .verifyComplete();

        verify(userRepository, never()).findByUsername("should_not_run");
        verify(userRepository, never()).save(any(Users.class));
    }
}
