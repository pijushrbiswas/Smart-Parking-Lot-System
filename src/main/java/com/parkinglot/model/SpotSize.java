package com.parkinglot.model;

import java.util.Arrays;
import java.util.List;

/**
 * Physical spot sizes, declared smallest to largest. Declaration order is
 * significant: a spot can hold any vehicle whose required size is not larger.
 */
public enum SpotSize {
    SMALL,
    MEDIUM,
    LARGE;

    public boolean canAccommodate(SpotSize required) {
        return this.compareTo(required) >= 0;
    }

    /** All sizes able to hold {@code required}, ordered smallest first (best fit first). */
    public static List<SpotSize> sizesFitting(SpotSize required) {
        return Arrays.stream(values()).filter(s -> s.canAccommodate(required)).toList();
    }
}
