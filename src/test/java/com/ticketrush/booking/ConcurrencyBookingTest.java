package com.ticketrush.booking;

import com.ticketrush.booking.dto.HoldSeatsRequest;
import com.ticketrush.booking.dto.HoldSeatsResponse;
import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.booking.repository.BookingSeatRepository;
import com.ticketrush.booking.service.BookingService;
import com.ticketrush.common.exception.BusinessRuleException;
import com.ticketrush.common.exception.SeatUnavailableException;
import com.ticketrush.common.security.UserPrincipal;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyBookingTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyBookingTest.class);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private EventSeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User organizer;

    @BeforeEach
    void setUp() {
        bookingSeatRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        organizer = new User("organizer.conc@test.com", "pass", "Organizer Conc", Role.ORGANIZER);
        organizer = userRepository.save(organizer);
    }

    @Test
    @DisplayName("CONCURRENCY A: 100 threads compete for the EXACT SAME 1 seat -> Exactly 1 succeeds, 99 fail with 409")
    void test100ThreadsCompeteForOneSeat() throws InterruptedException {
        int numberOfUsers = 100;

        // Create published event with exactly ONE seat
        Event event = new Event(
                organizer, "Highlander Solo Concert", "Only one can win", "Music", "Austin", "Moody Center",
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(2),
                1, 1, "[]"
        );
        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);

        EventSeat seat = new EventSeat(event, "A", 1, "VIP", BigDecimal.valueOf(150.00));
        seat.setStatus(SeatStatus.AVAILABLE);
        seat = seatRepository.save(seat);
        final Long targetSeatId = seat.getId();
        final Long eventId = event.getId();

        // Pre-create 100 distinct users
        List<UserPrincipal> users = new ArrayList<>();
        for (int i = 1; i <= numberOfUsers; i++) {
            User u = new User("racer" + i + "@ticketrush.com", "pass", "Racer " + i, Role.CUSTOMER);
            u = userRepository.save(u);
            users.add(UserPrincipal.create(u));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numberOfUsers);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(numberOfUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        log.info("Firing 100 concurrent threads competing for Seat ID {}...", targetSeatId);

        for (int i = 0; i < numberOfUsers; i++) {
            final UserPrincipal principal = users.get(i);
            executor.submit(() -> {
                try {
                    startGun.await(); // Hold all threads at starting line
                    HoldSeatsRequest req = new HoldSeatsRequest(eventId, List.of(targetSeatId));
                    bookingService.holdSeats(req, principal);
                    successCount.incrementAndGet();
                } catch (SeatUnavailableException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    log.error("Unexpected error: ", ex);
                    otherErrors.incrementAndGet();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        // Fire the gun!
        startGun.countDown();
        boolean completed = finishLine.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        log.info("CONCURRENCY A RESULTS: {} Succeeded, {} Conflicts (409), {} Unexpected",
                successCount.get(), conflictCount.get(), otherErrors.get());

        // --- CORE INVARIANTS ---
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(99);
        assertThat(otherErrors.get()).isEqualTo(0);

        // Verify Database State
        EventSeat finalSeat = seatRepository.findById(targetSeatId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(finalSeat.getBookingId()).isNotNull();

        long totalBookingsInDb = bookingRepository.count();
        assertThat(totalBookingsInDb).isEqualTo(1);
    }

    @Test
    @DisplayName("CONCURRENCY B: 200 threads race for random groups of 1-4 seats out of 30 -> Invariant proves zero double-booking")
    void test200ThreadsRandomOverlappingSeats() throws InterruptedException {
        int numberOfThreads = 200;
        int totalSeatsInVenue = 30; // 3 rows x 10 seats

        Event event = new Event(
                organizer, "Mega Festival 2026", "Big Stadium", "Festival", "Chicago", "Soldier Field",
                LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(10).plusHours(4),
                3, 10, "[]"
        );
        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);
        final Long eventId = event.getId();

        List<Long> allSeatIds = new ArrayList<>();
        for (int r = 1; r <= 3; r++) {
            String row = String.valueOf((char) ('A' + r - 1));
            for (int s = 1; s <= 10; s++) {
                EventSeat seat = new EventSeat(event, row, s, "General", BigDecimal.valueOf(50.00));
                seat = seatRepository.save(seat);
                allSeatIds.add(seat.getId());
            }
        }

        List<UserPrincipal> users = new ArrayList<>();
        for (int i = 1; i <= numberOfThreads; i++) {
            User u = new User("heavyracer" + i + "@ticketrush.com", "pass", "Heavy Racer " + i, Role.CUSTOMER);
            u = userRepository.save(u);
            users.add(UserPrincipal.create(u));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Random random = new Random(42); // deterministic seed for reproducibility

        for (int i = 0; i < numberOfThreads; i++) {
            final UserPrincipal principal = users.get(i);

            // Pick 1 to 4 random seats from the 30 seats
            int groupSize = 1 + random.nextInt(4);
            List<Long> pool = new ArrayList<>(allSeatIds);
            Collections.shuffle(pool, random);
            final List<Long> chosenSeats = pool.subList(0, groupSize);

            executor.submit(() -> {
                try {
                    startGun.await();
                    HoldSeatsRequest req = new HoldSeatsRequest(eventId, chosenSeats);
                    bookingService.holdSeats(req, principal);
                    successCount.incrementAndGet();
                } catch (SeatUnavailableException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    log.error("Error in test B: ", ex);
                } finally {
                    finishLine.countDown();
                }
            });
        }

        // Fire the gun!
        startGun.countDown();
        boolean completed = finishLine.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        log.info("CONCURRENCY B RESULTS: {} bookings succeeded, {} requests rejected due to conflict.",
                successCount.get(), conflictCount.get());

        // --- SQL INVARIANT CHECK 1: ZERO DOUBLE BOOKINGS ---
        // Verify that no seat ID appears more than once in active bookings
        Integer doubleBookedSeats = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                        "  SELECT bs.event_seat_id, COUNT(*) as cnt " +
                        "  FROM booking_seats bs " +
                        "  JOIN bookings b ON bs.booking_id = b.id " +
                        "  WHERE b.status = 'PENDING_PAYMENT' " +
                        "  GROUP BY bs.event_seat_id " +
                        "  HAVING cnt > 1" +
                        ") as duplicates",
                Integer.class
        );
        assertThat(doubleBookedSeats).isEqualTo(0);

        // --- SQL INVARIANT CHECK 2: EXACT CONSERVATION OF SEATS ---
        // The total number of seats marked HELD in event_seats must EXACTLY equal
        // the total number of seat rows recorded in booking_seats for successful bookings!
        Integer heldSeatsInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_seats WHERE event_id = ? AND status = 'HELD'",
                Integer.class,
                eventId
        );
        Integer recordedSeatsInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_seats bs " +
                        "JOIN bookings b ON bs.booking_id = b.id " +
                        "WHERE b.status = 'PENDING_PAYMENT'",
                Integer.class
        );
        assertThat(heldSeatsInDb).isEqualTo(recordedSeatsInDb);
        assertThat(heldSeatsInDb).isLessThanOrEqualTo(totalSeatsInVenue);
    }

    @Test
    @DisplayName("EXPIRED HOLDS: Can be re-booked immediately without waiting for background scheduler")
    void testExpiredHoldRebookedImmediately() {
        Event event = new Event(
                organizer, "Expiry Demo", "Desc", "Concert", "Dallas", "Center",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2),
                1, 1, "[]"
        );
        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);

        // Seat is marked HELD, but hold expired 30 seconds ago
        EventSeat seat = new EventSeat(event, "A", 1, "VIP", BigDecimal.valueOf(100.00));
        seat.setStatus(SeatStatus.HELD);
        seat.setHoldExpiresAt(LocalDateTime.now().minusSeconds(30));
        seat = seatRepository.save(seat);

        User customer = new User("newcustomer@test.com", "pass", "New Customer", Role.CUSTOMER);
        customer = userRepository.save(customer);

        // New customer attempts to hold it:
        HoldSeatsResponse response = bookingService.holdSeats(
                new HoldSeatsRequest(event.getId(), List.of(seat.getId())),
                UserPrincipal.create(customer)
        );

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        EventSeat updatedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(updatedSeat.getBookingId()).isEqualTo(response.bookingId());
        assertThat(updatedSeat.getHoldExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("ANTI-HOARDING: User cannot hold more than 2 bookings concurrently")
    void testAntiHoardingEnforced() {
        Event event = new Event(
                organizer, "Hoarding Test", "Desc", "Concert", "Dallas", "Center",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2),
                5, 5, "[]"
        );
        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);

        List<EventSeat> seats = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            seats.add(seatRepository.save(new EventSeat(event, "A", i, "Gen", BigDecimal.valueOf(30.00))));
        }

        User hoarder = new User("hoarder@test.com", "pass", "Hoarder", Role.CUSTOMER);
        hoarder = userRepository.save(hoarder);
        UserPrincipal principal = UserPrincipal.create(hoarder);

        // Booking 1: OK
        bookingService.holdSeats(new HoldSeatsRequest(event.getId(), List.of(seats.get(0).getId())), principal);
        // Booking 2: OK
        bookingService.holdSeats(new HoldSeatsRequest(event.getId(), List.of(seats.get(1).getId())), principal);

        final Long eventId = event.getId();
        final Long seat3Id = seats.get(2).getId();

        // Booking 3: Must be rejected with BusinessRuleException
        assertThrows(BusinessRuleException.class, () ->
                bookingService.holdSeats(new HoldSeatsRequest(eventId, List.of(seat3Id)), principal)
        );
    }
}
