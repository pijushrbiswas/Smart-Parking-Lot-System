package com.parkinglot.event;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer hub for real-time updates (display boards, mobile apps, analytics).
 * A failing listener is logged and isolated: it must never roll back a vehicle's
 * entry or exit that has already happened.
 */
public class ParkingEventPublisher {

    private static final Logger LOG = System.getLogger(ParkingEventPublisher.class.getName());

    private final List<ParkingEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(ParkingEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void publish(ParkingEvent event) {
        for (ParkingEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Listener " + listener + " failed for event " + event, e);
            }
        }
    }
}
