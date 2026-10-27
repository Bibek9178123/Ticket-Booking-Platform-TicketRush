package com.ticketrush.booking.service;

import com.ticketrush.booking.dto.BookingDetailResponse;
import com.ticketrush.booking.dto.BookingSeatResponse;
import com.ticketrush.booking.dto.HoldSeatsRequest;
import com.ticketrush.booking.dto.HoldSeatsResponse;
import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingSeat;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.booking.repository.BookingSeatRepository;
import com.ticketrush.common.exception.BusinessRuleException;
import com.ticketrush.common.exception.ResourceNotFoundException;
import com.ticketrush.common.exception.SeatUnavailableException;
import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.entity.EventStatus;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.entity.EventSeat;
import com.ticketrush.seat.repository.EventSeatRepository;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final EventSeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final com.ticketrush.payment.repository.PaymentRepository paymentRepository;
    private final com.ticketrush.payment.service.PaymentGateway paymentGateway;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            EventSeatRepository seatRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            com.ticketrush.payment.repository.PaymentRepository paymentRepository,
            com.ticketrush.payment.service.PaymentGateway paymentGateway) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * CORE BOOKING ENGINE:
     * Atomically holds 1 to 6 seats for exactly 5 minutes with all-or-nothing rollback semantics.
     */
    @Transactional
    public HoldSeatsResponse holdSeats(HoldSeatsRequest request, UserPrincipal principal) {
        // 1. Anti-Hoarding Check: User cannot have more than 2 active unexpired holds pending payment
        long activeHolds = bookingRepository.countByUserIdAndStatusAndHoldExpiresAtAfter(
                principal.getId(), BookingStatus.PENDING_PAYMENT, LocalDateTime.now()
        );
        if (activeHolds >= 2) {
            throw new BusinessRuleException("Anti-hoarding rule: You already have 2 active bookings pending payment. Please complete or wait for them to expire.");
        }

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.eventId()));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot book seats for an event that is not PUBLISHED. Current status: " + event.getStatus());
        }

        // 2. Anti-Deadlock: Sort seat IDs strictly in ascending order
        List<Long> sortedSeatIds = request.seatIds().stream().distinct().sorted().toList();

        // 3. Load seat metadata to verify prices and event ownership
        List<EventSeat> requestedSeats = seatRepository.findAllById(sortedSeatIds);
        if (requestedSeats.size() != sortedSeatIds.size()) {
            throw new ResourceNotFoundException("One or more seat IDs do not exist.");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<String> seatLabels = new ArrayList<>();
        for (EventSeat s : requestedSeats) {
            if (!s.getEvent().getId().equals(event.getId())) {
                throw new BusinessRuleException("Seat " + s.getId() + " does not belong to event " + event.getId());
            }
            totalAmount = totalAmount.add(s.getPrice());
            seatLabels.add(s.getRowLabel() + s.getSeatNumber());
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 4. Create PENDING_PAYMENT booking entity with 5-minute hold timestamp
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(5);

        Booking booking = new Booking(user, event, totalAmount, expiresAt);
        Booking savedBooking = bookingRepository.save(booking);

        // 5. ATOMIC CONDITIONAL UPDATE:
        // Sets status = 'HELD', booking_id = savedBooking.id, hold_expires_at = expiresAt
        // for seats that are AVAILABLE OR (HELD and hold_expires_at < now).
        int updatedRows = seatRepository.holdSeatsAtomic(sortedSeatIds, savedBooking.getId(), expiresAt, now);

        // 6. ALL-OR-NOTHING INVARIANT CHECK:
        if (updatedRows < sortedSeatIds.size()) {
            log.warn("Hold conflict detected! Requested {} seats, but only {} updated. Rolling back booking {}.",
                    sortedSeatIds.size(), updatedRows, savedBooking.getId());
            // Throwing RuntimeException triggers full Spring @Transactional rollback
            throw new SeatUnavailableException("One or more selected seats are no longer available. Please select different seats.");
        }

        // 7. Success: Save individual audit seat records
        for (EventSeat s : requestedSeats) {
            bookingSeatRepository.save(new BookingSeat(savedBooking, s, s.getPrice()));
        }

        long remainingSeconds = Math.max(0, Duration.between(now, expiresAt).getSeconds());

        log.info("Booking {} successfully held {} seats for user {}.", savedBooking.getId(), sortedSeatIds.size(), user.getEmail());

        return new HoldSeatsResponse(
                savedBooking.getId(),
                event.getId(),
                event.getTitle(),
                seatLabels,
                totalAmount,
                savedBooking.getStatus(),
                expiresAt,
                remainingSeconds
        );
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingDetails(Long bookingId, UserPrincipal principal) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        boolean isOwner = booking.getUser().getId().equals(principal.getId());
        boolean isStaff = principal.getRole() == Role.ADMIN ||
                booking.getEvent().getOrganizer().getId().equals(principal.getId());

        if (!isOwner && !isStaff) {
            throw new AccessDeniedException("You do not have permission to view this booking.");
        }

        List<BookingSeat> bookingSeats = bookingSeatRepository.findAllByBookingId(booking.getId());
        List<BookingSeatResponse> seatResponses = bookingSeats.stream()
                .map(bs -> new BookingSeatResponse(
                        bs.getEventSeat().getId(),
                        bs.getEventSeat().getRowLabel(),
                        bs.getEventSeat().getSeatNumber(),
                        bs.getEventSeat().getTier(),
                        bs.getPricePaid()
                ))
                .toList();

        long remainingSeconds = (booking.getHoldExpiresAt() != null && booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                ? Math.max(0, Duration.between(LocalDateTime.now(), booking.getHoldExpiresAt()).getSeconds())
                : 0;

        return new BookingDetailResponse(
                booking.getId(),
                booking.getEvent().getId(),
                booking.getEvent().getTitle(),
                booking.getEvent().getVenue(),
                booking.getEvent().getCity(),
                booking.getEvent().getStartsAt(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getBookingCode(),
                booking.getHoldExpiresAt(),
                booking.getCheckedInAt(),
                seatResponses,
                remainingSeconds
        );
    }

    @Transactional(readOnly = true)
    public Page<BookingDetailResponse> getUserBookings(UserPrincipal principal, Pageable pageable) {
        return bookingRepository.findAllByUserIdOrderByCreatedAtDesc(principal.getId(), pageable)
                .map(b -> {
                    List<BookingSeat> bs = bookingSeatRepository.findAllByBookingId(b.getId());
                    List<BookingSeatResponse> seats = bs.stream()
                            .map(s -> new BookingSeatResponse(
                                    s.getEventSeat().getId(),
                                    s.getEventSeat().getRowLabel(),
                                    s.getEventSeat().getSeatNumber(),
                                    s.getEventSeat().getTier(),
                                    s.getPricePaid()
                            )).toList();

                    long remainingSeconds = (b.getHoldExpiresAt() != null && b.getStatus() == BookingStatus.PENDING_PAYMENT)
                            ? Math.max(0, Duration.between(LocalDateTime.now(), b.getHoldExpiresAt()).getSeconds())
                            : 0;

                    return new BookingDetailResponse(
                            b.getId(),
                            b.getEvent().getId(),
                            b.getEvent().getTitle(),
                            b.getEvent().getVenue(),
                            b.getEvent().getCity(),
                            b.getEvent().getStartsAt(),
                            b.getStatus(),
                            b.getTotalAmount(),
                            b.getBookingCode(),
                            b.getHoldExpiresAt(),
                            b.getCheckedInAt(),
                            seats,
                            remainingSeconds
                    );
                });
    }

    @Transactional
    public BookingDetailResponse cancelBooking(Long bookingId, UserPrincipal principal) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        boolean isOwner = booking.getUser().getId().equals(principal.getId());
        boolean isAdmin = principal.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to cancel this booking.");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Only CONFIRMED bookings can be cancelled. Current status: " + booking.getStatus());
        }

        // 24-hour cancellation deadline rule:
        LocalDateTime deadline = booking.getEvent().getStartsAt().minusHours(24);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessRuleException("Bookings can only be cancelled up to 24 hours before the event starts.");
        }

        // 1. Release seats back to AVAILABLE
        seatRepository.releaseSeatsByBookingId(booking.getId());

        // 2. Transition booking to CANCELLED
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // 3. Initiate refund on payment
        paymentRepository.findByBookingId(booking.getId()).ifPresent(payment -> {
            payment.setStatus(com.ticketrush.payment.entity.PaymentStatus.REFUND_PENDING);
            paymentRepository.save(payment);

            com.ticketrush.payment.service.PaymentGateway.RefundResult refundResult = paymentGateway.processRefund(payment);
            if (refundResult.success()) {
                payment.setStatus(com.ticketrush.payment.entity.PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
            }
        });

        log.info("Booking {} successfully cancelled by user {}. Refund initiated.", booking.getId(), principal.getUsername());
        return getBookingDetails(booking.getId(), principal);
    }

    @Transactional
    public BookingDetailResponse checkIn(String bookingCode, UserPrincipal principal) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("No booking found with code: " + bookingCode));

        boolean isOrganizer = booking.getEvent().getOrganizer().getId().equals(principal.getId());
        boolean isAdmin = principal.getRole() == Role.ADMIN;

        if (!isOrganizer && !isAdmin) {
            throw new AccessDeniedException("Only the event organizer or system admin can check in attendees.");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Cannot check in booking with status: " + booking.getStatus());
        }

        if (booking.getCheckedInAt() != null) {
            throw new BusinessRuleException("Ticket has already been checked in at " + booking.getCheckedInAt());
        }

        booking.setCheckedInAt(LocalDateTime.now());
        Booking updated = bookingRepository.save(booking);

        return getBookingDetails(updated.getId(), principal);
    }
}
