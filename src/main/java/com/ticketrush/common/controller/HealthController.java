package com.ticketrush.common.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Lightweight public health check controller.
 * Used by Render, UptimeRobot, and cloud monitors for keep-alive pings
 * to prevent free-tier instances from spinning down after 15 minutes of inactivity.
 */
@RestController
public class HealthController {

    private final Instant startTime = Instant.now();

    @GetMapping({"/api/health", "/health", "/api/health/ping"})
    public ResponseEntity<Map<String, Object>> checkHealth() {
        long uptimeSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        Map<String, Object> healthStatus = Map.of(
                "status", "UP",
                "service", "ticketrush",
                "timestamp", Instant.now().toString(),
                "uptimeSeconds", uptimeSeconds,
                "message", "TicketRush service is live and healthy"
        );
        return ResponseEntity.ok(healthStatus);
    }
}
