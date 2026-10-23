package com.parkinglot.exception;

public class PricingException extends ParkingException {
    public PricingException(String message) {
        super(ErrorCode.PRICING_ERROR, message);
    }
}
