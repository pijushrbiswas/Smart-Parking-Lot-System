package com.parkinglot.repository;

import com.parkinglot.model.ParkingTicket;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTicketRepository implements TicketRepository {

    private final Map<String, ParkingTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public void save(ParkingTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        tickets.put(ticket.id(), ticket);
    }

    @Override
    public Optional<ParkingTicket> findById(String ticketId) {
        return ticketId == null ? Optional.empty() : Optional.ofNullable(tickets.get(ticketId));
    }

    @Override
    public List<ParkingTicket> findAll() {
        return List.copyOf(tickets.values());
    }
}
