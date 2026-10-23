package com.parkinglot.service;

import com.parkinglot.allocation.BestFitAllocationStrategy;
import com.parkinglot.allocation.NearestFloorAllocationStrategy;
import com.parkinglot.allocation.SpotAllocationStrategy;
import com.parkinglot.event.ParkingEvent;
import com.parkinglot.event.ParkingEventPublisher;
import com.parkinglot.exception.ErrorCode;
import com.parkinglot.exception.NoAvailableSpotException;
import com.parkinglot.exception.ParkingException;
import com.parkinglot.exception.PricingException;
import com.parkinglot.exception.TicketAlreadyClosedException;
import com.parkinglot.exception.TicketNotFoundException;
import com.parkinglot.exception.VehicleAlreadyParkedException;
import com.parkinglot.exception.VehicleNotParkedException;
import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.Receipt;
import com.parkinglot.model.SpotSize;
import com.parkinglot.model.TicketStatus;
import com.parkinglot.model.Vehicle;
import com.parkinglot.model.VehicleType;
import com.parkinglot.pricing.FeeCalculator;
import com.parkinglot.pricing.HourlyFeeCalculator;
import com.parkinglot.repository.InMemoryTicketRepository;
import com.parkinglot.util.SimulatedClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultParkingServiceTest {

    private static final FeeCalculator HOURLY = new HourlyFeeCalculator(Map.of(
            VehicleType.MOTORCYCLE, new BigDecimal("10"),
            VehicleType.CAR, new BigDecimal("20"),
            VehicleType.BUS, new BigDecimal("50")), Duration.ofMinutes(10));

    private SimulatedClock clock;
    private ParkingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        clock = new SimulatedClock(Instant.parse("2026-01-01T10:00:00Z"));
        publisher = new ParkingEventPublisher();
    }

    private ParkingService service(ParkingLot lot) {
        return service(lot, new BestFitAllocationStrategy(), HOURLY);
    }

    private ParkingService service(ParkingLot lot, SpotAllocationStrategy strategy, FeeCalculator fees) {
        return new DefaultParkingService(lot, strategy, fees, new InMemoryTicketRepository(), publisher, clock);
    }

    private static Vehicle car(String plate) {
        return new Vehicle(plate, VehicleType.CAR);
    }

    @Test
    void assignsSmallestFittingSpot() {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 1, 1, 1).build());

        assertEquals(SpotSize.SMALL, svc.checkIn(new Vehicle("M1", VehicleType.MOTORCYCLE)).spot().size());
        assertEquals(SpotSize.MEDIUM, svc.checkIn(car("C1")).spot().size());
        assertEquals(SpotSize.LARGE, svc.checkIn(new Vehicle("B1", VehicleType.BUS)).spot().size());
    }

    @Test
    void fallsBackToLargerSpotWhenFittingSizeIsFull() {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 1, 1, 0).build());
        svc.checkIn(new Vehicle("M1", VehicleType.MOTORCYCLE));

        assertEquals(SpotSize.MEDIUM, svc.checkIn(new Vehicle("M2", VehicleType.MOTORCYCLE)).spot().size());
    }

    @Test
    void neverPutsLargerVehicleInSmallerSpot() {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 5, 5, 0).build());

        NoAvailableSpotException e = assertThrows(NoAvailableSpotException.class,
                () -> svc.checkIn(new Vehicle("B1", VehicleType.BUS)));
        assertEquals(ErrorCode.NO_SPOT_AVAILABLE, e.getErrorCode());
    }

    @Test
    void bestFitPrefersExactSizeOnHigherFloorButNearestFloorDoesNot() {
        // Floor 0 only has a large spot, floor 1 has a medium one.
        ParkingLot lot1 = ParkingLot.builder("L").addFloor(0, 0, 0, 1).addFloor(1, 0, 1, 0).build();
        ParkingLot lot2 = ParkingLot.builder("L").addFloor(0, 0, 0, 1).addFloor(1, 0, 1, 0).build();

        assertEquals("F1-M-001", service(lot1).checkIn(car("C1")).spot().id());
        assertEquals("F0-L-001",
                service(lot2, new NearestFloorAllocationStrategy(), HOURLY).checkIn(car("C1")).spot().id());
    }

    @Test
    void rejectsDuplicateCheckInEvenWithDifferentPlateFormatting() {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 3, 0).build());
        svc.checkIn(car("KA01AB1234"));

        assertThrows(VehicleAlreadyParkedException.class, () -> svc.checkIn(car("ka01 ab1234")));
        assertEquals(2, svc.getAvailability().getFirst().available().get(SpotSize.MEDIUM),
                "failed duplicate check-in must not consume a spot");
    }

    @Test
    void checkOutCalculatesFeeAndFreesSpot() {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 1, 0).build());
        ParkingTicket ticket = svc.checkIn(car("C1"));
        clock.advance(Duration.ofMinutes(61));

        Receipt receipt = svc.checkOut(ticket.id());

        assertEquals(new BigDecimal("40.00"), receipt.fee());
        assertEquals(Duration.ofMinutes(61), receipt.duration());
        assertEquals(TicketStatus.CLOSED, ticket.status());
        assertEquals(1, svc.getAvailability().getFirst().available().get(SpotSize.MEDIUM));
        assertDoesNotThrow(() -> svc.checkIn(car("C1")), "vehicle may re-enter after leaving");
    }

    @Test
    void checkOutErrors() {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 1, 0).build());
        ParkingTicket ticket = svc.checkIn(car("C1"));
        svc.checkOut(ticket.id());

        assertThrows(TicketAlreadyClosedException.class, () -> svc.checkOut(ticket.id()));
        assertThrows(TicketNotFoundException.class, () -> svc.checkOut("nope"));
        assertThrows(TicketNotFoundException.class, () -> svc.checkOut(null));
        assertThrows(VehicleNotParkedException.class, () -> svc.checkOutByLicensePlate("C1"));
    }

    @Test
    void lostTicketCheckOutByPlate() {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 1, 0).build());
        ParkingTicket ticket = svc.checkIn(car("KA01AB1234"));

        assertEquals(ticket.id(), svc.checkOutByLicensePlate(" ka01ab1234 ").ticketId());
    }

    @Test
    void pricingFailureLeavesSessionIntact() {
        AtomicInteger calls = new AtomicInteger();
        FeeCalculator flaky = (type, d) -> {
            if (calls.getAndIncrement() == 0) throw new PricingException("rate service down");
            return BigDecimal.TEN;
        };
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 1, 0).build(),
                new BestFitAllocationStrategy(), flaky);
        ParkingTicket ticket = svc.checkIn(car("C1"));

        assertThrows(PricingException.class, () -> svc.checkOut(ticket.id()));
        assertEquals(TicketStatus.ACTIVE, ticket.status());
        assertEquals(BigDecimal.TEN, svc.checkOut(ticket.id()).fee());
    }

    @Test
    void failingListenerDoesNotBreakFlow() {
        List<ParkingEvent> received = new ArrayList<>();
        publisher.subscribe(e -> { throw new RuntimeException("display offline"); });
        publisher.subscribe(received::add);
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 1, 0).build());

        ParkingTicket ticket = svc.checkIn(car("C1"));
        svc.checkOut(ticket.id());

        assertEquals(2, received.size());
        assertInstanceOf(ParkingEvent.VehicleEntered.class, received.get(0));
        assertInstanceOf(ParkingEvent.VehicleExited.class, received.get(1));
    }

    @Test
    void concurrentCheckInsNeverOverbook() throws Exception {
        int spots = 25;
        int vehicles = 200;
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 10, 0).addFloor(1, 0, 15, 0).build());
        Set<String> assignedSpots = ConcurrentHashMap.newKeySet();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < vehicles; i++) {
                String plate = "CAR" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        assertTrue(assignedSpots.add(svc.checkIn(car(plate)).spot().id()), "spot double-booked");
                    } catch (NoAvailableSpotException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get();
        }

        assertEquals(spots, assignedSpots.size());
        assertEquals(vehicles - spots, rejected.get());
        assertTrue(svc.getAvailability().stream().allMatch(a -> a.available().get(SpotSize.MEDIUM) == 0));
    }

    @Test
    void concurrentCheckInOfSameVehicleAdmitsOnlyOnce() throws Exception {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 50, 0).build());
        AtomicInteger admitted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        svc.checkIn(car("SAME1"));
                        admitted.incrementAndGet();
                    } catch (VehicleAlreadyParkedException ignored) {
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get();
        }

        assertEquals(1, admitted.get());
        assertEquals(49, svc.getAvailability().getFirst().available().get(SpotSize.MEDIUM));
    }

    @Test
    void concurrentCheckOutOfSameTicketSucceedsOnce() throws Exception {
        ParkingService svc = service(ParkingLot.builder("L").addFloor(0, 0, 1, 0).build());
        ParkingTicket ticket = svc.checkIn(car("C1"));
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        svc.checkOut(ticket.id());
                        succeeded.incrementAndGet();
                    } catch (ParkingException e) {
                        assertEquals(ErrorCode.TICKET_ALREADY_CLOSED, e.getErrorCode());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get();
        }

        assertEquals(1, succeeded.get());
        assertEquals(1, svc.getAvailability().getFirst().available().get(SpotSize.MEDIUM));
    }
}
