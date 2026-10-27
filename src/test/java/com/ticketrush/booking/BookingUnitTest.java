package com.ticketrush.booking;

import com.ticketrush.booking.dto.HoldSeatsRequest;
import com.ticketrush.booking.dto.HoldSeatsResponse;
import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingStatus;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.booking.repository.BookingSeatRepository;
import com.ticketrush.booking.service.BookingService;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingUnitTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSeatRepository bookingSeatRepository;

    @Mock
    private EventSeatRepository seatRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    private User user;
    private Event event;
    private EventSeat seat1;
    private EventSeat seat2;

    @BeforeEach
    void setUp() {
        user = new User("test@user.com", "pass", "Test User", Role.CUSTOMER);
        user.setId(10L);

        User organizer = new User("org@test.com", "pass", "Org", Role.ORGANIZER);
        organizer.setId(20L);

        event = new Event(
                organizer, "Festival", "Desc", "Music", "NYC", "Park",
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(2),
                2, 2, "[]"
        );
        event.setId(100L);
        event.setStatus(EventStatus.PUBLISHED);

        seat1 = new EventSeat(event, "A", 1, "VIP", BigDecimal.valueOf(120.00));
        seat1.setId(1L);

        seat2 = new EventSeat(event, "A", 2, "General", BigDecimal.valueOf(45.50));
        seat2.setId(2L);
    }

    @Test
    @DisplayName("Unit: Total price calculation correctly sums distinct seat prices")
    void testTotalPriceCalculation() {
        UserPrincipal principal = UserPrincipal.create(user);
        HoldSeatsRequest req = new HoldSeatsRequest(100L, List.of(1L, 2L));

        when(bookingRepository.countByUserIdAndStatusAndHoldExpiresAtAfter(eq(10L), eq(BookingStatus.PENDING_PAYMENT), any())).thenReturn(0L);
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(seatRepository.findAllById(anyList())).thenReturn(List.of(seat1, seat2));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        Booking mockSavedBooking = new Booking(user, event, BigDecimal.valueOf(165.50), LocalDateTime.now().plusMinutes(5));
        mockSavedBooking.setId(555L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(mockSavedBooking);
        when(seatRepository.holdSeatsAtomic(eq(List.of(1L, 2L)), eq(555L), any(), any())).thenReturn(2);

        HoldSeatsResponse response = bookingService.holdSeats(req, principal);

        assertThat(response).isNotNull();
        // 120.00 + 45.50 = 165.50
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(165.50));
        assertThat(response.seatLabels()).containsExactly("A1", "A2");
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        verify(seatRepository).holdSeatsAtomic(eq(List.of(1L, 2L)), eq(555L), any(), any());
    }

    @Test
    @DisplayName("Unit: Booking state transitions correctly to CONFIRMED and EXPIRED")
    void testBookingStateTransitions() {
        Booking booking = new Booking(user, event, BigDecimal.valueOf(100.00), LocalDateTime.now().plusMinutes(5));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        // Transition to CONFIRMED
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookingCode("TR-2026-XYZ99");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getBookingCode()).isEqualTo("TR-2026-XYZ99");

        // Transition to EXPIRED
        Booking expiredBooking = new Booking(user, event, BigDecimal.valueOf(50.00), LocalDateTime.now().minusMinutes(1));
        expiredBooking.setStatus(BookingStatus.EXPIRED);
        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }
}
