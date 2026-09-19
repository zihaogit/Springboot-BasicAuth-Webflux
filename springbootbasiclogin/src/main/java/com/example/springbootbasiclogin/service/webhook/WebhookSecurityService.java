package com.example.springbootbasiclogin.service.webhook;

import com.example.springbootbasiclogin.config.ApplicationPropertiesConfig;
import com.example.springbootbasiclogin.entity.ProcessedWebhookEvent;
import com.example.springbootbasiclogin.repo.ProcessedWebhookEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@Slf4j
public class WebhookSecurityService {

    private final ApplicationPropertiesConfig applicationProperties;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final ZoneId zoneId;

    // In-memory sliding-window rate limiter: stores timestamps of received requests
    private final ConcurrentLinkedDeque<Instant> requestTimestamps = new ConcurrentLinkedDeque<>();

    public WebhookSecurityService(ApplicationPropertiesConfig applicationProperties,
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            ZoneId zoneId) {
        this.applicationProperties = applicationProperties;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.zoneId = zoneId;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Shared Secret Verification
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that the X-Webhook-Secret header matches the configured secret.
     * If no secret is configured (empty string), this check is skipped.
     *
     * @param headerSecret the value from the X-Webhook-Secret header
     * @return true if valid or not configured; false if mismatch
     */
    public boolean verifySecret(String headerSecret) {
        String configuredSecret = applicationProperties.getAuth().getWebhook().getSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            // No secret configured — skip this check
            return true;
        }
        if (headerSecret == null || headerSecret.isBlank()) {
            log.warn("Webhook secret header missing but secret is configured");
            return false;
        }
        boolean match = MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                headerSecret.getBytes(StandardCharsets.UTF_8));
        if (!match) {
            log.warn("Webhook secret header does not match configured secret");
        }
        return match;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. HMAC-SHA256 Signature Verification
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the FusionAuth signature, supporting either the native
     * X-FusionAuth-Signature-JWT header (JWT containing request_body_sha256)
     * or the legacy X-FusionAuth-Signature header (raw HMAC-SHA256).
     *
     * @param rawPayload         the raw JSON body as a string
     * @param signatureHeader    the Base64-encoded signature from
     *                           X-FusionAuth-Signature (optional)
     * @param signatureJwtHeader the signed JWT from X-FusionAuth-Signature-JWT
     *                           (optional)
     * @return true if the signature is valid, or if no signature key is configured
     */
    public boolean verifySignature(String rawPayload, String signatureHeader, String signatureJwtHeader) {
        String signingKey = applicationProperties.getAuth().getWebhook().getSignatureKey();
        if (signingKey == null || signingKey.isBlank()) {
            log.debug("No webhook signature key configured — skipping signature verification");
            return true;
        }

        // 1. Prefer native FusionAuth JWT signature if present
        if (signatureJwtHeader != null && !signatureJwtHeader.isBlank()) {
            return verifyJwtSignature(rawPayload, signatureJwtHeader);
        }

        // 2. Fall back to raw HMAC-SHA256 signature
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            return verifyRawHmac(rawPayload, signatureHeader);
        }

        log.warn(
                "Signature key is configured, but neither X-FusionAuth-Signature-JWT nor X-FusionAuth-Signature header was provided");
        return false;
    }

    public boolean verifySignature(String rawPayload, String signatureHeader) {
        return verifySignature(rawPayload, signatureHeader, null);
    }

    /**
     * Verifies native FusionAuth X-FusionAuth-Signature-JWT header:
     * 1. Computes SHA-256 base64 digest of raw payload.
     * 2. Verifies the JWT using the configured signature key.
     * 3. Compares request_body_sha256 claim with computed digest.
     */
    public boolean verifyJwtSignature(String rawPayload, String signatureJwt) {
        String signingKey = applicationProperties.getAuth().getWebhook().getSignatureKey();
        try {
            // Compute base64 SHA-256 digest of raw payload
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            String computedBase64 = Base64.getEncoder().encodeToString(digest);

            // Verify JWT
            byte[] keyBytes = signingKey.getBytes(StandardCharsets.UTF_8);
            SecretKey key = Keys.hmacShaKeyFor(keyBytes.length < 32 ? Arrays.copyOf(keyBytes, 32) : keyBytes);

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(signatureJwt)
                    .getPayload();

            String claimHash = claims.get("request_body_sha256", String.class);
            if (claimHash == null) {
                log.warn("X-FusionAuth-Signature-JWT missing 'request_body_sha256' claim");
                return false;
            }

            boolean match = MessageDigest.isEqual(
                    computedBase64.getBytes(StandardCharsets.UTF_8),
                    claimHash.getBytes(StandardCharsets.UTF_8));
            if (!match) {
                log.warn("request_body_sha256 claim does not match computed payload hash");
            }
            return match;
        } catch (Exception e) {
            log.error("Failed to verify X-FusionAuth-Signature-JWT: {}", e.getMessage());
            return false;
        }
    }

    private boolean verifyRawHmac(String rawPayload, String signatureHeader) {
        String signingKey = applicationProperties.getAuth().getWebhook().getSignatureKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computedHmac = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            byte[] providedHmac = Base64.getDecoder().decode(signatureHeader);

            boolean match = MessageDigest.isEqual(computedHmac, providedHmac);
            if (!match) {
                log.warn("HMAC-SHA256 signature verification failed");
            }
            return match;
        } catch (Exception e) {
            log.error("Error computing HMAC-SHA256 signature", e);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Timestamp Replay Protection
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that the event timestamp (createInstant from FusionAuth, in
     * milliseconds since epoch)
     * is within the acceptable window. Rejects stale events to prevent replay
     * attacks.
     *
     * @param createInstantMillis the event createInstant in epoch milliseconds
     * @return true if the event is within the allowed time window
     */
    public boolean verifyTimestamp(long createInstantMillis) {
        long maxAgeSeconds = applicationProperties.getAuth().getWebhook().getMaxEventAgeSeconds();
        long nowMillis = Instant.now().toEpochMilli();
        long ageMillis = nowMillis - createInstantMillis;

        if (ageMillis < 0) {
            // Future timestamp — could be clock skew. Allow up to 60 seconds in the future.
            if (Math.abs(ageMillis) > 60_000) {
                log.warn("Webhook event timestamp is too far in the future: {}ms", ageMillis);
                return false;
            }
            return true;
        }

        boolean valid = ageMillis <= (maxAgeSeconds * 1000);
        if (!valid) {
            log.warn("Webhook event is too old: {}ms (max allowed: {}ms)", ageMillis, maxAgeSeconds * 1000);
        }
        return valid;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Rate Limiting (In-Memory Sliding Window)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Checks if the incoming webhook request exceeds the configured rate limit.
     * Uses an in-memory sliding window of request timestamps.
     *
     * @return true if the request is within the rate limit; false if rate-limited
     */
    public synchronized boolean checkRateLimit() {
        ApplicationPropertiesConfig.RateLimitConfig rateLimitConfig = applicationProperties.getAuth().getWebhook().getRateLimit();
        int maxAttempts = rateLimitConfig.getMaxAttempts();
        int windowSeconds = rateLimitConfig.getWindowSeconds();

        Instant windowStart = Instant.now().minusSeconds(windowSeconds);

        // Purge expired entries
        while (!requestTimestamps.isEmpty()) {
            Instant earliest = requestTimestamps.peekFirst();
            if (earliest != null && earliest.isBefore(windowStart)) {
                requestTimestamps.pollFirst();
            } else {
                break;
            }
        }

        if (requestTimestamps.size() >= maxAttempts) {
            log.warn("Webhook rate limit exceeded: {} requests in {}s window (max: {})",
                    requestTimestamps.size(), windowSeconds, maxAttempts);
            return false;
        }

        requestTimestamps.addLast(Instant.now());
        return true;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Idempotency Check
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Checks if the event has already been processed by looking up the event ID
     * in the {@code processed_webhook_events} table.
     *
     * @param eventId the unique event identifier from FusionAuth
     * @return a Mono emitting true if the event was already processed (duplicate)
     */
    public Mono<Boolean> isEventAlreadyProcessed(String eventId) {
        return processedWebhookEventRepository.existsByEventId(eventId);
    }

    /**
     * Records a processed event in the database for idempotency tracking.
     *
     * @param eventId   the unique event identifier
     * @param eventType the event type (e.g., "user.delete", "user.update")
     * @return a Mono that completes when the event is persisted
     */
    public Mono<ProcessedWebhookEvent> recordProcessedEvent(String eventId, String eventType) {
        ProcessedWebhookEvent event = ProcessedWebhookEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .processedAt(ZonedDateTime.now(zoneId))
                .isNew(true)
                .build();
        return processedWebhookEventRepository.save(event);
    }
}
