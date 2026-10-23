package com.parkinglot.allocation;

import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.SpotSize;
import com.parkinglot.model.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AllocationStrategyTest {

    // Floor 0: one large spot. Floor 1: one small, one medium.
    private static ParkingLot lot() {
        return ParkingLot.builder("L").addFloor(0, 0, 0, 1).addFloor(1, 1, 1, 0).build();
    }

    private static List<String> drain(SpotAllocationStrategy strategy, VehicleType type) {
        ParkingLot lot = lot();
        return Stream.generate(() -> strategy.allocate(lot, type))
                .takeWhile(Optional::isPresent)
                .map(o -> o.get().id())
                .toList();
    }

    @Test
    void bestFitExhaustsSmallestSizeAcrossFloorsBeforeGoingUp() {
        assertEquals(List.of("F1-S-001", "F1-M-002", "F0-L-001"),
                drain(new BestFitAllocationStrategy(), VehicleType.MOTORCYCLE));
    }

    @Test
    void nearestFloorExhaustsLowerFloorFirst() {
        assertEquals(List.of("F0-L-001", "F1-S-001", "F1-M-002"),
                drain(new NearestFloorAllocationStrategy(), VehicleType.MOTORCYCLE));
    }

    @Test
    void bothStrategiesOnlyOfferSpotsThatFit() {
        for (SpotAllocationStrategy strategy : List.of(new BestFitAllocationStrategy(), new NearestFloorAllocationStrategy())) {
            assertEquals(List.of("F0-L-001"), drain(strategy, VehicleType.BUS), strategy.getClass().getSimpleName());
            assertEquals(2, drain(strategy, VehicleType.CAR).size(), strategy.getClass().getSimpleName());
        }
    }

    @Test
    void allocatedSpotIsMarkedOccupiedAndReleasable() {
        ParkingLot lot = lot();
        ParkingSpot spot = new BestFitAllocationStrategy().allocate(lot, VehicleType.BUS).orElseThrow();

        assertTrue(spot.isOccupied());
        assertTrue(new BestFitAllocationStrategy().allocate(lot, VehicleType.BUS).isEmpty());
        lot.release(spot);
        assertEquals(1, lot.floors().getFirst().availableCount(SpotSize.LARGE));
    }
}
