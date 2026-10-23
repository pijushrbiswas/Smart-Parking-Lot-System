package com.parkinglot.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record Receipt(
        String ticketId,
        String licensePlate,
        VehicleType vehicleType,
        String spotId,
        Instant entryTime,
        Instant exitTime,
        Duration duration,
        BigDecimal fee) {

    public static Receipt from(ParkingTicket ticket) {
        Instant exit = ticket.exitTime().orElseThrow(() ->
                new IllegalStateException("Ticket " + ticket.id() + " is not closed"));
        return new Receipt(
                ticket.id(),
                ticket.vehicle().licensePlate(),
                ticket.vehicle().type(),
                ticket.spot().id(),
                ticket.entryTime(),
                exit,
                Duration.between(ticket.entryTime(), exit),
                ticket.fee().orElseThrow());
    }
}
