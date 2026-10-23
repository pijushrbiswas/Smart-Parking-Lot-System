package com.parkinglot.model;

import com.parkinglot.exception.InvalidConfigurationException;
import com.parkinglot.exception.InvalidVehicleException;
import com.parkinglot.exception.TicketAlreadyClosedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void vehicleValidationAndNormalisation() {
        assertEquals("KA01AB1234", new Vehicle(" ka01 ab 1234 ", VehicleType.CAR).licensePlate());
        assertThrows(InvalidVehicleException.class, () -> new Vehicle(null, VehicleType.CAR));
        assertThrows(InvalidVehicleException.class, () -> new Vehicle("  ", VehicleType.CAR));
        assertThrows(InvalidVehicleException.class, () -> new Vehicle("AB#12", VehicleType.CAR));
        assertThrows(InvalidVehicleException.class, () -> new Vehicle("AB12", null));
    }

    @Test
    void spotSizeFitting() {
        assertEquals(List.of(SpotSize.SMALL, SpotSize.MEDIUM, SpotSize.LARGE), SpotSize.sizesFitting(SpotSize.SMALL));
        assertEquals(List.of(SpotSize.LARGE), SpotSize.sizesFitting(SpotSize.LARGE));
    }

    @Test
    void builderValidation() {
        assertThrows(InvalidConfigurationException.class, () -> ParkingLot.builder("L").build());
        assertThrows(InvalidConfigurationException.class, () -> ParkingLot.builder(" ").addFloor(0, 1, 1, 1).build());
        assertThrows(InvalidConfigurationException.class, () -> ParkingLot.builder("L").addFloor(0, 0, 0, 0).build());
        assertThrows(InvalidConfigurationException.class, () -> ParkingLot.builder("L").addFloor(0, -1, 1, 1));
        assertThrows(InvalidConfigurationException.class,
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
        assertThrows(TicketAlreadyClosedException.class, () -> ticket.close(entry.plusSeconds(120), BigDecimal.ONE));
    }
}
