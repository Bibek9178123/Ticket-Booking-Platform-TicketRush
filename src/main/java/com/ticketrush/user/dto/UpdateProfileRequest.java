package com.ticketrush.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Name cannot be blank")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Pattern(regexp = "^$|^[0-9+\\-\\s()]{7,20}$", message = "Invalid phone number format")
        String phone,

        @Size(max = 50, message = "City name cannot exceed 50 characters")
        String preferredCity
) {}
