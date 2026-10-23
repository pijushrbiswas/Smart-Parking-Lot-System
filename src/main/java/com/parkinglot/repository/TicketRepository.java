package com.parkinglot.repository;

import com.parkinglot.model.ParkingTicket;

import java.util.List;
import java.util.Optional;

/** Persistence port for parking sessions. Swap the in-memory adapter for a JDBC/JPA one. */
public interface TicketRepository {

    void save(ParkingTicket ticket);

    Optional<ParkingTicket> findById(String ticketId);

    List<ParkingTicket> findAll();
}
