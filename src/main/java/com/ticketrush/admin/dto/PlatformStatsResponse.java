package com.ticketrush.admin.dto;

import java.math.BigDecimal;

public record PlatformStatsResponse(
        long totalUsers,
        long totalEvents,
        long totalBookings,
        BigDecimal totalRevenue
) {}
