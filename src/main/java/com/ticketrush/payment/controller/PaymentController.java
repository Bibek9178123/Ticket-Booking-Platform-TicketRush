package com.ticketrush.payment.controller;

import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.payment.dto.InitiatePaymentRequest;
import com.ticketrush.payment.dto.InitiatePaymentResponse;
import com.ticketrush.payment.dto.MockPaymentSimulateRequest;
import com.ticketrush.payment.dto.PaymentStatusResponse;
import com.ticketrush.payment.service.PaymentService;
import com.ticketrush.webhook.dto.WebhookResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(paymentService.initiatePayment(request, principal));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(paymentService.getPaymentStatus(bookingId, principal));
    }

    /**
     * Simulation endpoint for the mock payment provider frontend (/mock-pay.html).
     */
    @PostMapping("/mock-simulate")
    public ResponseEntity<WebhookResponse> simulatePayment(
            @Valid @RequestBody MockPaymentSimulateRequest request) {

        return ResponseEntity.ok(paymentService.simulateMockPayment(request));
    }
}
