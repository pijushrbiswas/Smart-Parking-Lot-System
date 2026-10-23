package com.parkinglot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * A floor keeps one lock-free free-list per spot size. {@code pollFirst()} on a
 * {@link ConcurrentSkipListSet} atomically removes and returns the lowest-numbered
 * free spot, so two concurrent callers can never receive the same spot, and
 * acquisition is O(log n) rather than a linear scan.
 */
public final class ParkingFloor {

    private static final Comparator<ParkingSpot> BY_NUMBER = Comparator.comparingInt(ParkingSpot::number);

    private final int level;
    private final List<ParkingSpot> spots;
    private final Map<SpotSize, NavigableSet<ParkingSpot>> freeSpots = new EnumMap<>(SpotSize.class);

    ParkingFloor(int level, List<ParkingSpot> spots) {
        this.level = level;
        this.spots = Collections.unmodifiableList(new ArrayList<>(spots));
        for (SpotSize size : SpotSize.values()) {
            freeSpots.put(size, new ConcurrentSkipListSet<>(BY_NUMBER));
        }
        spots.forEach(spot -> freeSpots.get(spot.size()).add(spot));
    }

    public int level() {
        return level;
    }

    public List<ParkingSpot> spots() {
        return spots;
    }

    public Optional<ParkingSpot> tryAcquire(SpotSize size) {
        ParkingSpot spot = freeSpots.get(size).pollFirst();
        if (spot != null) {
            spot.occupy();
        }
        return Optional.ofNullable(spot);
    }

    public void release(ParkingSpot spot) {
        if (spot.floorLevel() != level) {
            throw new IllegalArgumentException("Spot " + spot.id() + " does not belong to floor " + level);
        }
        // Vacate before re-publishing to the free list so a spot is never handed out while marked occupied.
        spot.vacate();
        freeSpots.get(spot.size()).add(spot);
    }

    public int availableCount(SpotSize size) {
        return freeSpots.get(size).size();
    }

    public long capacity(SpotSize size) {
        return spots.stream().filter(s -> s.size() == size).count();
    }
}
