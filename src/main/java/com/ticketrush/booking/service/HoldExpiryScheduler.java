package com.ticketrush.booking.service;

import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.seat.repository.EventSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final BookingRepository bookingRepository;
    private final EventSeatRepository seatRepository;

    public HoldExpiryScheduler(BookingRepository bookingRepository, EventSeatRepository seatRepository) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
    }

    /**
     * Garbage collector for expired seat holds.
     * Runs every 30 seconds (30,000 ms).
     * Note: Core system correctness does NOT depend on this scheduler!
     * The atomic conditional query in BookingService already reclaims expired seats inline.
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void cleanupExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Mark expired bookings
        List<Booking> expiredBookings = bookingRepository.findAllByStatusAndHoldExpiresAtBefore(
                BookingStatus.PENDING_PAYMENT, now
        );

        if (!expiredBookings.isEmpty()) {
            for (Booking b : expiredBookings) {
                b.setStatus(BookingStatus.EXPIRED);
            }
            bookingRepository.saveAll(expiredBookings);
            log.info("HoldExpiryScheduler: Marked {} bookings as EXPIRED.", expiredBookings.size());
        }

        // 2. Bulk release any lingering expired seats back to AVAILABLE
        int releasedSeats = seatRepository.releaseExpiredHolds(now);
        if (releasedSeats > 0) {
            log.info("HoldExpiryScheduler: Released {} expired seats back to AVAILABLE.", releasedSeats);
        }
    }
}
