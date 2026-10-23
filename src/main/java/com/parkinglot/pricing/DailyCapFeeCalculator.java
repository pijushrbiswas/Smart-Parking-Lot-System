package com.parkinglot.pricing;

import com.parkinglot.exception.InvalidConfigurationException;
import com.parkinglot.model.VehicleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Decorator that caps the charge for each 24h period. Adds a pricing rule
 * without modifying the wrapped calculator (open/closed principle).
 */
public class DailyCapFeeCalculator implements FeeCalculator {

    private static final Duration DAY = Duration.ofDays(1);

    private final FeeCalculator delegate;
    private final Map<VehicleType, BigDecimal> dailyCaps;

    public DailyCapFeeCalculator(FeeCalculator delegate, Map<VehicleType, BigDecimal> dailyCaps) {
        if (delegate == null) {
            throw new InvalidConfigurationException("Delegate fee calculator must not be null");
        }
        for (VehicleType type : VehicleType.values()) {
            BigDecimal cap = dailyCaps.get(type);
            if (cap == null || cap.signum() < 0) {
                throw new InvalidConfigurationException("Missing or negative daily cap for " + type);
            }
        }
        this.delegate = delegate;
        this.dailyCaps = new EnumMap<>(dailyCaps);
    }

    @Override
    public BigDecimal calculate(VehicleType vehicleType, Duration duration) {
        BigDecimal cap = dailyCaps.get(vehicleType);
        long fullDays = duration.toDays();
        Duration remainder = duration.minus(DAY.multipliedBy(fullDays));

        BigDecimal fullDaysFee = cap.multiply(BigDecimal.valueOf(fullDays));
        BigDecimal remainderFee = remainder.isZero()
                ? BigDecimal.ZERO
                : delegate.calculate(vehicleType, remainder).min(cap);
        return fullDaysFee.add(remainderFee).setScale(2, RoundingMode.HALF_UP);
    }
}
