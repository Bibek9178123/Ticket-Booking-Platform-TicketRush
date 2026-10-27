package com.ticketrush.seat.dto;

import java.util.List;

public record SeatMapResponse(
        Long eventId,
        String eventTitle,
        int rowCount,
        int seatsPerRow,
        List<SeatResponse> seats
) {}
