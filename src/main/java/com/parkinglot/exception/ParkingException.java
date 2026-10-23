package com.parkinglot.exception;

/**
 * The single domain exception. Callers branch on {@link ErrorCode} rather than on
 * exception subclasses. Unchecked so callers are not forced to wrap every call.
 */
public final class ParkingException extends RuntimeException {

    private final ErrorCode errorCode;

    public ParkingException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
