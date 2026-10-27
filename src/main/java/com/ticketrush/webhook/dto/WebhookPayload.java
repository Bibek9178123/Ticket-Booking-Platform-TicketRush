package com.ticketrush.webhook.dto;

import java.math.BigDecimal;

public record WebhookPayload(
        String eventId,
        String type,
        String providerRef,
        BigDecimal amount,
        Long bookingId
) {}
