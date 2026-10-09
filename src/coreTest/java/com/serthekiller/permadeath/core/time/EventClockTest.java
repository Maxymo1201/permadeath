package com.serthekiller.permadeath.core.time;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventClockTest {
    private static final long MS = 1_000_000L;

    @Test
    void firstStepIsZeroThenRealTime() {
        AtomicLong nanos = new AtomicLong(123_456_789L);
        EventClock clock = new EventClock(nanos::get);
        assertEquals(0L, clock.step(), "time before the first tick (server stopped) is never counted");
        nanos.addAndGet(50 * MS);
        assertEquals(50L, clock.step());
        nanos.addAndGet(100 * MS); // 10 TPS: each tick is worth 100 ms
        assertEquals(100L, clock.step());
    }

    @Test
    void subMillisecondRemaindersAreCarried() {
        AtomicLong nanos = new AtomicLong();
        EventClock clock = new EventClock(nanos::get);
        clock.step();
        long total = 0L;
        for (int i = 0; i < 1000; i++) {
            nanos.addAndGet(MS / 2 + 1); // 0.500001 ms per call
            total += clock.step();
        }
        assertEquals(500L, total, "1000 x 0.500001 ms = 500.001 ms, no time lost to rounding");
    }

    @Test
    void stepIsCappedAndBackwardsTimeIgnored() {
        AtomicLong nanos = new AtomicLong();
        EventClock clock = new EventClock(nanos::get);
        clock.step();
        nanos.addAndGet(3_600_000L * MS); // a frozen server for one hour
        assertEquals(EventClock.MAX_STEP_MILLIS, clock.step());
        nanos.addAndGet(-5 * MS);
        assertEquals(0L, clock.step());
        nanos.addAndGet(20 * MS);
        assertEquals(20L, clock.step());
    }

    @Test
    void resetForgetsThePreviousReading() {
        AtomicLong nanos = new AtomicLong();
        EventClock clock = new EventClock(nanos::get);
        clock.step();
        nanos.addAndGet(40 * MS);
        clock.reset();
        nanos.addAndGet(9_000 * MS);
        assertEquals(0L, clock.step(), "after a restart the gap is not counted");
    }
}
