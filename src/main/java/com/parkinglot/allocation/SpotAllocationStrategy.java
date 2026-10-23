package com.parkinglot.allocation;

import com.parkinglot.model.ParkingLot;
import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.VehicleType;

import java.util.Optional;

/**
 * Strategy for choosing a spot. Implementations must acquire the spot atomically
 * (via {@link com.parkinglot.model.ParkingFloor#tryAcquire}) so the returned spot
 * is already reserved for the caller.
 */
public interface SpotAllocationStrategy {
    Optional<ParkingSpot> allocate(ParkingLot lot, VehicleType vehicleType);
}
