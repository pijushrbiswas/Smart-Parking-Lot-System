package com.parkinglot.service;

import com.parkinglot.allocation.SpotAllocationStrategy;
import com.parkinglot.event.ParkingEvent;
import com.parkinglot.event.ParkingEventPublisher;
import com.parkinglot.exception.ErrorCode;
import com.parkinglot.exception.ParkingException;
import com.parkinglot.model.FloorAvailability;
import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.Receipt;
import com.parkinglot.model.Vehicle;
import com.parkinglot.pricing.FeeCalculator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates check-in / check-out. All collaborators are injected as
 * abstractions (dependency inversion), so allocation, pricing and notification
 * can each change independently. Tickets are held in memory; schema.sql shows the
 * relational model a persistent deployment would use.
 *
 * <h2>Concurrency</h2>
 * <ul>
 *   <li>Spot allocation is lock-free: each free list hands out a spot to exactly one caller.</li>
 *   <li>{@code activeSessions} (plate -> ticket) is the single gate for "one active session per
 *       vehicle". Check-in uses {@code compute}, which is atomic per key, so two gates scanning the
 *       same plate cannot both admit it. Check-out uses {@code remove(key, value)}, so exactly one
 *       of several concurrent exits for the same ticket wins.</li>
 *   <li>In a DB-backed deployment these map to a unique partial index on
 *       {@code parking_ticket(vehicle_id) WHERE status = 'ACTIVE'} and
 *       {@code SELECT ... FOR UPDATE SKIP LOCKED} on spots (see schema.sql).</li>
 * </ul>
 */
public class DefaultParkingService implements ParkingService {

    private final ParkingLot lot;
    private final SpotAllocationStrategy allocationStrategy;
    private final FeeCalculator feeCalculator;
    private final ParkingEventPublisher eventPublisher;
    private final Clock clock;

    private final ConcurrentHashMap<String, ParkingTicket> ticketsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ParkingTicket> activeSessions = new ConcurrentHashMap<>();

    /** The clock is injected because fees depend on elapsed time; tests and the demo advance it explicitly. */
    public DefaultParkingService(ParkingLot lot,
                                 SpotAllocationStrategy allocationStrategy,
                                 FeeCalculator feeCalculator,
                                 ParkingEventPublisher eventPublisher,
                                 Clock clock) {
        this.lot = Objects.requireNonNull(lot, "lot");
        this.allocationStrategy = Objects.requireNonNull(allocationStrategy, "allocationStrategy");
        this.feeCalculator = Objects.requireNonNull(feeCalculator, "feeCalculator");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ParkingTicket checkIn(Vehicle vehicle) {
        if (vehicle == null) {
            throw new ParkingException(ErrorCode.INVALID_VEHICLE, "Vehicle must not be null");
        }
        // Exceptions thrown inside compute() propagate and leave the map unchanged.
        ParkingTicket ticket = activeSessions.compute(vehicle.licensePlate(), (plate, existing) -> {
            if (existing != null) {
                throw new ParkingException(ErrorCode.VEHICLE_ALREADY_PARKED,
                        "Vehicle " + plate + " is already parked at spot " + existing.spot().id());
            }
            ParkingSpot spot = allocationStrategy.allocate(lot, vehicle.type())
                    .orElseThrow(() -> new ParkingException(ErrorCode.NO_SPOT_AVAILABLE,
                            "No available spot for vehicle type " + vehicle.type()));
            ParkingTicket created = new ParkingTicket(UUID.randomUUID().toString(), vehicle, spot, clock.instant());
            ticketsById.put(created.id(), created);
            return created;
        });
        eventPublisher.publish(new ParkingEvent.VehicleEntered(ticket));
        return ticket;
    }

    @Override
    public Receipt checkOut(String ticketId) {
        ParkingTicket ticket = ticketId == null ? null : ticketsById.get(ticketId);
        if (ticket == null) {
            throw new ParkingException(ErrorCode.TICKET_NOT_FOUND, "Ticket not found: " + ticketId);
        }

        // Price before mutating anything: if pricing fails, the session stays intact and can be retried.
        Instant exitTime = clock.instant();
        BigDecimal fee = feeCalculator.calculate(
                ticket.vehicle().type(), Duration.between(ticket.entryTime(), exitTime));

        // Atomic gate: only one concurrent caller can end this session.
        if (!activeSessions.remove(ticket.vehicle().licensePlate(), ticket)) {
            throw new ParkingException(ErrorCode.TICKET_ALREADY_CLOSED, "Ticket already closed: " + ticketId);
        }
        ticket.close(exitTime, fee);
        lot.release(ticket.spot());

        Receipt receipt = Receipt.from(ticket);
        eventPublisher.publish(new ParkingEvent.VehicleExited(receipt, ticket.spot().floorLevel()));
        return receipt;
    }

    @Override
    public Receipt checkOutByLicensePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new ParkingException(ErrorCode.INVALID_VEHICLE, "License plate must not be blank");
        }
        String plate = Vehicle.normalise(licensePlate);
        ParkingTicket ticket = activeSessions.get(plate);
        if (ticket == null) {
            throw new ParkingException(ErrorCode.VEHICLE_NOT_PARKED, "No active parking session for vehicle " + plate);
        }
        return checkOut(ticket.id());
    }

    @Override
    public List<FloorAvailability> getAvailability() {
        return lot.floors().stream().map(FloorAvailability::of).toList();
    }
}
