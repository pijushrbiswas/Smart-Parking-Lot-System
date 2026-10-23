package com.parkinglot.exception;

import com.parkinglot.model.VehicleType;

public class NoAvailableSpotException extends ParkingException {
    public NoAvailableSpotException(VehicleType vehicleType) {
        super(ErrorCode.NO_SPOT_AVAILABLE, "No available spot for vehicle type " + vehicleType);
    }
}
