package com.parkinglot.model;

public enum VehicleType {
    MOTORCYCLE(SpotSize.SMALL),
    CAR(SpotSize.MEDIUM),
    BUS(SpotSize.LARGE);

    private final SpotSize requiredSpotSize;

    VehicleType(SpotSize requiredSpotSize) {
        this.requiredSpotSize = requiredSpotSize;
    }

    public SpotSize requiredSpotSize() {
        return requiredSpotSize;
    }
}
