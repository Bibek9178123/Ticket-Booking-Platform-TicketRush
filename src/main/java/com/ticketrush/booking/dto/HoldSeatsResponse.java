package com.ticketrush.booking.dto;

import com.ticketrush.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record HoldSeatsResponse(
        Long bookingId,
        Long eventId,
        String eventTitle,
        List<String> seatLabels,
        BigDecimal totalAmount,
        BookingStatus status,
        LocalDateTime holdExpiresAt,
        long remainingSeconds
) {}
