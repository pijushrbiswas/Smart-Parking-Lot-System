package com.parkinglot.event;

import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.Receipt;

public sealed interface ParkingEvent {

    int floorLevel();

    record VehicleEntered(ParkingTicket ticket) implements ParkingEvent {
        @Override
        public int floorLevel() {
            return ticket.spot().floorLevel();
        }
    }

    record VehicleExited(Receipt receipt, int floorLevel) implements ParkingEvent {
    }
}
