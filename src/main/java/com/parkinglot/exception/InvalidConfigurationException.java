package com.parkinglot.exception;

public class InvalidConfigurationException extends ParkingException {
    public InvalidConfigurationException(String message) {
        super(ErrorCode.INVALID_CONFIGURATION, message);
    }
}
