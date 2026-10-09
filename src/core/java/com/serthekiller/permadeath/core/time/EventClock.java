package com.serthekiller.permadeath.core.time;

import java.util.function.LongSupplier;

/**
 * Real time elapsed between two server ticks, measured with a monotonic clock ({@link System#nanoTime()}).
 *
 * <ul>
 *     <li>Independent of TPS: at 10 TPS every tick is worth ~100 ms, so a 30 minute timer still lasts 30 real
 *     minutes.</li>
 *     <li>Immune to wall-clock jumps (NTP, manual changes, DST): only nanoTime differences are used and they are
 *     never persisted.</li>
 *     <li>A single step is capped at {@link #MAX_STEP_MILLIS}: a frozen server (debugger, host suspend, a huge lag
 *     spike) cannot burn a whole event or summon several Withers at once.</li>
 *     <li>The first step after a (re)start is 0: time with the server stopped is never counted.</li>
 *     <li>Sub-millisecond remainders are carried over, so no time is lost to rounding.</li>
 * </ul>
 */
public final class EventClock {
    public static final long MAX_STEP_MILLIS = 10_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final LongSupplier nanoTime;
    private boolean started;
    private long lastNanos;
    private long carryNanos;

    public EventClock(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public static EventClock system() {
        return new EventClock(System::nanoTime);
    }

    /** Milliseconds of real time since the previous call (0 on the first call), capped at {@link #MAX_STEP_MILLIS}. */
    public long step() {
        long now = nanoTime.getAsLong();
        if (!started) {
            started = true;
            lastNanos = now;
            return 0L;
        }
        long delta = now - lastNanos;
        lastNanos = now;
        if (delta <= 0L) {
            return 0L;
        }
        delta += carryNanos;
        long millis = delta / NANOS_PER_MILLI;
        if (millis >= MAX_STEP_MILLIS) {
            carryNanos = 0L;
            return MAX_STEP_MILLIS;
        }
        carryNanos = delta % NANOS_PER_MILLI;
        return millis;
    }

    /** Forgets the previous reading (server stop): the next step is 0. */
    public void reset() {
        started = false;
        carryNanos = 0L;
    }
}
