package com.parkinglot.exception;

public class InvalidVehicleException extends ParkingException {
    public InvalidVehicleException(String message) {
        super(ErrorCode.INVALID_VEHICLE, message);
    }
}
