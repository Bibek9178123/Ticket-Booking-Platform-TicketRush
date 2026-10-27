package com.ticketrush.event.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TierRule(
        @NotBlank(message = "Tier name is required")
        String name,

        @Min(value = 1, message = "Row start must be at least 1")
        int rowStart,

        @Min(value = 1, message = "Row end must be at least 1")
        int rowEnd,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        BigDecimal price
) {}
