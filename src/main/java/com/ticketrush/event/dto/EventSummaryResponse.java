package com.ticketrush.event.dto;

import com.ticketrush.event.entity.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventSummaryResponse(
        Long id,
        String title,
        String category,
        String city,
        String venue,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int totalSeats,
        long availableSeats,
        BigDecimal startingPrice,
        EventStatus status,
        String posterUrl
) {
    public EventSummaryResponse(
            Long id,
            String title,
            String category,
            String city,
            String venue,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int totalSeats,
            long availableSeats,
            BigDecimal startingPrice,
            EventStatus status
    ) {
        this(id, title, category, city, venue, startsAt, endsAt, totalSeats, availableSeats, startingPrice, status, null);
    }
}
