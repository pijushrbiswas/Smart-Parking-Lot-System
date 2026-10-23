package com.parkinglot.demo;

import com.parkinglot.allocation.BestFitAllocationStrategy;
import com.parkinglot.event.AvailabilityDisplayBoard;
import com.parkinglot.event.ParkingEventPublisher;
import com.parkinglot.exception.ParkingException;
import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.Receipt;
import com.parkinglot.model.Vehicle;
import com.parkinglot.model.VehicleType;
import com.parkinglot.pricing.DailyCapFeeCalculator;
import com.parkinglot.pricing.FeeCalculator;
import com.parkinglot.pricing.HourlyFeeCalculator;
import com.parkinglot.repository.InMemoryTicketRepository;
import com.parkinglot.service.DefaultParkingService;
import com.parkinglot.service.ParkingService;
import com.parkinglot.util.SimulatedClock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Composition root + walkthrough of the main flows. */
public class Main {

    public static void main(String[] args) throws Exception {
        ParkingLot lot = ParkingLot.builder("Downtown Central")
                .addFloor(0, 2, 3, 1)
                .addFloor(1, 2, 3, 1)
                .build();

        FeeCalculator pricing = new DailyCapFeeCalculator(
                new HourlyFeeCalculator(Map.of(
                        VehicleType.MOTORCYCLE, new BigDecimal("10"),
                        VehicleType.CAR, new BigDecimal("20"),
                        VehicleType.BUS, new BigDecimal("50")),
                        Duration.ofMinutes(10)),
                Map.of(
                        VehicleType.MOTORCYCLE, new BigDecimal("80"),
                        VehicleType.CAR, new BigDecimal("150"),
                        VehicleType.BUS, new BigDecimal("400")));

        ParkingEventPublisher publisher = new ParkingEventPublisher();
        publisher.subscribe(new AvailabilityDisplayBoard(lot, System.out));

        SimulatedClock clock = new SimulatedClock(Instant.parse("2026-10-22T08:00:00Z"));
        ParkingService service = new DefaultParkingService(
                lot, new BestFitAllocationStrategy(), pricing, new InMemoryTicketRepository(), publisher, clock);

        System.out.println("== Check-in ==");
        ParkingTicket car = service.checkIn(new Vehicle("KA01AB1234", VehicleType.CAR));
        ParkingTicket bike = service.checkIn(new Vehicle("KA02CD5678", VehicleType.MOTORCYCLE));
        ParkingTicket bus = service.checkIn(new Vehicle("KA03EF9012", VehicleType.BUS));
        List.of(car, bike, bus).forEach(t -> System.out.println("  " + t));

        System.out.println("\n== Error handling ==");
        attempt("duplicate entry", () -> service.checkIn(new Vehicle("ka01 ab1234", VehicleType.CAR)));
        attempt("unknown ticket", () -> service.checkOut("does-not-exist"));
        attempt("invalid plate", () -> new Vehicle("  ", VehicleType.CAR));

        System.out.println("\n== Check-out after 2h 15m ==");
        clock.advance(Duration.ofMinutes(135));
        print(service.checkOut(car.id()));
        print(service.checkOutByLicensePlate("KA02CD5678"));
        attempt("double check-out", () -> service.checkOut(car.id()));

        System.out.println("\n== Bus checks out after 30h (daily cap applies) ==");
        clock.advance(Duration.ofHours(28).minusMinutes(15));
        print(service.checkOut(bus.id()));

        System.out.println("\n== Concurrency: 30 cars race for 8 car-capable spots ==");
        concurrentRush(service, 30);

        System.out.println("\n== Final availability ==");
        service.getAvailability().forEach(a -> System.out.println("  " + a));
    }

    private static void concurrentRush(ParkingService service, int cars) throws Exception {
        AtomicInteger parked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < cars; i++) {
                String plate = "RUSH%03d".formatted(i);
                futures.add(pool.submit(() -> {
                    try {
                        service.checkIn(new Vehicle(plate, VehicleType.CAR));
                        parked.incrementAndGet();
                    } catch (ParkingException e) {
                        rejected.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : futures) f.get();
        }
        System.out.printf("  parked=%d rejected=%d%n", parked.get(), rejected.get());
    }

    private static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": unexpectedly succeeded");
        } catch (ParkingException e) {
            System.out.printf("  %s -> %s: %s%n", label, e.getErrorCode(), e.getMessage());
        }
    }

    private static void print(Receipt r) {
        System.out.printf("  Receipt %s | %s (%s) | spot %s | %d min | fee %s%n",
                r.ticketId().substring(0, 8), r.licensePlate(), r.vehicleType(), r.spotId(),
                r.duration().toMinutes(), r.fee());
    }
}
