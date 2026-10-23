package com.parkinglot.model;

import com.parkinglot.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.parkinglot.ParkingAssertions.assertError;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void vehicleValidationAndNormalisation() {
        assertEquals("KA01AB1234", new Vehicle(" ka01 ab 1234 ", VehicleType.CAR).licensePlate());
        assertError(ErrorCode.INVALID_VEHICLE, () -> new Vehicle(null, VehicleType.CAR));
        assertError(ErrorCode.INVALID_VEHICLE, () -> new Vehicle("  ", VehicleType.CAR));
        assertError(ErrorCode.INVALID_VEHICLE, () -> new Vehicle("AB#12", VehicleType.CAR));
        assertError(ErrorCode.INVALID_VEHICLE, () -> new Vehicle("AB12", null));
    }

    @Test
    void spotSizeFitting() {
        assertEquals(List.of(SpotSize.SMALL, SpotSize.MEDIUM, SpotSize.LARGE), SpotSize.sizesFitting(SpotSize.SMALL));
        assertEquals(List.of(SpotSize.LARGE), SpotSize.sizesFitting(SpotSize.LARGE));
    }

    @Test
    void builderValidation() {
        assertError(ErrorCode.INVALID_CONFIGURATION, () -> ParkingLot.builder("L").build());
        assertError(ErrorCode.INVALID_CONFIGURATION, () -> ParkingLot.builder(" ").addFloor(0, 1, 1, 1).build());
        assertError(ErrorCode.INVALID_CONFIGURATION, () -> ParkingLot.builder("L").addFloor(0, 0, 0, 0).build());
        assertError(ErrorCode.INVALID_CONFIGURATION, () -> ParkingLot.builder("L").addFloor(0, -1, 1, 1));
        assertError(ErrorCode.INVALID_CONFIGURATION,
                () -> ParkingLot.builder("L").addFloor(0, 1, 1, 1).addFloor(0, 1, 1, 1));
    }

    @Test
    void floorAcquireAndRelease() {
        ParkingFloor floor = ParkingLot.builder("L").addFloor(2, 0, 2, 0).build().floors().getFirst();

        ParkingSpot first = floor.tryAcquire(SpotSize.MEDIUM).orElseThrow();
        assertEquals("F2-M-001", first.id());
        assertTrue(first.isOccupied());
        assertEquals(1, floor.availableCount(SpotSize.MEDIUM));
        assertTrue(floor.tryAcquire(SpotSize.LARGE).isEmpty());

        floor.release(first);
        assertFalse(first.isOccupied());
        assertThrows(IllegalStateException.class, () -> floor.release(first), "double release must fail");
        assertThrows(IllegalArgumentException.class, () -> floor.release(new ParkingSpot(9, 1, SpotSize.MEDIUM)));
    }

    @Test
    void ticketClosesOnlyOnce() {
        Instant entry = Instant.parse("2026-01-01T10:00:00Z");
        ParkingTicket ticket = new ParkingTicket("t1", new Vehicle("C1", VehicleType.CAR),
                new ParkingSpot(0, 1, SpotSize.MEDIUM), entry);

        assertThrows(IllegalArgumentException.class, () -> ticket.close(entry.minusSeconds(1), BigDecimal.ONE));
        ticket.close(entry.plusSeconds(60), BigDecimal.ONE);
        assertError(ErrorCode.TICKET_ALREADY_CLOSED, () -> ticket.close(entry.plusSeconds(120), BigDecimal.ONE));
    }
}
