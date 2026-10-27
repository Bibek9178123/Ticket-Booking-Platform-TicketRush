package com.ticketrush.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.booking.dto.HoldSeatsRequest;
import com.ticketrush.booking.dto.HoldSeatsResponse;
import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.booking.repository.BookingSeatRepository;
import com.ticketrush.booking.service.BookingService;
import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.common.util.SignatureUtils;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.entity.EventStatus;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.payment.dto.InitiatePaymentRequest;
import com.ticketrush.payment.dto.InitiatePaymentResponse;
import com.ticketrush.payment.entity.Payment;
import com.ticketrush.payment.entity.PaymentStatus;
import com.ticketrush.payment.repository.PaymentRepository;
import com.ticketrush.payment.service.PaymentService;
import com.ticketrush.seat.entity.EventSeat;
import com.ticketrush.seat.entity.SeatStatus;
import com.ticketrush.seat.repository.EventSeatRepository;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import com.ticketrush.webhook.dto.WebhookPayload;
import com.ticketrush.webhook.entity.WebhookEvent;
import com.ticketrush.webhook.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.webhook.secret}")
    private String webhookSecret;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventSeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User organizer;
    private User customer;
    private User customer2;
    private Event testEvent;
    private EventSeat testSeat;

    @BeforeEach
    void setUp() {
        // Enforce strict child-to-parent deletion order
        bookingSeatRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        webhookEventRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        organizer = userRepository.save(new User(
                "org_webhook@test.com",
                passwordEncoder.encode("Pass123!"),
                "Webhook Organizer",
                Role.ORGANIZER
        ));

        customer = userRepository.save(new User(
                "cust_webhook@test.com",
                passwordEncoder.encode("Pass123!"),
                "Webhook Customer",
                Role.CUSTOMER
        ));

        customer2 = userRepository.save(new User(
                "cust2_webhook@test.com",
                passwordEncoder.encode("Pass123!"),
                "Webhook Customer 2",
                Role.CUSTOMER
        ));

        // Create an event starting 48 hours in future
        Event event = new Event(
                organizer,
                "Webhook Stadium Live",
                "Testing HMAC & Idempotent Webhooks",
                "Concert",
                "Seattle",
                "Climate Pledge Arena",
                LocalDateTime.now().plusHours(48),
                LocalDateTime.now().plusHours(52),
                1,
                5,
                "[]"
        );
        event.setStatus(EventStatus.PUBLISHED);
        testEvent = eventRepository.save(event);

        // Create seats
        testSeat = seatRepository.save(new EventSeat(
                testEvent, "A", 1, "VIP", new BigDecimal("120.00")
        ));
    }

    private String createSignatureHeader(String rawPayload, long timestampSeconds) {
        String data = timestampSeconds + "." + rawPayload;
        String hex = SignatureUtils.computeHmacHex(webhookSecret, data);
        return "t=" + timestampSeconds + ",v1=" + hex;
    }

    @Test
    @DisplayName("Webhook: Valid HMAC signature confirms booking, sets seats BOOKED, and sets payment SUCCEEDED")
    void validSignature_ConfirmsBookingAndSeatsBooked() throws Exception {
        // 1. Hold seat
        UserPrincipal principal = UserPrincipal.create(customer);
        HoldSeatsResponse holdResponse = bookingService.holdSeats(
                new HoldSeatsRequest(testEvent.getId(), List.of(testSeat.getId())),
                principal
        );

        // 2. Initiate payment
        InitiatePaymentResponse payInit = paymentService.initiatePayment(
                new InitiatePaymentRequest(holdResponse.bookingId()),
                principal
        );

        // 3. Prepare webhook payload for payment.succeeded
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        WebhookPayload payload = new WebhookPayload(
                eventId,
                "payment.succeeded",
                payInit.providerRef(),
                new BigDecimal("120.00"),
                holdResponse.bookingId()
        );
        String rawPayload = objectMapper.writeValueAsString(payload);
        long now = Instant.now().getEpochSecond();
        String signatureHeader = createSignatureHeader(rawPayload, now);

        // 4. POST to /api/webhooks/payments
        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureHeader)
                        .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 5. Verify database invariants
        Booking booking = bookingRepository.findById(holdResponse.bookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getBookingCode()).isNotNull().startsWith("TR-");

        EventSeat seat = seatRepository.findById(testSeat.getId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getBookingId()).isEqualTo(booking.getId());

        Payment payment = paymentRepository.findByProviderRef(payInit.providerRef()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(webhookEventRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    @DisplayName("Webhook: Invalid HMAC signature returns 401 Unauthorized")
    void invalidSignature_Returns401Unauthorized() throws Exception {
        String eventId = "evt_tampered";
        WebhookPayload payload = new WebhookPayload(
                eventId,
                "payment.succeeded",
                "mock_ref_tampered",
                new BigDecimal("100.00"),
                1L
        );
        String rawPayload = objectMapper.writeValueAsString(payload);
        long now = Instant.now().getEpochSecond();
        String invalidSignatureHeader = "t=" + now + ",v1=deadbeefcafebabe1234567890abcdef";

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", invalidSignatureHeader)
                        .content(rawPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Webhook signature verification failed or timestamp expired."));
    }

    @Test
    @DisplayName("Webhook: Old timestamp (> 5 minutes) returns 401 Unauthorized (Replay Protection)")
    void oldTimestamp_Returns401Unauthorized() throws Exception {
        String eventId = "evt_replay";
        WebhookPayload payload = new WebhookPayload(
                eventId,
                "payment.succeeded",
                "mock_ref_replay",
                new BigDecimal("100.00"),
                1L
        );
        String rawPayload = objectMapper.writeValueAsString(payload);
        // 6 minutes in the past
        long oldTimestamp = Instant.now().minusSeconds(360).getEpochSecond();
        String signatureHeader = createSignatureHeader(rawPayload, oldTimestamp);

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureHeader)
                        .content(rawPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Webhook: Duplicate event_id is idempotent (returns 200 OK without re-processing)")
    void duplicateEventId_IsIdempotent() throws Exception {
        UserPrincipal principal = UserPrincipal.create(customer);
        HoldSeatsResponse holdResponse = bookingService.holdSeats(
                new HoldSeatsRequest(testEvent.getId(), List.of(testSeat.getId())),
                principal
        );

        InitiatePaymentResponse payInit = paymentService.initiatePayment(
                new InitiatePaymentRequest(holdResponse.bookingId()),
                principal
        );

        String eventId = "evt_idempotent_test_001";
        WebhookPayload payload = new WebhookPayload(
                eventId,
                "payment.succeeded",
                payInit.providerRef(),
                new BigDecimal("120.00"),
                holdResponse.bookingId()
        );
        String rawPayload = objectMapper.writeValueAsString(payload);
        long now = Instant.now().getEpochSecond();
        String signatureHeader = createSignatureHeader(rawPayload, now);

        // First delivery -> Processed
        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureHeader)
                        .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // Second delivery of EXACT same event -> Idempotent skip
        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureHeader)
                        .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event already processed (idempotent cache)"));

        // Only 1 record in webhook_events
        long count = webhookEventRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Webhook: payment.failed releases held seats back to AVAILABLE")
    void paymentFailedEvent_ReleasesSeats() throws Exception {
        UserPrincipal principal = UserPrincipal.create(customer);
        HoldSeatsResponse holdResponse = bookingService.holdSeats(
                new HoldSeatsRequest(testEvent.getId(), List.of(testSeat.getId())),
                principal
        );

        InitiatePaymentResponse payInit = paymentService.initiatePayment(
                new InitiatePaymentRequest(holdResponse.bookingId()),
                principal
        );

        String eventId = "evt_failed_" + UUID.randomUUID();
        WebhookPayload payload = new WebhookPayload(
                eventId,
                "payment.failed",
                payInit.providerRef(),
                new BigDecimal("120.00"),
                holdResponse.bookingId()
        );
        String rawPayload = objectMapper.writeValueAsString(payload);
        long now = Instant.now().getEpochSecond();
        String signatureHeader = createSignatureHeader(rawPayload, now);

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureHeader)
                        .content(rawPayload))
                .andExpect(status().isOk());

        Booking booking = bookingRepository.findById(holdResponse.bookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);

        EventSeat seat = seatRepository.findById(testSeat.getId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getBookingId()).isNull();

        Payment payment = paymentRepository.findByProviderRef(payInit.providerRef()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("Late Payment Rule: Seats still free -> Reclaimed and booking CONFIRMED")
    void latePayment_WhenSeatsStillFree_ReclaimsAndConfirms() throws Exception {
        UserPrincipal principal = UserPrincipal.create(customer);
        HoldSeatsResponse holdResponse = bookingService.holdSeats(
                new HoldSeatsRequest(testEvent.getId(), List.of(testSeat.getId())),
                principal
        );

        InitiatePaymentResponse payInit = paymentService.initiatePayment(
                new InitiatePaymentRequest(holdResponse.bookingId()),
                principal
        );

        // Manually simulate hold expiration (set hold_expires_at in past)
        Booking booking = bookingRepository.findById(holdResponse.bookingId()).orElseThrow();
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));
        bookingRepository.save(booking);

        EventSeat seat = seatRepository.findById(testSeat.getId()).orElseThrow();
        seat.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));
        seatRepository.save(seat);

        // Now payment arrives late
        String eventId = "evt_late_ok_" + UUID.randomUUID();
        WebhookPayload payload = new WebhookPayload(
                eventId,
                "payment.succeeded",
                payInit.providerRef(),
                new BigDecimal("120.00"),
                holdResponse.bookingId()
        );
        String rawPayload = objectMapper.writeValueAsString(payload);
        String signatureHeader = createSignatureHeader(rawPayload, Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureHeader)
                        .content(rawPayload))
                .andExpect(status().isOk());

        Booking updatedBooking = bookingRepository.findById(holdResponse.bookingId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        EventSeat updatedSeat = seatRepository.findById(testSeat.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    @DisplayName("Late Payment Rule: Seats taken by another user -> Booking CANCELLED and REFUNDED")
    void latePayment_WhenSeatsTaken_CancelsAndRefunds() throws Exception {
        UserPrincipal cust1 = UserPrincipal.create(customer);
        HoldSeatsResponse hold1 = bookingService.holdSeats(
                new HoldSeatsRequest(testEvent.getId(), List.of(testSeat.getId())),
                cust1
        );

        InitiatePaymentResponse payInit1 = paymentService.initiatePayment(
                new InitiatePaymentRequest(hold1.bookingId()),
                cust1
        );

        // Simulate cust1 hold expiry
        Booking booking1 = bookingRepository.findById(hold1.bookingId()).orElseThrow();
        booking1.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));
        bookingRepository.save(booking1);

        EventSeat seat = seatRepository.findById(testSeat.getId()).orElseThrow();
        seat.setHoldExpiresAt(LocalDateTime.now().minusMinutes(2));
        seatRepository.save(seat);

        // Customer 2 jumps in and holds the expired seat!
        UserPrincipal cust2 = UserPrincipal.create(customer2);
        HoldSeatsResponse hold2 = bookingService.holdSeats(
                new HoldSeatsRequest(testEvent.getId(), List.of(testSeat.getId())),
                cust2
        );

        // Now cust1's late payment webhook arrives
        String eventId = "evt_late_conflict_" + UUID.randomUUID();
        WebhookPayload payload = new WebhookPayload(
                eventId,
                "payment.succeeded",
                payInit1.providerRef(),
                new BigDecimal("120.00"),
                hold1.bookingId()
        );
        String rawPayload = objectMapper.writeValueAsString(payload);
        String signatureHeader = createSignatureHeader(rawPayload, Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signatureHeader)
                        .content(rawPayload))
                .andExpect(status().isOk());

        // Verify cust1 booking cancelled and refunded
        Booking updatedBooking1 = bookingRepository.findById(hold1.bookingId()).orElseThrow();
        assertThat(updatedBooking1.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        Payment payment1 = paymentRepository.findByProviderRef(payInit1.providerRef()).orElseThrow();
        assertThat(payment1.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        // Verify seat is STILL held by cust2
        EventSeat currentSeat = seatRepository.findById(testSeat.getId()).orElseThrow();
        assertThat(currentSeat.getBookingId()).isEqualTo(hold2.bookingId());
    }

    @Test
    @DisplayName("Cancellation Rule: Customer cancels > 24 hours before event -> Seats released & REFUNDED")
    void customerCancellation_Before24Hours_CancelsAndRefunds() {
        UserPrincipal principal = UserPrincipal.create(customer);
        HoldSeatsResponse holdResponse = bookingService.holdSeats(
                new HoldSeatsRequest(testEvent.getId(), List.of(testSeat.getId())),
                principal
        );

        InitiatePaymentResponse payInit = paymentService.initiatePayment(
                new InitiatePaymentRequest(holdResponse.bookingId()),
                principal
        );

        // Confirm booking via payment simulation
        paymentService.simulateMockPayment(new com.ticketrush.payment.dto.MockPaymentSimulateRequest(
                payInit.providerRef(),
                holdResponse.bookingId(),
                "payment.succeeded"
        ));

        // Customer cancels confirmed booking
        bookingService.cancelBooking(holdResponse.bookingId(), principal);

        // Verify invariants
        Booking booking = bookingRepository.findById(holdResponse.bookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        EventSeat seat = seatRepository.findById(testSeat.getId()).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getBookingId()).isNull();

        Payment payment = paymentRepository.findByProviderRef(payInit.providerRef()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
