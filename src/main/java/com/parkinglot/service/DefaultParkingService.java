package com.parkinglot.service;

import com.parkinglot.allocation.SpotAllocationStrategy;
import com.parkinglot.event.ParkingEvent;
import com.parkinglot.event.ParkingEventPublisher;
import com.parkinglot.exception.InvalidVehicleException;
import com.parkinglot.exception.NoAvailableSpotException;
import com.parkinglot.exception.TicketAlreadyClosedException;
import com.parkinglot.exception.TicketNotFoundException;
import com.parkinglot.exception.VehicleAlreadyParkedException;
import com.parkinglot.exception.VehicleNotParkedException;
import com.parkinglot.model.FloorAvailability;
import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.Receipt;
import com.parkinglot.model.Vehicle;
import com.parkinglot.pricing.FeeCalculator;
import com.parkinglot.repository.TicketRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Orchestrates check-in / check-out. All collaborators are injected as
 * abstractions (dependency inversion), so allocation, pricing, storage and
 * notification can each change independently.
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
    private final TicketRepository ticketRepository;
    private final ParkingEventPublisher eventPublisher;
    private final Clock clock;
    private final Supplier<String> ticketIdGenerator;

    private final ConcurrentHashMap<String, ParkingTicket> activeSessions = new ConcurrentHashMap<>();

    public DefaultParkingService(ParkingLot lot,
                                 SpotAllocationStrategy allocationStrategy,
                                 FeeCalculator feeCalculator,
                                 TicketRepository ticketRepository,
                                 ParkingEventPublisher eventPublisher,
                                 Clock clock) {
        this(lot, allocationStrategy, feeCalculator, ticketRepository, eventPublisher, clock,
                () -> UUID.randomUUID().toString());
    }

    public DefaultParkingService(ParkingLot lot,
                                 SpotAllocationStrategy allocationStrategy,
                                 FeeCalculator feeCalculator,
                                 TicketRepository ticketRepository,
                                 ParkingEventPublisher eventPublisher,
                                 Clock clock,
                                 Supplier<String> ticketIdGenerator) {
        this.lot = Objects.requireNonNull(lot, "lot");
        this.allocationStrategy = Objects.requireNonNull(allocationStrategy, "allocationStrategy");
        this.feeCalculator = Objects.requireNonNull(feeCalculator, "feeCalculator");
        this.ticketRepository = Objects.requireNonNull(ticketRepository, "ticketRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ticketIdGenerator = Objects.requireNonNull(ticketIdGenerator, "ticketIdGenerator");
    }

    @Override
    public ParkingTicket checkIn(Vehicle vehicle) {
        if (vehicle == null) {
            throw new InvalidVehicleException("Vehicle must not be null");
        }
        // Exceptions thrown inside compute() propagate and leave the map unchanged.
        ParkingTicket ticket = activeSessions.compute(vehicle.licensePlate(), (plate, existing) -> {
            if (existing != null) {
                throw new VehicleAlreadyParkedException(plate, existing.spot().id());
            }
            ParkingSpot spot = allocationStrategy.allocate(lot, vehicle.type())
                    .orElseThrow(() -> new NoAvailableSpotException(vehicle.type()));
            try {
                ParkingTicket created = new ParkingTicket(ticketIdGenerator.get(), vehicle, spot, clock.instant());
                ticketRepository.save(created);
                return created;
            } catch (RuntimeException e) {
                lot.release(spot); // compensate: never leak a reserved spot
                throw e;
            }
        });
        eventPublisher.publish(new ParkingEvent.VehicleEntered(ticket));
        return ticket;
    }

    @Override
    public Receipt checkOut(String ticketId) {
        ParkingTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        // Price before mutating anything: if pricing fails, the session stays intact and can be retried.
        Instant exitTime = clock.instant();
        BigDecimal fee = feeCalculator.calculate(
                ticket.vehicle().type(), Duration.between(ticket.entryTime(), exitTime));

        // Atomic gate: only one concurrent caller can end this session.
        if (!activeSessions.remove(ticket.vehicle().licensePlate(), ticket)) {
            throw new TicketAlreadyClosedException(ticketId);
        }
        ticket.close(exitTime, fee);
        ticketRepository.save(ticket);
        lot.release(ticket.spot());

        Receipt receipt = Receipt.from(ticket);
        eventPublisher.publish(new ParkingEvent.VehicleExited(receipt, ticket.spot().floorLevel()));
        return receipt;
    }

    @Override
    public Receipt checkOutByLicensePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new InvalidVehicleException("License plate must not be blank");
        }
        String plate = Vehicle.normalise(licensePlate);
        ParkingTicket ticket = activeSessions.get(plate);
        if (ticket == null) {
            throw new VehicleNotParkedException(plate);
        }
        return checkOut(ticket.id());
    }

    @Override
    public List<FloorAvailability> getAvailability() {
        return lot.floors().stream().map(FloorAvailability::of).toList();
    }
}
