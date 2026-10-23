package com.parkinglot.allocation;

import com.parkinglot.model.ParkingFloor;
import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.SpotSize;
import com.parkinglot.model.VehicleType;

import java.util.Optional;

/**
 * Prefers the lowest floor, then the smallest fitting size on that floor.
 * Minimises driving distance for the customer.
 */
public class NearestFloorAllocationStrategy implements SpotAllocationStrategy {

    @Override
    public Optional<ParkingSpot> allocate(ParkingLot lot, VehicleType vehicleType) {
        for (ParkingFloor floor : lot.floors()) {
            for (SpotSize size : SpotSize.sizesFitting(vehicleType.requiredSpotSize())) {
                Optional<ParkingSpot> spot = floor.tryAcquire(size);
                if (spot.isPresent()) {
                    return spot;
                }
            }
        }
        return Optional.empty();
    }
}
