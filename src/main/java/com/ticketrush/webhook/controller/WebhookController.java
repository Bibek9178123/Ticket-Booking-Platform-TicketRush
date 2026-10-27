package com.ticketrush.webhook.controller;

import com.ticketrush.webhook.dto.WebhookResponse;
import com.ticketrush.webhook.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Webhook Receiver for payment provider event notifications.
 *
 * Security:
 * - Publicly accessible endpoint (no JWT required)
 * - Cryptographically verified using HMAC-SHA256 signature header ("X-Signature")
 * - Enforces anti-replay timestamp validation (< 5 minutes)
 * - Guarantees idempotency via event_id tracking
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/payments")
    public ResponseEntity<WebhookResponse> handlePaymentWebhook(
            @RequestHeader(value = "X-Signature", required = false) String signatureHeader,
            @RequestBody String rawPayload) {

        WebhookResponse response = webhookService.processWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(response);
    }
}
