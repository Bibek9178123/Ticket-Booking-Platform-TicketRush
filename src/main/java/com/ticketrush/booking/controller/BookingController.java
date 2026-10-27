package com.ticketrush.booking.controller;

import com.ticketrush.booking.dto.BookingDetailResponse;
import com.ticketrush.booking.dto.CheckInRequest;
import com.ticketrush.booking.dto.HoldSeatsRequest;
import com.ticketrush.booking.dto.HoldSeatsResponse;
import com.ticketrush.booking.service.BookingService;
import com.ticketrush.common.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/hold")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<HoldSeatsResponse> holdSeats(
            @Valid @RequestBody HoldSeatsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        HoldSeatsResponse response = bookingService.holdSeats(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDetailResponse> getBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        BookingDetailResponse response = bookingService.getBookingDetails(id, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingDetailResponse> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        BookingDetailResponse response = bookingService.cancelBooking(id, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<BookingDetailResponse>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(page = 0, size = 10) Pageable pageable) {

        Page<BookingDetailResponse> page = bookingService.getUserBookings(principal, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public ResponseEntity<BookingDetailResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        BookingDetailResponse response = bookingService.checkIn(request.bookingCode(), principal);
        return ResponseEntity.ok(response);
    }
}
