package com.example.springbootbasiclogin.controller.bulk;

import com.example.springbootbasiclogin.dto.auth.RegisterRequest;
import com.example.springbootbasiclogin.dto.bulk.BulkOptions;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterItem;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import com.example.springbootbasiclogin.dto.bulk.JobStatusResponse;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.service.auth.RegistrationService;
import com.example.springbootbasiclogin.service.bulk.BulkJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkJobControllerTest {

    @Mock
    private BulkJobService bulkJobService;

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private BulkJobController bulkJobController;

    @Test
    @DisplayName("Should return 202 Accepted when bulk user creation is submitted")
    void shouldReturn202OnBulkCreateUsers() {
        BulkRegisterRequest request = BulkRegisterRequest.builder()
                .options(new BulkOptions(true))
                .records(List.of(
                        new BulkRegisterItem("REF-1", "admin_bulk_1", "pass123", "b1@example.com")
                ))
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin_user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(bulkJobService.submit(any(BulkRegisterRequest.class), eq("admin_user"), eq("client-idem-key")))
                .thenReturn(Mono.just("uuid-1234"));

        StepVerifier.create(bulkJobController.bulkCreateUsers(request, "client-idem-key", auth))
                .assertNext(response -> {
                    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals("uuid-1234", response.getBody().jobId());
                    assertEquals("ACCEPTED", response.getBody().status());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 200 OK when job status exists")
    void shouldReturnJobStatus() {
        JobStatusResponse status = JobStatusResponse.builder()
                .jobId("uuid-1234")
                .status("COMPLETED")
                .submittedBy("admin_user")
                .submittedAt(ZonedDateTime.now())
                .completedAt(ZonedDateTime.now())
                .totalRecords(1)
                .successCount(1)
                .failCount(0)
                .errors(List.of())
                .build();

        when(bulkJobService.getStatus("uuid-1234")).thenReturn(Mono.just(status));

        StepVerifier.create(bulkJobController.getJobStatus("uuid-1234"))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals("uuid-1234", response.getBody().getJobId());
                    assertEquals("COMPLETED", response.getBody().getStatus());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 404 Not Found when job status does not exist")
    void shouldReturn404WhenJobNotFound() {
        when(bulkJobService.getStatus("unknown-id")).thenReturn(Mono.empty());

        StepVerifier.create(bulkJobController.getJobStatus("unknown-id"))
                .assertNext(response -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 201 Created for single user registration")
    void shouldCreateSingleUser() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("single_user");
        registerRequest.setEmail("single@example.com");
        registerRequest.setPassword("password123");

        Users createdUser = new Users();
        createdUser.setId(42);
        createdUser.setUsername("single_user");
        createdUser.setEmail("single@example.com");

        when(registrationService.registerUser(registerRequest)).thenReturn(Mono.just(createdUser));

        StepVerifier.create(bulkJobController.createUser(registerRequest))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals(42, response.getBody().getId());
                    assertEquals("single_user", response.getBody().getUsername());
                })
                .verifyComplete();
    }
}
