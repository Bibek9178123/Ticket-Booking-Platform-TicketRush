package com.ticketrush.payment.dto;

import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull(message = "Booking ID is required")
        Long bookingId
) {}
