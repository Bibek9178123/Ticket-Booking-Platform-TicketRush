package com.ticketrush.booking.dto;

import java.math.BigDecimal;

public record BookingSeatResponse(
        Long seatId,
        String rowLabel,
        int seatNumber,
        String tier,
        BigDecimal price
) {}
