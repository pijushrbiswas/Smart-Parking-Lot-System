package com.parkinglot.pricing;

import com.parkinglot.exception.InvalidConfigurationException;
import com.parkinglot.exception.PricingException;
import com.parkinglot.model.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeeCalculatorTest {

    private final HourlyFeeCalculator hourly = new HourlyFeeCalculator(Map.of(
            VehicleType.MOTORCYCLE, new BigDecimal("10"),
            VehicleType.CAR, new BigDecimal("20"),
            VehicleType.BUS, new BigDecimal("50")), Duration.ofMinutes(10));

    @Test
    void freeWithinGracePeriod() {
        assertEquals(new BigDecimal("0.00"), hourly.calculate(VehicleType.CAR, Duration.ofMinutes(10)));
    }

    @Test
    void billsEveryStartedHourPerVehicleType() {
        assertEquals(new BigDecimal("20.00"), hourly.calculate(VehicleType.CAR, Duration.ofMinutes(11)));
        assertEquals(new BigDecimal("20.00"), hourly.calculate(VehicleType.CAR, Duration.ofMinutes(60)));
        assertEquals(new BigDecimal("40.00"), hourly.calculate(VehicleType.CAR, Duration.ofMinutes(61)));
        assertEquals(new BigDecimal("30.00"), hourly.calculate(VehicleType.MOTORCYCLE, Duration.ofMinutes(150)));
        assertEquals(new BigDecimal("150.00"), hourly.calculate(VehicleType.BUS, Duration.ofHours(3)));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(PricingException.class, () -> hourly.calculate(VehicleType.CAR, Duration.ofMinutes(-1)));
        assertThrows(PricingException.class, () -> hourly.calculate(null, Duration.ZERO));
    }

    @Test
    void failsFastOnIncompleteRateCard() {
        assertThrows(InvalidConfigurationException.class,
                () -> new HourlyFeeCalculator(Map.of(VehicleType.CAR, BigDecimal.ONE), Duration.ZERO));
        assertThrows(InvalidConfigurationException.class,
                () -> new HourlyFeeCalculator(Map.of(
                        VehicleType.MOTORCYCLE, BigDecimal.ONE,
                        VehicleType.CAR, BigDecimal.ONE,
                        VehicleType.BUS, BigDecimal.ONE), Duration.ofMinutes(-5)));
    }

    @Test
    void dailyCapLimitsEachDay() {
        FeeCalculator capped = new DailyCapFeeCalculator(hourly, Map.of(
                VehicleType.MOTORCYCLE, new BigDecimal("80"),
                VehicleType.CAR, new BigDecimal("150"),
                VehicleType.BUS, new BigDecimal("400")));

        assertEquals(new BigDecimal("60.00"), capped.calculate(VehicleType.CAR, Duration.ofHours(3)));
        assertEquals(new BigDecimal("150.00"), capped.calculate(VehicleType.CAR, Duration.ofHours(10)));
        assertEquals(new BigDecimal("150.00"), capped.calculate(VehicleType.CAR, Duration.ofHours(24)));
        // 1 full day (150) + 2h (40)
        assertEquals(new BigDecimal("190.00"), capped.calculate(VehicleType.CAR, Duration.ofHours(26)));
    }
}
