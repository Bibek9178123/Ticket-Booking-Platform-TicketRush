package com.ticketrush.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreateEventRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        String description,

        @NotBlank(message = "Category is required")
        String category,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Venue is required")
        String venue,

        @NotNull(message = "Start time is required")
        @Future(message = "Event start time must be in the future")
        LocalDateTime startsAt,

        @NotNull(message = "End time is required")
        LocalDateTime endsAt,

        @Min(value = 1, message = "Row count must be at least 1")
        @Max(value = 50, message = "Row count cannot exceed 50")
        int rowCount,

        @Min(value = 1, message = "Seats per row must be at least 1")
        @Max(value = 50, message = "Seats per row cannot exceed 50")
        int seatsPerRow,

        @NotEmpty(message = "At least one pricing tier is required")
        @Valid
        List<TierRule> tiers
) {}
