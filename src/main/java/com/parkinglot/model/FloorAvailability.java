package com.parkinglot.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Point-in-time view of free spots on a floor, per spot size. */
public record FloorAvailability(int level, Map<SpotSize, Integer> available, Map<SpotSize, Long> capacity) {

    public FloorAvailability {
        available = Collections.unmodifiableMap(new EnumMap<>(available));
        capacity = Collections.unmodifiableMap(new EnumMap<>(capacity));
    }

    public static FloorAvailability of(ParkingFloor floor) {
        Map<SpotSize, Integer> available = new EnumMap<>(SpotSize.class);
        Map<SpotSize, Long> capacity = new EnumMap<>(SpotSize.class);
        for (SpotSize size : SpotSize.values()) {
            available.put(size, floor.availableCount(size));
            capacity.put(size, floor.capacity(size));
        }
        return new FloorAvailability(floor.level(), available, capacity);
    }
}
