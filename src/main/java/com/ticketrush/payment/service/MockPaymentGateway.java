package com.ticketrush.payment.service;

import com.ticketrush.booking.entity.Booking;
import com.ticketrush.payment.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public PaymentInitiationResult initiatePayment(Booking booking) {
        String providerRef = "mock_pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String paymentUrl = "/mock-pay.html?ref=" + providerRef +
                "&amount=" + booking.getTotalAmount() +
                "&bookingId=" + booking.getId() +
                "&eventId=" + booking.getEvent().getId();

        log.info("MockPaymentGateway: Initiated payment for booking {}. Provider Ref: {}", booking.getId(), providerRef);
        return new PaymentInitiationResult(providerRef, paymentUrl, booking.getTotalAmount());
    }

    @Override
    public RefundResult processRefund(Payment payment) {
        String refundRef = "mock_re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("MockPaymentGateway: Processed refund of ${} for payment {}. Refund Ref: {}",
                payment.getAmount(), payment.getProviderRef(), refundRef);

        return new RefundResult(true, refundRef, "Mock refund successfully processed");
    }
}
