package com.parkinglot.model;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single physical spot. Identity is immutable; occupancy is guarded by a CAS
 * so an invariant violation (double occupy / double vacate) fails loudly
 * instead of silently corrupting state.
 */
public final class ParkingSpot {

    private final String id;
    private final int floorLevel;
    private final int number;
    private final SpotSize size;
    private final AtomicBoolean occupied = new AtomicBoolean(false);

    public ParkingSpot(int floorLevel, int number, SpotSize size) {
        this.floorLevel = floorLevel;
        this.number = number;
        this.size = size;
        this.id = "F%d-%s-%03d".formatted(floorLevel, size.name().charAt(0), number);
    }

    public String id() {
        return id;
    }

    public int floorLevel() {
        return floorLevel;
    }

    public int number() {
        return number;
    }

    public SpotSize size() {
        return size;
    }

    public boolean isOccupied() {
        return occupied.get();
    }

    void occupy() {
        if (!occupied.compareAndSet(false, true)) {
            throw new IllegalStateException("Spot " + id + " is already occupied");
        }
    }

    void vacate() {
        if (!occupied.compareAndSet(true, false)) {
            throw new IllegalStateException("Spot " + id + " is not occupied");
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ParkingSpot other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
