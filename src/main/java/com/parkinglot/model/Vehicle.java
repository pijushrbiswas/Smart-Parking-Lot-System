package com.parkinglot.model;

import com.parkinglot.exception.InvalidVehicleException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Immutable vehicle identity. The license plate is normalised (trimmed,
 * upper-cased, inner whitespace removed) so "ka 01 ab 1234" and "KA01AB1234"
 * are the same vehicle.
 */
public record Vehicle(String licensePlate, VehicleType type) {

    private static final Pattern PLATE_PATTERN = Pattern.compile("[A-Z0-9-]{2,15}");

    public Vehicle {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new InvalidVehicleException("License plate must not be blank");
        }
        if (type == null) {
            throw new InvalidVehicleException("Vehicle type must not be null");
        }
        licensePlate = normalise(licensePlate);
        if (!PLATE_PATTERN.matcher(licensePlate).matches()) {
            throw new InvalidVehicleException("Invalid license plate: " + licensePlate);
        }
    }

    public static String normalise(String licensePlate) {
        return licensePlate.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
