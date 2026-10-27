package com.ticketrush.admin.dto;

import com.ticketrush.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull(message = "Role is required")
        Role role
) {}
