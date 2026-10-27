package com.ticketrush.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload from the mock payment provider page (/mock-pay.html)
 * to simulate an asynchronous webhook event dispatch.
 */
public record MockPaymentSimulateRequest(
        @NotBlank(message = "providerRef is required")
        String providerRef,

        @NotNull(message = "bookingId is required")
        Long bookingId,

        @NotBlank(message = "eventType is required (payment.succeeded or payment.failed)")
        String eventType
) {}
