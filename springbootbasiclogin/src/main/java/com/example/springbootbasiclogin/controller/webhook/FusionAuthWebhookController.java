package com.example.springbootbasiclogin.controller.webhook;

import com.example.springbootbasiclogin.service.webhook.FusionAuthWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/webhooks")
@Validated
@RequiredArgsConstructor
public class FusionAuthWebhookController {

    private final FusionAuthWebhookService fusionAuthWebhookService;

    /**
     * Handles incoming FusionAuth webhook events.
     *
     * @param rawPayload       the raw JSON body as a string
     * @param webhookSecret    the X-Webhook-Secret header value (optional)
     * @param fusionAuthSig    the X-FusionAuth-Signature header value (raw HMAC-SHA256, optional)
     * @param fusionAuthJwtSig the X-FusionAuth-Signature-JWT header value (signed JWT, optional)
     * @return 200 OK on success, 401 on security failure, 400 on bad request
     */
    @PostMapping("/fusionauth")
    public Mono<ResponseEntity<Map<String, String>>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(name = "X-Webhook-Secret", required = false) String webhookSecret,
            @RequestHeader(name = "X-FusionAuth-Signature", required = false) String fusionAuthSig,
            @RequestHeader(name = "X-FusionAuth-Signature-JWT", required = false) String fusionAuthJwtSig) {

        return fusionAuthWebhookService.handleWebhook(rawPayload, webhookSecret, fusionAuthSig, fusionAuthJwtSig);
    }
}
