package com.ticketrush.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.common.exception.BusinessRuleException;
import com.ticketrush.common.exception.ResourceNotFoundException;
import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.common.util.SignatureUtils;
import com.ticketrush.payment.dto.InitiatePaymentRequest;
import com.ticketrush.payment.dto.InitiatePaymentResponse;
import com.ticketrush.payment.dto.MockPaymentSimulateRequest;
import com.ticketrush.payment.dto.PaymentStatusResponse;
import com.ticketrush.payment.entity.Payment;
import com.ticketrush.payment.entity.PaymentStatus;
import com.ticketrush.payment.repository.PaymentRepository;
import com.ticketrush.webhook.dto.WebhookPayload;
import com.ticketrush.webhook.dto.WebhookResponse;
import com.ticketrush.webhook.service.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGateway paymentGateway;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public PaymentService(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            PaymentGateway paymentGateway,
            WebhookService webhookService,
            ObjectMapper objectMapper,
            @Value("${app.webhook.secret}") String webhookSecret) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.paymentGateway = paymentGateway;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public InitiatePaymentResponse initiatePayment(InitiatePaymentRequest request, UserPrincipal principal) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.bookingId()));

        if (!booking.getUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("You do not have permission to pay for this booking.");
        }

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Cannot initiate payment for booking in status: " + booking.getStatus());
        }

        if (booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            throw new BusinessRuleException("The 5-minute hold on your seats has expired. Please select seats again.");
        }

        // Return existing payment if already initiated
        return paymentRepository.findByBookingId(booking.getId())
                .filter(p -> p.getStatus() == PaymentStatus.INITIATED)
                .map(existing -> {
                    String url = "/mock-pay.html?ref=" + existing.getProviderRef() +
                            "&amount=" + existing.getAmount() +
                            "&bookingId=" + booking.getId() +
                            "&eventId=" + booking.getEvent().getId();
                    return new InitiatePaymentResponse(
                            existing.getId(),
                            booking.getId(),
                            existing.getProviderRef(),
                            url,
                            existing.getAmount(),
                            existing.getStatus()
                    );
                })
                .orElseGet(() -> {
                    PaymentGateway.PaymentInitiationResult result = paymentGateway.initiatePayment(booking);
                    Payment payment = new Payment(booking, "MOCK", result.providerRef(), result.amount());
                    Payment saved = paymentRepository.save(payment);

                    return new InitiatePaymentResponse(
                            saved.getId(),
                            booking.getId(),
                            saved.getProviderRef(),
                            result.paymentUrl(),
                            saved.getAmount(),
                            saved.getStatus()
                    );
                });
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Long bookingId, UserPrincipal principal) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Access denied to payment record.");
        }

        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for booking: " + bookingId));

        return new PaymentStatusResponse(
                payment.getId(),
                booking.getId(),
                payment.getProviderRef(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }

    /**
     * Simulates what a real payment provider (e.g. Stripe) does:
     * Constructs a signed JSON payload with an HMAC-SHA256 signature
     * and dispatches it through the webhook engine.
     */
    public WebhookResponse simulateMockPayment(MockPaymentSimulateRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.bookingId()));

        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = Instant.now().getEpochSecond();

        WebhookPayload payload = new WebhookPayload(
                eventId,
                request.eventType(),
                request.providerRef(),
                booking.getTotalAmount(),
                booking.getId()
        );

        try {
            String rawPayload = objectMapper.writeValueAsString(payload);
            String signedContent = timestamp + "." + rawPayload;
            String hexSignature = SignatureUtils.computeHmacHex(webhookSecret, signedContent);
            String signatureHeader = "t=" + timestamp + ",v1=" + hexSignature;

            return webhookService.processWebhook(rawPayload, signatureHeader);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize webhook simulation payload", e);
        }
    }
}
