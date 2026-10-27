package com.ticketrush.booking.naive;

import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.common.exception.SeatUnavailableException;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.entity.EventSeat;
import com.ticketrush.seat.entity.SeatStatus;
import com.ticketrush.seat.repository.EventSeatRepository;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DELIBERATELY BUGGY NAIVE BOOKING SERVICE (FOR DEMO/TESTING ONLY).
 * Placed in test sources to demonstrate TOCTOU (Time-of-Check to Time-of-Use) race conditions.
 * It reads the seat status first, then writes. Under concurrent load, multiple threads
 * see the seat as AVAILABLE simultaneously and both book it, causing double-bookings.
 */
@Service
public class NaiveBookingService {

    private final EventSeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public NaiveBookingService(
            EventSeatRepository seatRepository,
            BookingRepository bookingRepository,
            EventRepository eventRepository,
            UserRepository userRepository) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Booking bookSeatNaive(Long eventId, Long seatId, Long userId) {
        // STEP 1: READ (Time of Check)
        EventSeat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found"));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatUnavailableException("Seat is not available: " + seatId);
        }

        // Simulate real-world application processing, I/O, or thread interleaving
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // STEP 2: WRITE (Time of Use - State has changed in the meantime!)
        seat.setStatus(SeatStatus.HELD);
        seat.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        seatRepository.save(seat);

        Event event = eventRepository.findById(eventId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        Booking booking = new Booking(user, event, seat.getPrice(), LocalDateTime.now().plusMinutes(5));
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(booking);
    }
}
