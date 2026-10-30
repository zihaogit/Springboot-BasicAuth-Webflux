# Bulk Load vs Single Load Architecture

> **Status:** Implemented  
> **Date:** 2026-10-25  
> **Author:** Development Team

---

## Overview

This document outlines the architecture decision for handling **single-record** and **bulk-record** operations in the application. Each path uses a different async strategy optimized for its workload.

---

## Architecture Summary

```
┌──────────────────────────────────────────────────────┐
│                     API Layer                        │
├─────────────────────────┬────────────────────────────┤
│   Single Load           │   Bulk Load                │
│   POST /api/{resource}  │   POST /api/{resource}/bulk│
│                         │                            │
│   ┌─────────────────┐   │   ┌──────────────────────┐ │
│   │ WebFlux Reactive│   │   │ Async (UUID Pattern) │ │
│   │ Mono<Response>  │   │   │ Mono<JobAccepted>    │ │
│   └────────┬────────┘   │   └──────────┬───────────┘ │
│            │            │              │             │
│   Validate & Process    │   Fast Validate & Enqueue  │
│            │            │              │             │
│   Return result/error   │   Return UUID immediately  │
│                         │              │             │
│                         │   ┌──────────▼───────────┐ │
│                         │   │  Background Worker   │ │
│                         │   │  Processes records   │ │
│                         │   └──────────┬───────────┘ │
│                         │              │             │
│                         │   GET /api/jobs/{id}       │
│                         │   → Job status & results   │
└─────────────────────────┴────────────────────────────┘
```

---

## Single Load — WebFlux Reactive

### Pattern

Standard request-response using WebFlux reactive types (`Mono` / `Flux`).

### Flow

1. Client sends a `POST` request with a single record
2. Server validates the payload
3. Server processes the record (DB writes, external calls)
4. Server returns the result or error directly in the HTTP response

### Example

```java
@PostMapping("/api/users")
public Mono<ResponseEntity<UserResponse>> createUser(@RequestBody @Valid UserRequest request) {
    return userService.create(request)
        .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(user))
        .onErrorResume(ValidationException.class, e ->
            Mono.just(ResponseEntity.badRequest().body(errorResponse(e))));
}
```

### When to Use

- Operations that complete in **milliseconds to a few seconds**
- Client needs **immediate feedback** (success, validation error, conflict)
- User-facing operations (registration, login, CRUD)

### Error Handling

- Validation errors → `400 Bad Request`
- Conflicts → `409 Conflict`
- Not found → `404 Not Found`
- Server errors → `500 Internal Server Error`

All errors return a structured error response body.

---

## Bulk Load — Async Architecture (UUID Pattern)

### Pattern

Fire-and-forget with a job tracking ID. Client submits a batch, receives a UUID immediately, and polls for results.

### Flow

1. Client (**must have `ROLE_ADMIN`**) sends a `POST` request with a list of records
2. Security layer validates the user's role (returns `403 Forbidden` if not `ADMIN`)
3. Server performs **fast pre-validation** (schema, format, size limits)
4. Server generates a **Job ID (UUID)**, binds the authenticated admin's ID/username to the job, and enqueues the work
5. Server returns the Job ID immediately with `202 Accepted`
6. Background worker processes records asynchronously
7. Admin polls `GET /api/jobs/{id}` for status and results

### Example

```java
@PostMapping("/api/users/bulk")
@PreAuthorize("hasRole('ADMIN')")
public Mono<ResponseEntity<BulkJobAccepted>> bulkCreateUsers(
        @RequestBody @Valid BulkUserRequest request,
        Authentication authentication) {
    String adminUsername = authentication.getName();
    return bulkJobService.submit(request, adminUsername)
        .map(jobId -> ResponseEntity
            .accepted()
            .body(new BulkJobAccepted(jobId, "ACCEPTED")));
}

@GetMapping("/api/jobs/{jobId}")
@PreAuthorize("hasRole('ADMIN')")
public Mono<ResponseEntity<JobStatus>> getJobStatus(@PathVariable String jobId) {
    return bulkJobService.getStatus(jobId)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

### Bulk Request Payload (The Envelope Pattern)

Bulk load endpoints use the **Envelope Pattern** (`options` + `records` array). This supports batch-level configuration, per-record correlation keys (`clientRefId`), and standard bean validation.

#### JSON Request Example

```json
{
  "options": {
    "continueOnError": true
  },
  "records": [
    {
      "clientRefId": "CSV-ROW-101",
      "username": "john_doe",
      "email": "john@example.com",
      "password": "SecurePassword123!"
    },
    {
      "clientRefId": "CSV-ROW-102",
      "username": "jane_doe",
      "email": "jane@example.com",
      "password": "SecurePassword456!"
    }
  ]
}
```

#### Java DTOs

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRegisterRequest {

    private BulkOptions options = new BulkOptions();

    @NotNull(message = "Records list cannot be null")
    @NotEmpty(message = "Records list cannot be empty")
    @Size(max = 500, message = "Maximum batch size is 500 records")
    @Valid
    private List<BulkRegisterItem> records;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRegisterItem {

    @Schema(description = "Optional client reference or row number for tracing", example = "CSV-ROW-101")
    private String clientRefId;

    @NotBlank
    @Size(max = 255)
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkOptions {
    private boolean continueOnError = true;
}
```

### Job Status Response

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "submittedBy": "admin_user",
  "submittedAt": "2026-10-25T22:00:00+08:00",
  "completedAt": "2026-10-25T22:00:12+08:00",
  "totalRecords": 50,
  "successCount": 48,
  "failCount": 2,
  "errors": [
    {
      "index": 12,
      "clientRefId": "CSV-ROW-112",
      "field": "email",
      "message": "Email already exists"
    },
    {
      "index": 37,
      "clientRefId": "CSV-ROW-137",
      "field": "username",
      "message": "Username must be between 3 and 50 characters"
    }
  ]
}
```

### Job Status Lifecycle

```
ACCEPTED → PROCESSING → COMPLETED
                      → PARTIALLY_COMPLETED (some records failed)
                      → FAILED (entire job failed)
```

### When to Use

- Batch operations with **many records** (10+)
- Operations that could take **seconds to minutes**
- When partial failure is acceptable and shouldn't block the whole request

---

## Shared Validation Layer

Both paths share the same validation logic to ensure consistency:

```
BulkController  ──►  ValidationService  ◄──  SingleController
                           │
                 (same rules, reused logic)
```

| Validation Phase | Single Load | Bulk Load |
|---|---|---|
| **Schema / Format** | Synchronous, return error | Synchronous (pre-validation), return error |
| **Business Rules** | Synchronous, return error | Async during processing, tracked in job result |

---

## Implementation Phases

### Phase 1: In-Memory Queue (Current)

Start with a simple in-memory implementation to establish the API contract and job tracking:

```java
public interface BulkJobProducer {
    Mono<String> submit(BulkRequest request);  // returns jobId
}

public interface BulkJobStatusRepository {
    Mono<Void> save(JobStatus status);
    Mono<JobStatus> findById(String jobId);
}
```

**Implementation:**
- `InMemoryBulkJobProducer` — uses a bounded queue or `Sinks.Many` in WebFlux
- Job status stored in the database (or Redis if available)
- Background worker processes jobs using `Flux.interval` or `@Scheduled`

**Limitations:**
- Jobs are lost on application restart
- Cannot scale workers independently
- Limited backpressure control

---

### Phase 2: Kafka Migration (Future)

> **🎯 Goal:** Replace the in-memory queue with Apache Kafka for durability, scalability, and decoupled processing.

#### Why Kafka?

| Concern | In-Memory | Kafka |
|---|---|---|
| **Durability** | ❌ Lost on restart | ✅ Persisted to disk |
| **Scalability** | ❌ Single instance | ✅ Partition-based parallelism |
| **Backpressure** | Limited | ✅ Built-in consumer lag |
| **Retry / DLQ** | Manual | ✅ Native support |
| **Multi-service** | ❌ Same process | ✅ Cross-service messaging |

#### Kafka Architecture & DLQ Retry Pattern

```
                        ┌────────────────────────┐
                        │   Main Kafka Topic     │
                        │      bulk-jobs         │
                        └───────────┬────────────┘
                                    │
                                    ▼
                         ┌───────────────────────┐
                         │    Consumer Worker    │
                         └──────────┬────────────┘
                                    │
               ┌────────────────────┴────────────────────┐
               ▼                                         ▼
      [Permanent Failure]                       [Transient Failure]
   (e.g., duplicate email,                    (e.g., DB lock timeout,
      validation error)                         downstream API 503)
               │                                         │
               ▼                                         ▼
   ┌───────────────────────┐                 ┌───────────────────────┐
   │ Record in JobStatus   │                 │     DLQ / Retry       │
   │  "FAILED: bad data"   │                 │  bulk-jobs-dlq-retry  │
   │   (Do NOT retry)      │                 └───────────┬───────────┘
   └───────────────────────┘                             │
                                                         ▼
                                             ┌───────────────────────┐
                                             │    Cron Job / Admin   │
                                             │  Replays every 15 min │
                                             └───────────┬───────────┘
                                                         │
                                    (Re-publish) ────────┘
```

#### Transient vs Permanent Error Handling

| Error Category | Examples | Retry Strategy | Action |
|---|---|:---:|---|
| **Permanent / Business** | Duplicate email, constraint violation, malformed data | ❌ **No Retry** | Record failure immediately in `JobStatus.errors[]`. Retrying will never succeed. |
| **Transient / Infrastructure** | DB connection timeout, deadlock/lock wait, downstream API 503 | ✅ **Scheduled / Backoff** | Route to DLQ / retry topic. Replay after delay or via cron. |

#### Non-Blocking Retries with Spring Kafka (`@RetryableTopic`)

Spring Kafka natively handles exponential backoff and dead-letter routing without blocking main topic partitions:

```java
@RetryableTopic(
    attempts = "4",
    backoff = @Backoff(delay = 60000, multiplier = 2.0), // 1 min, 2 min, 4 min
    include = { CannotAcquireLockException.class, TimeoutException.class },
    exclude = { ValidationException.class, DuplicateKeyException.class },
    dltTopicSuffix = "-dead-letter"
)
@KafkaListener(topics = "bulk-jobs")
public void processBulkJobItem(BulkRegisterItem item) {
    userRegistrationService.processItem(item);
}
```

#### Migration Steps

1. Add `spring-kafka` or `reactor-kafka` dependency
2. Create `KafkaBulkJobProducer` implementing `BulkJobProducer`
3. Create `BulkJobConsumer` with `@KafkaListener` or reactive consumer
4. Configure topics, partitions, and consumer groups
5. Set up Dead Letter Queue (DLQ) for failed records
6. Swap the bean via Spring profile (`@Profile("kafka")`)

#### Swap via Spring Profile

```java
@Component
@Profile("!kafka")
public class InMemoryBulkJobProducer implements BulkJobProducer { ... }

@Component
@Profile("kafka")
public class KafkaBulkJobProducer implements BulkJobProducer { ... }
```

```yaml
# application.yml
spring:
  profiles:
    active: kafka  # switch to enable Kafka
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: bulk-job-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

> **Key principle:** The controller and job status endpoint remain unchanged. Only the producer/consumer implementation swaps.

#### Local Development Environment (Docker Compose + Kafka-UI)

To test Kafka and view messages completely for free without cloud quotas, a local cluster with a web console is integrated into the root `docker-compose.yml` under the `kafka` profile.

1. **Start Kafka and the Web Console:**
   ```powershell
   # Start only Kafka & Kafka-UI alongside existing services
   docker compose --profile kafka up -d kafka kafka-ui

   # Or start the entire stack including Kafka
   docker compose --profile kafka up -d
   ```

2. **Access Points:**
   - **Kafka Broker:** `localhost:9092`
   - **Kafka-UI Web Console:** [http://localhost:8085](http://localhost:8085) *(Mapped to port 8085 to avoid conflicting with Spring Boot on port 8080)*

3. **Web Console Features:**
   - View real-time messages in the `bulk-jobs` topic and `bulk-jobs-dlq`.
   - Monitor consumer lag across partitions.
   - Inspect payloads, headers, and error stack traces.
   - Manually produce test messages to verify consumers.

4. **Postman Smoke Testing Collection:**
   - Pre-configured requests and test scripts are available in [`postman/Springboot-BasicAuth-Webflux.postman_collection.json`](file:///C:/Users/Laggerbomb/OneDrive/Documents/Default%20Project/Springboot-BasicAuth-Webflux/postman/Springboot-BasicAuth-Webflux.postman_collection.json) under folder **Bulk Load (Kafka)**.
   - Includes single load, bulk load with idempotency key, validation error handling (`continueOnError: true`), status polling, and unauthorized role checks.
   - Test scripts automatically capture the `jobId` and store it in collection variables for subsequent status queries.

---

## API Endpoint Summary

| Endpoint | Method | Pattern | Authorization | Response |
|---|---|---|---|---|
| `/api/{resource}` | POST | Single / Reactive | Public / Authenticated User | `201` with result or error |
| `/api/{resource}/bulk` | POST | Bulk / Async UUID | **Admin Only** (`ROLE_ADMIN`) | `202` with `{ jobId, status }` |
| `/api/jobs/{jobId}` | GET | Status polling | **Admin Only** (`ROLE_ADMIN`) | `200` with job status & results |

---

## Design Considerations

### Security & Access Control (Admin-Only)

Bulk operations consume significant database and server resources, and impact downstream systems. Therefore, bulk loading is restricted strictly to administrators:

1. **Endpoint Protection**: Secured via Spring Security method security:
   ```java
   @PreAuthorize("hasRole('ADMIN')")
   ```
   Or configured at the reactive `SecurityWebFilterChain` level:
   ```java
   .pathMatchers(HttpMethod.POST, "/api/*/bulk").hasRole("ADMIN")
   .pathMatchers(HttpMethod.GET, "/api/jobs/**").hasRole("ADMIN")
   ```
2. **Audit Logging & Attribution**: Every bulk request captures the authenticated admin's identity (`Authentication.getName()`) and persists it with the `JobStatus`. This provides full traceability into who initiated the batch run.
3. **Unauthorized Handling**: Any non-admin request is rejected immediately at the gateway/filter with `403 Forbidden` before any parsing or queueing occurs.

### Idempotency

Bulk endpoints should support an **idempotency key** (sent as a header) to prevent duplicate submissions:

```
POST /api/users/bulk
X-Idempotency-Key: client-generated-uuid
```

### Size Limits

Enforce a maximum batch size to prevent abuse:

```java
@Size(min = 1, max = 500, message = "Batch size must be between 1 and 500")
private List<BulkRegisterItem> records;
```

### Role of Cron Jobs in Bulk Processing

| Aspect | Event-Driven / Kafka (Primary) | Cron Job (`@Scheduled`) |
|---|---|---|
| **Trigger** | Instant upon request submission | Clock-based (e.g., every 5 mins or 2:00 AM) |
| **User Experience** | Real-time status progression (`PROCESSING`) | Delayed until next cron tick |
| **Multi-Pod Safety** | Partition & consumer group coordination | Requires distributed locking (e.g., ShedLock) |
| **Resource Usage** | Push-based on demand | Constant DB polling (`WHERE status = 'PENDING'`) |

#### Verdict:
- **Do NOT use Cron as the primary processor** for user-initiated bulk loads to avoid latency and multi-pod race conditions.
- **DO use Cron for these two critical operational patterns**:
  1. **The Sweeper / Reaper Pattern**: Runs every 15 minutes to find orphaned jobs stuck in `PROCESSING` for > 30 minutes (e.g., due to pod crashes/restarts) and marks them `FAILED` or resets them.
  2. **DLQ Redrive / Replay**: Runs every 15–30 minutes to re-evaluate transient failures held in the retry queue after temporary downstream outages resolve.
  3. **Off-Peak Nightly Runs**: For massive data imports (e.g., 100k+ records) uploaded during business hours and queued for execution at 2:00 AM.

### Monitoring

Track the following metrics for bulk jobs:

- Jobs submitted per minute
- Average processing time per job
- Success/failure ratio
- Consumer lag (when using Kafka)

---

## References

- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Reactor Kafka](https://projectreactor.io/docs/kafka/release/reference/)
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/)
