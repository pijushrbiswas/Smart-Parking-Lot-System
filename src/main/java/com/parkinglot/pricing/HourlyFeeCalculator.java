package com.parkinglot.pricing;

import com.parkinglot.exception.ErrorCode;
import com.parkinglot.exception.ParkingException;
import com.parkinglot.model.VehicleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Charges a per-vehicle-type hourly rate, billing every started hour.
 * Stays within the grace period are free.
 */
public class HourlyFeeCalculator implements FeeCalculator {

    private final Map<VehicleType, BigDecimal> hourlyRates;
    private final Duration gracePeriod;

    public HourlyFeeCalculator(Map<VehicleType, BigDecimal> hourlyRates, Duration gracePeriod) {
        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new ParkingException(ErrorCode.INVALID_CONFIGURATION, "Grace period must be non-negative");
        }
        // Fail fast at startup rather than at a customer's exit gate.
        for (VehicleType type : VehicleType.values()) {
            BigDecimal rate = hourlyRates.get(type);
            if (rate == null || rate.signum() < 0) {
                throw new ParkingException(ErrorCode.INVALID_CONFIGURATION, "Missing or negative hourly rate for " + type);
            }
        }
        this.hourlyRates = new EnumMap<>(hourlyRates);
        this.gracePeriod = gracePeriod;
    }

    @Override
    public BigDecimal calculate(VehicleType vehicleType, Duration duration) {
        if (vehicleType == null || duration == null || duration.isNegative()) {
            throw new ParkingException(ErrorCode.PRICING_ERROR, "Invalid pricing input: type=" + vehicleType + ", duration=" + duration);
        }
        if (duration.compareTo(gracePeriod) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        long billableHours = Math.max(1, ceilHours(duration));
        return hourlyRates.get(vehicleType)
                .multiply(BigDecimal.valueOf(billableHours))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static long ceilHours(Duration duration) {
        long seconds = duration.toSeconds();
        return (seconds + 3599) / 3600;
    }
}
