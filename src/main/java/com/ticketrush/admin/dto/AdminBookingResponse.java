package com.ticketrush.admin.dto;

import com.ticketrush.booking.entity.Booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminBookingResponse(
        Long id,
        String bookingCode,
        String customerName,
        String customerEmail,
        String eventTitle,
        String eventCity,
        BigDecimal totalAmount,
        String status,
        LocalDateTime createdAt
) {
    public static AdminBookingResponse from(Booking b) {
        return new AdminBookingResponse(
                b.getId(),
                b.getBookingCode() != null ? b.getBookingCode() : "PENDING",
                b.getUser() != null ? b.getUser().getName() : "Unknown",
                b.getUser() != null ? b.getUser().getEmail() : "Unknown",
                b.getEvent() != null ? b.getEvent().getTitle() : "Unknown",
                b.getEvent() != null ? b.getEvent().getCity() : "Unknown",
                b.getTotalAmount(),
                b.getStatus().name(),
                b.getCreatedAt()
        );
    }
}
