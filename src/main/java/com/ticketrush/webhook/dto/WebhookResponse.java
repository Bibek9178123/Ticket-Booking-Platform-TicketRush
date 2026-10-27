package com.ticketrush.webhook.dto;

public record WebhookResponse(
        String status,
        String message
) {}
