package com.example.springbootbasiclogin.service.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FusionAuthWebhookService {

    private final WebhookSecurityService webhookSecurityService;
    private final WebhookEventService webhookEventService;
    private final ObjectMapper objectMapper;

    /**
     * Authenticates, validates, parses, and processes an incoming FusionAuth webhook event.
     *
     * @param rawPayload       raw JSON body as a string
     * @param webhookSecret    X-Webhook-Secret header value (optional)
     * @param fusionAuthSig    X-FusionAuth-Signature header value (raw HMAC-SHA256, optional)
     * @param fusionAuthJwtSig X-FusionAuth-Signature-JWT header value (signed JWT, optional)
     * @return 200 OK on success/idempotent replay, 401 on unauthorized, 400 on bad request, 500 on server error
     */
    public Mono<ResponseEntity<Map<String, String>>> handleWebhook(
            String rawPayload,
            String webhookSecret,
            String fusionAuthSig,
            String fusionAuthJwtSig) {

        // ── 1. Rate Limiting ──────────────────────────────────────────────────
        if (!webhookSecurityService.checkRateLimit()) {
            log.warn("Webhook rate limit exceeded");
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded")));
        }

        // ── 2. Shared Secret Verification ─────────────────────────────────────
        if (!webhookSecurityService.verifySecret(webhookSecret)) {
            log.warn("Webhook secret verification failed");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook secret")));
        }

        // ── 3. Cryptographic Signature Verification (JWT or raw HMAC) ──────────
        if (!webhookSecurityService.verifySignature(rawPayload, fusionAuthSig, fusionAuthJwtSig)) {
            log.warn("Webhook signature verification failed");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature")));
        }

        // ── 4. Parse JSON payload ─────────────────────────────────────────────
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload", e);
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid JSON payload")));
        }

        JsonNode eventNode = root.path("event");
        if (eventNode.isMissingNode()) {
            log.warn("Webhook payload missing 'event' field");
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'event' field")));
        }

        String eventId = eventNode.path("id").asText(null);
        String eventType = eventNode.path("type").asText(null);
        long createInstant = eventNode.path("createInstant").asLong(0);
        JsonNode userNode = eventNode.path("user");

        if (eventId == null || eventType == null) {
            log.warn("Webhook event missing 'id' or 'type' field");
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing event 'id' or 'type'")));
        }

        // ── 5. Timestamp Replay Protection ────────────────────────────────────
        if (createInstant > 0 && !webhookSecurityService.verifyTimestamp(createInstant)) {
            log.warn("Webhook event {} rejected: timestamp too old or in the future", eventId);
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Event timestamp outside acceptable window")));
        }

        // ── 6. Idempotency Check & Event Processing ───────────────────────────
        return webhookSecurityService.isEventAlreadyProcessed(eventId)
                .flatMap(alreadyProcessed -> {
                    if (alreadyProcessed) {
                        log.info("Webhook event {} already processed (idempotent), returning 200", eventId);
                        return Mono.just(ResponseEntity.ok(
                                Map.of("status", "ok", "message", "Event already processed")));
                    }

                    return webhookEventService.processEvent(eventType, userNode, eventId)
                            .then(webhookSecurityService.recordProcessedEvent(eventId, eventType))
                            .then(Mono.fromCallable(() -> {
                                log.info("Webhook event {} ({}) processed successfully", eventId, eventType);
                                return ResponseEntity.ok(Map.of("status", "ok"));
                            }))
                            .onErrorResume(e -> {
                                log.error("Error processing webhook event {} ({})", eventId, eventType, e);
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Internal processing error")));
                            });
                });
    }
}
