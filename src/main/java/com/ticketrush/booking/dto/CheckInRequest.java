package com.ticketrush.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
        @NotBlank(message = "Booking code is required")
        String bookingCode
) {}
