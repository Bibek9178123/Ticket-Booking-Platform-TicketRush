package com.ticketrush.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HoldSeatsRequest(
        @NotNull(message = "Event ID is required")
        Long eventId,

        @NotEmpty(message = "At least one seat must be selected")
        @Size(min = 1, max = 6, message = "Anti-hoarding limit: You can hold at most 6 seats per booking")
        List<Long> seatIds
) {}
