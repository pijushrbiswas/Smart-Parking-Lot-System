package com.parkinglot.exception;

/**
 * Stable, machine-readable error codes. An API layer can map these to HTTP
 * statuses without depending on exception class names.
 */
public enum ErrorCode {
    INVALID_VEHICLE,
    INVALID_CONFIGURATION,
    NO_SPOT_AVAILABLE,
    VEHICLE_ALREADY_PARKED,
    VEHICLE_NOT_PARKED,
    TICKET_NOT_FOUND,
    TICKET_ALREADY_CLOSED,
    PRICING_ERROR
}
