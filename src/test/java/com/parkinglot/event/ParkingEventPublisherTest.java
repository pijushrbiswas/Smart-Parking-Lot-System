package com.parkinglot.event;

import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.SpotSize;
import com.parkinglot.model.Vehicle;
import com.parkinglot.model.VehicleType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParkingEventPublisherTest {

    @Test
    void deliversToEveryListenerInSubscriptionOrder() {
        ParkingEventPublisher publisher = new ParkingEventPublisher();
        List<String> calls = new ArrayList<>();
        publisher.subscribe(e -> calls.add("a"));
        publisher.subscribe(e -> calls.add("b"));

        publisher.publish(entered(new ParkingSpot(0, 1, SpotSize.MEDIUM)));

        assertEquals(List.of("a", "b"), calls);
    }

    @Test
    void rejectsNullListener() {
        assertThrows(NullPointerException.class, () -> new ParkingEventPublisher().subscribe(null));
    }

    @Test
    void displayBoardPrintsOnlyTheAffectedFloor() {
        ParkingLot lot = ParkingLot.builder("L").addFloor(0, 1, 0, 0).addFloor(1, 0, 2, 0).build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AvailabilityDisplayBoard board = new AvailabilityDisplayBoard(lot, new PrintStream(out, true));
        ParkingSpot spot = lot.floors().get(1).tryAcquire(SpotSize.MEDIUM).orElseThrow();

        board.onEvent(entered(spot));

        String printed = out.toString();
        assertTrue(printed.contains("Floor 1"), printed);
        assertTrue(printed.contains("MEDIUM=1"), printed);
        assertFalse(printed.contains("Floor 0"), printed);
    }

    private static ParkingEvent entered(ParkingSpot spot) {
        return new ParkingEvent.VehicleEntered(
                new ParkingTicket("t1", new Vehicle("C1", VehicleType.CAR), spot, Instant.EPOCH));
    }
}
