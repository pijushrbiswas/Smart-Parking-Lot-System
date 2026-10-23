package com.parkinglot;

import com.parkinglot.exception.ErrorCode;
import com.parkinglot.exception.ParkingException;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ParkingAssertions {

    private ParkingAssertions() {
    }

    public static ParkingException assertError(ErrorCode expected, Executable action) {
        ParkingException e = assertThrows(ParkingException.class, action);
        assertEquals(expected, e.getErrorCode(), e.getMessage());
        return e;
    }
}
