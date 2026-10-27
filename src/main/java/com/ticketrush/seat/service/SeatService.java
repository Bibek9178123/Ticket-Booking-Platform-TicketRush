package com.ticketrush.seat.service;

import com.ticketrush.common.exception.ResourceNotFoundException;
import com.ticketrush.event.dto.TierRule;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.dto.SeatMapResponse;
import com.ticketrush.seat.dto.SeatResponse;
import com.ticketrush.seat.entity.EventSeat;
import com.ticketrush.seat.entity.SeatStatus;
import com.ticketrush.seat.repository.EventSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeatService {

    private final EventSeatRepository seatRepository;
    private final EventRepository eventRepository;

    public SeatService(EventSeatRepository seatRepository, EventRepository eventRepository) {
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void generateSeatsForEvent(Event event, List<TierRule> tiers) {
        // Delete any existing seats (in case of re-generation)
        seatRepository.deleteAllByEventId(event.getId());

        List<EventSeat> seats = new ArrayList<>();
        int rowCount = event.getRowCount();
        int seatsPerRow = event.getSeatsPerRow();

        for (int r = 1; r <= rowCount; r++) {
            String rowLabel = getRowLabel(r);
            TierRule matchingTier = findTierForRow(r, tiers);

            String tierName = matchingTier != null ? matchingTier.name() : "General";
            BigDecimal price = matchingTier != null ? matchingTier.price() : BigDecimal.valueOf(50.00);

            for (int s = 1; s <= seatsPerRow; s++) {
                seats.add(new EventSeat(event, rowLabel, s, tierName, price));
            }
        }

        seatRepository.saveAll(seats);
    }

    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        List<EventSeat> rawSeats = seatRepository.findSeatMapByEventId(eventId);
        LocalDateTime now = LocalDateTime.now();

        List<SeatResponse> seatResponses = rawSeats.stream().map(seat -> {
            SeatStatus effectiveStatus = seat.getStatus();
            // If the seat hold expired, it is effectively available to any customer!
            if (seat.getStatus() == SeatStatus.HELD &&
                    seat.getHoldExpiresAt() != null &&
                    seat.getHoldExpiresAt().isBefore(now)) {
                effectiveStatus = SeatStatus.AVAILABLE;
            }

            return new SeatResponse(
                    seat.getId(),
                    seat.getRowLabel(),
                    seat.getSeatNumber(),
                    seat.getTier(),
                    seat.getPrice(),
                    effectiveStatus
            );
        }).toList();

        return new SeatMapResponse(
                event.getId(),
                event.getTitle(),
                event.getRowCount(),
                event.getSeatsPerRow(),
                seatResponses
        );
    }

    private TierRule findTierForRow(int rowNumber, List<TierRule> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        for (TierRule tier : tiers) {
            if (rowNumber >= tier.rowStart() && rowNumber <= tier.rowEnd()) {
                return tier;
            }
        }
        // Default to first tier if not covered
        return tiers.get(0);
    }

    private String getRowLabel(int rowNumber) {
        StringBuilder sb = new StringBuilder();
        int n = rowNumber;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }
}
