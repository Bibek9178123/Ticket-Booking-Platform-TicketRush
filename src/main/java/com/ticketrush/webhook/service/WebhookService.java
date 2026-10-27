package com.ticketrush.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingSeat;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.booking.repository.BookingSeatRepository;
import com.ticketrush.common.exception.InvalidSignatureException;
import com.ticketrush.common.util.SignatureUtils;
import com.ticketrush.payment.entity.Payment;
import com.ticketrush.payment.entity.PaymentStatus;
import com.ticketrush.payment.repository.PaymentRepository;
import com.ticketrush.payment.service.PaymentGateway;
import com.ticketrush.seat.repository.EventSeatRepository;
import com.ticketrush.webhook.dto.WebhookPayload;
import com.ticketrush.webhook.dto.WebhookResponse;
import com.ticketrush.webhook.entity.WebhookEvent;
import com.ticketrush.webhook.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final String webhookSecret;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final EventSeatRepository seatRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    public WebhookService(
            @Value("${app.webhook.secret}") String webhookSecret,
            WebhookEventRepository webhookEventRepository,
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            EventSeatRepository seatRepository,
            PaymentGateway paymentGateway,
            ObjectMapper objectMapper) {
        this.webhookSecret = webhookSecret;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
        this.paymentGateway = paymentGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes inbound provider webhooks:
     * 1. Validates HMAC-SHA256 signature and timestamp freshness (< 5 mins).
     * 2. Enforces database idempotency using webhook_events.event_id.
     * 3. Executes state machine transitions, including the late-payment rule.
     */
    @Transactional
    public WebhookResponse processWebhook(String rawPayload, String signatureHeader) {
        // 1. Signature & Freshness Verification
        boolean isValid = SignatureUtils.verifyWebhookSignature(webhookSecret, rawPayload, signatureHeader);
        if (!isValid) {
            log.warn("Webhook rejected: Invalid signature or timestamp expired (> 5 min)");
            throw new InvalidSignatureException("Webhook signature verification failed or timestamp expired.");
        }

        // Parse Payload
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook JSON payload", e);
            return new WebhookResponse("BAD_REQUEST", "Invalid JSON format");
        }

        // 2. IDEMPOTENCY CHECK:
        // If event_id has already been processed, return 200 OK immediately with no side effects
        if (webhookEventRepository.existsByEventId(payload.eventId())) {
            log.info("Idempotent webhook skipped: Event {} was already processed.", payload.eventId());
            return new WebhookResponse("SUCCESS", "Event already processed (idempotent cache)");
        }

        // Record event_id to lock idempotency
        webhookEventRepository.save(new WebhookEvent(payload.eventId(), payload.type()));

        // 3. Process Event Types
        switch (payload.type()) {
            case "payment.succeeded" -> handlePaymentSucceeded(payload);
            case "payment.failed" -> handlePaymentFailed(payload);
            case "refund.succeeded" -> handleRefundSucceeded(payload);
            default -> log.info("Ignored unknown webhook event type: {}", payload.type());
        }

        return new WebhookResponse("SUCCESS", "Webhook event processed successfully");
    }

    private void handlePaymentSucceeded(WebhookPayload payload) {
        Optional<Payment> paymentOpt = paymentRepository.findByProviderRef(payload.providerRef());
        if (paymentOpt.isEmpty() && payload.bookingId() != null) {
            paymentOpt = paymentRepository.findByBookingId(payload.bookingId());
        }

        if (paymentOpt.isEmpty()) {
            log.warn("Payment not found for providerRef: {}", payload.providerRef());
            return;
        }

        Payment payment = paymentOpt.get();
        Booking booking = payment.getBooking();
        LocalDateTime now = LocalDateTime.now();

        // Check if hold is still active or expired
        boolean holdActive = booking.getHoldExpiresAt().isAfter(now);

        if (holdActive) {
            // Happy path: Still held -> Confirm booking & permanently mark seats BOOKED
            confirmBooking(booking, payment);
        } else {
            // THE LATE PAYMENT RULE:
            // Payment arrived AFTER the 5-minute hold expired.
            // Attempt to re-claim the same seats atomically:
            log.warn("Late payment arrived for booking {}. Attempting atomic seat re-claim...", booking.getId());

            List<BookingSeat> bookingSeats = bookingSeatRepository.findAllByBookingId(booking.getId());
            List<Long> seatIds = bookingSeats.stream().map(bs -> bs.getEventSeat().getId()).sorted().toList();

            LocalDateTime newExpires = now.plusMinutes(5);
            int reclaimed = seatRepository.holdSeatsAtomic(seatIds, booking.getId(), newExpires, now);

            if (reclaimed == seatIds.size()) {
                // Re-claim succeeded: Seats were still available! Confirm the booking.
                log.info("Late payment succeeded: Seats reclaimed! Confirming booking {}.", booking.getId());
                confirmBooking(booking, payment);
            } else {
                // Re-claim failed: One or more seats were already taken by another fan!
                // Cancel booking and trigger immediate refund.
                log.warn("Late payment conflict: Seats were stolen by another customer! Triggering refund for booking {}.", booking.getId());
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);

                payment.setStatus(PaymentStatus.REFUND_PENDING);
                paymentRepository.save(payment);

                PaymentGateway.RefundResult refundResult = paymentGateway.processRefund(payment);
                if (refundResult.success()) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                }
            }
        }
    }

    private void confirmBooking(Booking booking, Payment payment) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        booking.setStatus(BookingStatus.CONFIRMED);
        String bookingCode = "TR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        booking.setBookingCode(bookingCode);
        bookingRepository.save(booking);

        seatRepository.confirmSeatsByBookingId(booking.getId());
        log.info("Booking {} confirmed with code: {}", booking.getId(), bookingCode);
    }

    private void handlePaymentFailed(WebhookPayload payload) {
        paymentRepository.findByProviderRef(payload.providerRef()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            Booking booking = payment.getBooking();
            booking.setStatus(BookingStatus.FAILED);
            bookingRepository.save(booking);

            // Release seats back to AVAILABLE
            seatRepository.releaseSeatsByBookingId(booking.getId());
            log.info("Payment failed for booking {}. Seats released back to AVAILABLE.", booking.getId());
        });
    }

    private void handleRefundSucceeded(WebhookPayload payload) {
        paymentRepository.findByProviderRef(payload.providerRef()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Refund confirmed for payment {}", payment.getProviderRef());
        });
    }
}
