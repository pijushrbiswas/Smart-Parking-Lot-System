package com.parkinglot.pricing;

import com.parkinglot.model.VehicleType;

import java.math.BigDecimal;
import java.time.Duration;

/** Strategy for pricing a parking session. */
public interface FeeCalculator {
    BigDecimal calculate(VehicleType vehicleType, Duration duration);
}
