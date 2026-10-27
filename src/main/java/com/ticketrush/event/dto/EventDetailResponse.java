package com.ticketrush.event.dto;

import com.ticketrush.event.entity.EventStatus;

import java.time.LocalDateTime;
import java.util.List;

public record EventDetailResponse(
        Long id,
        Long organizerId,
        String organizerName,
        String title,
        String description,
        String category,
        String city,
        String venue,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int rowCount,
        int seatsPerRow,
        EventStatus status,
        int totalSeats,
        long availableSeats,
        List<TierRule> tiers,
        String posterUrl
) {
    public EventDetailResponse(
            Long id,
            Long organizerId,
            String organizerName,
            String title,
            String description,
            String category,
            String city,
            String venue,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int rowCount,
            int seatsPerRow,
            EventStatus status,
            int totalSeats,
            long availableSeats,
            List<TierRule> tiers
    ) {
        this(id, organizerId, organizerName, title, description, category, city, venue, startsAt, endsAt, rowCount, seatsPerRow, status, totalSeats, availableSeats, tiers, null);
    }
}
