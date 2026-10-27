package com.ticketrush.seat.repository;

import com.ticketrush.seat.entity.EventSeat;
import com.ticketrush.seat.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    List<EventSeat> findAllByEventIdOrderByRowLabelAscSeatNumberAsc(Long eventId);

    long countByEventId(Long eventId);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);

    void deleteAllByEventId(Long eventId);

    @Query("SELECT s FROM EventSeat s WHERE s.event.id = :eventId ORDER BY s.rowLabel ASC, s.seatNumber ASC")
    List<EventSeat> findSeatMapByEventId(@Param("eventId") Long eventId);

    /**
     * ATOMIC CONDITIONAL UPDATE:
     * Attempts to atomically lock and hold all requested seat IDs.
     * Matches ONLY seats that are currently AVAILABLE or HELD with an expired hold timestamp.
     * Returns the exact number of rows updated in MySQL.
     */
    @Modifying
    @Query("UPDATE EventSeat s " +
            "SET s.status = com.ticketrush.seat.entity.SeatStatus.HELD, " +
            "    s.bookingId = :bookingId, " +
            "    s.holdExpiresAt = :expiresAt " +
            "WHERE s.id IN :seatIds " +
            "  AND (s.status = com.ticketrush.seat.entity.SeatStatus.AVAILABLE " +
            "       OR (s.status = com.ticketrush.seat.entity.SeatStatus.HELD AND s.holdExpiresAt < :now))")
    int holdSeatsAtomic(
            @Param("seatIds") List<Long> seatIds,
            @Param("bookingId") Long bookingId,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    /**
     * Batch releases all expired held seats back to AVAILABLE.
     */
    @Modifying
    @Query("UPDATE EventSeat s " +
            "SET s.status = com.ticketrush.seat.entity.SeatStatus.AVAILABLE, " +
            "    s.bookingId = NULL, " +
            "    s.holdExpiresAt = NULL " +
            "WHERE s.status = com.ticketrush.seat.entity.SeatStatus.HELD " +
            "  AND s.holdExpiresAt < :now")
    int releaseExpiredHolds(@Param("now") LocalDateTime now);

    /**
     * Releases seats linked to a specific booking ID.
     */
    @Modifying
    @Query("UPDATE EventSeat s " +
            "SET s.status = com.ticketrush.seat.entity.SeatStatus.AVAILABLE, " +
            "    s.bookingId = NULL, " +
            "    s.holdExpiresAt = NULL " +
            "WHERE s.bookingId = :bookingId")
    int releaseSeatsByBookingId(@Param("bookingId") Long bookingId);

    /**
     * Transitions held seats to permanently BOOKED.
     */
    @Modifying
    @Query("UPDATE EventSeat s " +
            "SET s.status = com.ticketrush.seat.entity.SeatStatus.BOOKED, " +
            "    s.holdExpiresAt = NULL " +
            "WHERE s.bookingId = :bookingId")
    int confirmSeatsByBookingId(@Param("bookingId") Long bookingId);
}
