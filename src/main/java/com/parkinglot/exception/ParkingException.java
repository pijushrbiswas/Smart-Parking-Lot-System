package com.parkinglot.exception;

/**
 * Root of all domain exceptions. Unchecked so callers are not forced to wrap
 * every call, while still being catchable as a single type at the API boundary.
 */
public class ParkingException extends RuntimeException {

    private final ErrorCode errorCode;

    public ParkingException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ParkingException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
