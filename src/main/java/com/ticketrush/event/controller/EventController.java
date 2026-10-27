package com.ticketrush.event.controller;

import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.event.dto.CreateEventRequest;
import com.ticketrush.event.dto.EventDetailResponse;
import com.ticketrush.event.dto.EventSummaryResponse;
import com.ticketrush.event.dto.UpdateEventRequest;
import com.ticketrush.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<Page<EventSummaryResponse>> searchEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(page = 0, size = 12, sort = "startsAt", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<EventSummaryResponse> events = eventService.searchEvents(keyword, city, category, fromDate, toDate, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> getEventDetails(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventDetails(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public ResponseEntity<EventDetailResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        EventDetailResponse created = eventService.createEvent(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public ResponseEntity<EventDetailResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        EventDetailResponse updated = eventService.updateEvent(id, request, principal);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public ResponseEntity<EventDetailResponse> publishEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        EventDetailResponse published = eventService.publishEvent(id, principal);
        return ResponseEntity.ok(published);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public ResponseEntity<EventDetailResponse> cancelEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        EventDetailResponse cancelled = eventService.cancelEvent(id, principal);
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public ResponseEntity<Page<EventSummaryResponse>> getMyEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(eventService.getOrganizerEvents(principal, pageable));
    }
}
