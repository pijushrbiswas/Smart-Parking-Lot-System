package com.parkinglot.event;

import com.parkinglot.model.FloorAvailability;
import com.parkinglot.model.ParkingLot;

import java.io.PrintStream;

/** Example listener: prints the affected floor's free-spot counts on every change. */
public class AvailabilityDisplayBoard implements ParkingEventListener {

    private final ParkingLot lot;
    private final PrintStream out;

    public AvailabilityDisplayBoard(ParkingLot lot, PrintStream out) {
        this.lot = lot;
        this.out = out;
    }

    @Override
    public void onEvent(ParkingEvent event) {
        lot.floors().stream()
                .filter(f -> f.level() == event.floorLevel())
                .findFirst()
                .map(FloorAvailability::of)
                .ifPresent(a -> out.printf("  [Display] Floor %d free -> %s%n", a.level(), a.available()));
    }
}
