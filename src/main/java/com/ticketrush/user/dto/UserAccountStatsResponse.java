package com.ticketrush.user.dto;

import java.math.BigDecimal;

public record UserAccountStatsResponse(
        long totalBookings,
        long confirmedTickets,
        BigDecimal totalSpent,
        String memberSince
) {}
