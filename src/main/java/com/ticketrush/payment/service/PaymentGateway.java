package com.ticketrush.payment.service;

import com.ticketrush.booking.entity.Booking;
import com.ticketrush.payment.entity.Payment;

import java.math.BigDecimal;

public interface PaymentGateway {

    record PaymentInitiationResult(
            String providerRef,
            String paymentUrl,
            BigDecimal amount
    ) {}

    record RefundResult(
            boolean success,
            String refundRef,
            String message
    ) {}

    PaymentInitiationResult initiatePayment(Booking booking);

    RefundResult processRefund(Payment payment);
}
