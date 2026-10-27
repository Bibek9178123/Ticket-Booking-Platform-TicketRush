package com.ticketrush.booking.naive;

import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.entity.EventStatus;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.entity.EventSeat;
import com.ticketrush.seat.entity.SeatStatus;
import com.ticketrush.seat.repository.EventSeatRepository;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NaiveBookingTest {

    private static final Logger log = LoggerFactory.getLogger(NaiveBookingTest.class);

    @Autowired
    private NaiveBookingService naiveBookingService;

    @Autowired
    private EventSeatRepository seatRepository;

    @Autowired
    private com.ticketrush.booking.repository.BookingSeatRepository bookingSeatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private Long eventId;
    private Long seatId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookingSeatRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User organizer = new User("org.naive@test.com", "hash", "Org", Role.ORGANIZER);
        organizer = userRepository.save(organizer);

        Event event = new Event(
                organizer, "Naive Race Event", "Desc", "Music", "City", "Venue",
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(2),
                1, 1, "[]"
        );
        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);
        eventId = event.getId();

        // Exactly ONE single seat
        EventSeat seat = new EventSeat(event, "A", 1, "VIP", BigDecimal.valueOf(100.00));
        seat.setStatus(SeatStatus.AVAILABLE);
        seat = seatRepository.save(seat);
        seatId = seat.getId();

        // Create 20 distinct users
        for (int i = 1; i <= 20; i++) {
            User u = new User("user" + i + "@naive.com", "hash", "User " + i, Role.CUSTOMER);
            u = userRepository.save(u);
            userIds.add(u.getId());
        }
    }

    @Test
    @DisplayName("PROVE BUG: Naive read-then-write service allows double-booking under concurrency")
    void testNaiveDoubleBookingOccurs() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final Long uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    startGun.await(); // Synchronize all 20 threads at the exact same millisecond
                    naiveBookingService.bookSeatNaive(eventId, seatId, uid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        // Fire the gun!
        startGun.countDown();
        boolean completed = finishLine.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        log.info("NAIVE RACE RESULT: {} bookings succeeded for the 1 single seat!", successCount.get());

        // PROOF: More than 1 thread succeeded because read and write were not atomic!
        assertThat(successCount.get())
                .withFailMessage("Expected naive service to double-book, but only " + successCount.get() + " succeeded.")
                .isGreaterThan(1);
    }
}
