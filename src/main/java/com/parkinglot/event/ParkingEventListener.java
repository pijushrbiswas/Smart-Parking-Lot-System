package com.parkinglot.event;

@FunctionalInterface
public interface ParkingEventListener {
    void onEvent(ParkingEvent event);
}
