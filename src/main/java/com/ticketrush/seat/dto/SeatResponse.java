package com.ticketrush.seat.dto;

import com.ticketrush.seat.entity.SeatStatus;

import java.math.BigDecimal;

public record SeatResponse(
        Long id,
        String rowLabel,
        int seatNumber,
        String tier,
        BigDecimal price,
        SeatStatus effectiveStatus
) {}
