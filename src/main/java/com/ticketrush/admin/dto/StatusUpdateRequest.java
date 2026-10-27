package com.ticketrush.admin.dto;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {}
