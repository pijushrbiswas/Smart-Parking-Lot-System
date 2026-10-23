package com.parkinglot.allocation;

import com.parkinglot.model.ParkingFloor;
import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.SpotSize;
import com.parkinglot.model.VehicleType;

import java.util.Optional;

/**
 * Tries the smallest fitting size across all floors before moving up a size.
 * This keeps large spots free for buses as long as possible, at the cost of
 * possibly sending a car to a higher floor.
 *
 * Complexity: O(S * F * log N) for S sizes, F floors, N spots per free list.
 */
public class BestFitAllocationStrategy implements SpotAllocationStrategy {

    @Override
    public Optional<ParkingSpot> allocate(ParkingLot lot, VehicleType vehicleType) {
        for (SpotSize size : SpotSize.sizesFitting(vehicleType.requiredSpotSize())) {
            for (ParkingFloor floor : lot.floors()) {
                Optional<ParkingSpot> spot = floor.tryAcquire(size);
                if (spot.isPresent()) {
                    return spot;
                }
            }
        }
        return Optional.empty();
    }
}
