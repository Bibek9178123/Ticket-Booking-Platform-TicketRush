package com.ticketrush.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String email,
        String name,
        String role
) {
    public AuthResponse(String accessToken, Long userId, String email, String name, String role) {
        this(accessToken, "Bearer", userId, email, name, role);
    }
}
