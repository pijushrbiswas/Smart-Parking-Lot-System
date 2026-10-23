package com.parkinglot.model;

import com.parkinglot.exception.InvalidConfigurationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregate root for the physical layout. Floors are kept ordered by level so
 * allocation strategies can iterate "nearest to entrance first".
 */
public final class ParkingLot {

    private final String name;
    private final List<ParkingFloor> floors;
    private final Map<Integer, ParkingFloor> floorsByLevel;

    private ParkingLot(String name, List<ParkingFloor> floors) {
        this.name = name;
        this.floors = floors.stream().sorted(Comparator.comparingInt(ParkingFloor::level)).toList();
        this.floorsByLevel = new TreeMap<>();
        this.floors.forEach(f -> floorsByLevel.put(f.level(), f));
    }

    public String name() {
        return name;
    }

    public List<ParkingFloor> floors() {
        return floors;
    }

    public void release(ParkingSpot spot) {
        ParkingFloor floor = floorsByLevel.get(spot.floorLevel());
        if (floor == null) {
            throw new IllegalArgumentException("Unknown floor " + spot.floorLevel() + " for spot " + spot.id());
        }
        floor.release(spot);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Builder that validates the whole layout before the lot becomes usable. */
    public static final class Builder {
        private final String name;
        private final Map<Integer, List<ParkingSpot>> spotsByLevel = new TreeMap<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder addFloor(int level, int smallSpots, int mediumSpots, int largeSpots) {
            if (spotsByLevel.containsKey(level)) {
                throw new InvalidConfigurationException("Duplicate floor level " + level);
            }
            if (smallSpots < 0 || mediumSpots < 0 || largeSpots < 0) {
                throw new InvalidConfigurationException("Spot counts must be non-negative on floor " + level);
            }
            List<ParkingSpot> spots = new ArrayList<>();
            int number = 1;
            for (int i = 0; i < smallSpots; i++) spots.add(new ParkingSpot(level, number++, SpotSize.SMALL));
            for (int i = 0; i < mediumSpots; i++) spots.add(new ParkingSpot(level, number++, SpotSize.MEDIUM));
            for (int i = 0; i < largeSpots; i++) spots.add(new ParkingSpot(level, number++, SpotSize.LARGE));
            spotsByLevel.put(level, spots);
            return this;
        }

        public ParkingLot build() {
            if (name == null || name.isBlank()) {
                throw new InvalidConfigurationException("Parking lot name must not be blank");
            }
            if (spotsByLevel.isEmpty()) {
                throw new InvalidConfigurationException("Parking lot must have at least one floor");
            }
            if (spotsByLevel.values().stream().allMatch(List::isEmpty)) {
                throw new InvalidConfigurationException("Parking lot must have at least one spot");
            }
            List<ParkingFloor> floors = spotsByLevel.entrySet().stream()
                    .map(e -> new ParkingFloor(e.getKey(), e.getValue()))
                    .toList();
            return new ParkingLot(name, floors);
        }
    }
}
