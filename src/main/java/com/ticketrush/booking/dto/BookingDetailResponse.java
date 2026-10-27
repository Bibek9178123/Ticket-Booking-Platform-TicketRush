package com.ticketrush.booking.dto;

import com.ticketrush.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingDetailResponse(
        Long bookingId,
        Long eventId,
        String eventTitle,
        String venue,
        String city,
        LocalDateTime startsAt,
        BookingStatus status,
        BigDecimal totalAmount,
        String bookingCode,
        LocalDateTime holdExpiresAt,
        LocalDateTime checkedInAt,
        List<BookingSeatResponse> seats,
        long remainingSeconds
) {
    public BookingDetailResponse(
            Long bookingId,
            Long eventId,
            String eventTitle,
            String venue,
            String city,
            LocalDateTime startsAt,
            BookingStatus status,
            BigDecimal totalAmount,
            String bookingCode,
            LocalDateTime holdExpiresAt,
            LocalDateTime checkedInAt,
            List<BookingSeatResponse> seats
    ) {
        this(bookingId, eventId, eventTitle, venue, city, startsAt, status, totalAmount, bookingCode, holdExpiresAt, checkedInAt, seats, 0L);
    }
}
