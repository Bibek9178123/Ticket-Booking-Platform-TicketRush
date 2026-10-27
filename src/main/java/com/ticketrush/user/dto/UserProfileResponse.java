package com.ticketrush.user.dto;

import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String phone,
        String preferredCity,
        Role role,
        boolean active,
        LocalDateTime createdAt
) {
    public UserProfileResponse(Long id, String email, String name, Role role, boolean active) {
        this(id, email, name, null, "Hyderabad", role, active, null);
    }

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getPreferredCity() != null ? user.getPreferredCity() : "Hyderabad",
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
