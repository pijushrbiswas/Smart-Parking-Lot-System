package com.parkinglot.exception;

public class VehicleNotParkedException extends ParkingException {
    public VehicleNotParkedException(String licensePlate) {
        super(ErrorCode.VEHICLE_NOT_PARKED, "No active parking session for vehicle " + licensePlate);
    }
}
