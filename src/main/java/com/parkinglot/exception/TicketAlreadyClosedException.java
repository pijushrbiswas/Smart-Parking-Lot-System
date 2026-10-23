package com.parkinglot.exception;

public class TicketAlreadyClosedException extends ParkingException {
    public TicketAlreadyClosedException(String ticketId) {
        super(ErrorCode.TICKET_ALREADY_CLOSED, "Ticket already closed: " + ticketId);
    }
}
