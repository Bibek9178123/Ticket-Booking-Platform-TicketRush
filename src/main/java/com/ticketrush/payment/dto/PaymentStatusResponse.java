package com.ticketrush.payment.dto;

import com.ticketrush.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentStatusResponse(
        Long paymentId,
        Long bookingId,
        String providerRef,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime createdAt
) {}
