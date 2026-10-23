package com.parkinglot.exception;

public class TicketNotFoundException extends ParkingException {
    public TicketNotFoundException(String ticketId) {
        super(ErrorCode.TICKET_NOT_FOUND, "Ticket not found: " + ticketId);
    }
}
