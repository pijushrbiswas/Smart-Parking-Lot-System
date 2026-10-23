package com.parkinglot.model;

import com.parkinglot.exception.ErrorCode;
import com.parkinglot.exception.ParkingException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A parking session (the "transaction"). Identity and entry data are immutable;
 * the exit side is written exactly once via {@link #close}.
 */
public final class ParkingTicket {

    private final String id;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final Instant entryTime;

    private volatile TicketStatus status = TicketStatus.ACTIVE;
    private volatile Instant exitTime;
    private volatile BigDecimal fee;

    public ParkingTicket(String id, Vehicle vehicle, ParkingSpot spot, Instant entryTime) {
        this.id = Objects.requireNonNull(id, "id");
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
        this.spot = Objects.requireNonNull(spot, "spot");
        this.entryTime = Objects.requireNonNull(entryTime, "entryTime");
    }

    public synchronized void close(Instant exitTime, BigDecimal fee) {
        Objects.requireNonNull(exitTime, "exitTime");
        Objects.requireNonNull(fee, "fee");
        if (status == TicketStatus.CLOSED) {
            throw new ParkingException(ErrorCode.TICKET_ALREADY_CLOSED, "Ticket already closed: " + id);
        }
        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("Exit time " + exitTime + " is before entry time " + entryTime);
        }
        this.exitTime = exitTime;
        this.fee = fee;
        this.status = TicketStatus.CLOSED;
    }

    public String id() {
        return id;
    }

    public Vehicle vehicle() {
        return vehicle;
    }

    public ParkingSpot spot() {
        return spot;
    }

    public Instant entryTime() {
        return entryTime;
    }

    public TicketStatus status() {
        return status;
    }

    public Optional<Instant> exitTime() {
        return Optional.ofNullable(exitTime);
    }

    public Optional<BigDecimal> fee() {
        return Optional.ofNullable(fee);
    }

    @Override
    public String toString() {
        return "Ticket[%s, %s, spot=%s, entry=%s, status=%s]".formatted(
                id, vehicle.licensePlate(), spot.id(), entryTime, status);
    }
}
