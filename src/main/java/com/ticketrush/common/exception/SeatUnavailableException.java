package com.ticketrush.common.exception;

import java.util.List;

public class SeatUnavailableException extends RuntimeException {

    private final List<Long> unavailableSeatIds;

    public SeatUnavailableException(String message) {
        super(message);
        this.unavailableSeatIds = List.of();
    }

    public SeatUnavailableException(String message, List<Long> unavailableSeatIds) {
        super(message);
        this.unavailableSeatIds = unavailableSeatIds;
    }

    public List<Long> getUnavailableSeatIds() {
        return unavailableSeatIds;
    }
}
