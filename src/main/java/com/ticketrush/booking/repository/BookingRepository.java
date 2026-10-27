package com.ticketrush.booking.repository;

import com.ticketrush.booking.entity.Booking;
import com.ticketrush.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Anti-hoarding: Count active PENDING_PAYMENT bookings for a user
    long countByUserIdAndStatus(Long userId, BookingStatus status);

    long countByUserIdAndStatusAndHoldExpiresAtAfter(Long userId, BookingStatus status, LocalDateTime now);

    Page<Booking> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Booking> findByBookingCode(String bookingCode);

    // Used by background hold-expiry cleanup job
    List<Booking> findAllByStatusAndHoldExpiresAtBefore(BookingStatus status, LocalDateTime now);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.status = com.ticketrush.booking.entity.BookingStatus.CONFIRMED")
    java.math.BigDecimal sumConfirmedRevenue();

    long countByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.user.id = :userId AND b.status = com.ticketrush.booking.entity.BookingStatus.CONFIRMED")
    java.math.BigDecimal sumConfirmedSpentByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
