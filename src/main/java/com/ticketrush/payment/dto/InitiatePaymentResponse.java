package com.ticketrush.payment.dto;

import com.ticketrush.payment.entity.PaymentStatus;

import java.math.BigDecimal;

public record InitiatePaymentResponse(
        Long paymentId,
        Long bookingId,
        String providerRef,
        String paymentUrl,
        BigDecimal amount,
        PaymentStatus status
) {}
