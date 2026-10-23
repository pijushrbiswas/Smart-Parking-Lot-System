package com.parkinglot.exception;

public class VehicleAlreadyParkedException extends ParkingException {
    public VehicleAlreadyParkedException(String licensePlate, String spotId) {
        super(ErrorCode.VEHICLE_ALREADY_PARKED,
                "Vehicle " + licensePlate + " is already parked at spot " + spotId);
    }
}
