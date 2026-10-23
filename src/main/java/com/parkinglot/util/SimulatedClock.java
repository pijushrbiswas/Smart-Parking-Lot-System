package com.parkinglot.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe clock that only moves when told to; for simulations and tests. */
public final class SimulatedClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public SimulatedClock(Instant start) {
        this(new AtomicReference<>(start), ZoneOffset.UTC);
    }

    private SimulatedClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        now.updateAndGet(t -> t.plus(duration));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new SimulatedClock(now, zone);
    }
}
