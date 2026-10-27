package com.ticketrush.booking.repository;

import com.ticketrush.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findAllByBookingId(Long bookingId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(COUNT(bs), 0) FROM BookingSeat bs WHERE bs.booking.user.id = :userId AND bs.booking.status = com.ticketrush.booking.entity.BookingStatus.CONFIRMED")
    long countConfirmedTicketsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
