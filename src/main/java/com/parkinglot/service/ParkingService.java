package com.parkinglot.service;

import com.parkinglot.model.FloorAvailability;
import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.Receipt;
import com.parkinglot.model.Vehicle;

import java.util.List;

/** Use-case boundary consumed by entry/exit gates or a REST controller. */
public interface ParkingService {

    ParkingTicket checkIn(Vehicle vehicle);

    Receipt checkOut(String ticketId);

    /** Lost-ticket flow: look the session up by plate. */
    Receipt checkOutByLicensePlate(String licensePlate);

    List<FloorAvailability> getAvailability();
}
