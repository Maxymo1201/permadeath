package com.serthekiller.permadeath.core;

import java.time.Duration;
import java.util.OptionalInt;

/**
 * Single source of truth for the Permadeath calendar. Every mechanic asks this clock which day/phase
 * is active; no mechanic computes its own calendar.
 */
public interface ProgressionClock {

    ProgressionMode mode();

    /** Effective Permadeath day, 0..60. Never decreases except through an explicit {@link #setDay(int)}. */
    int getDay();

    default int getPhase() {
        return PermadeathCalendar.phaseForDay(getDay());
    }

    default boolean hasReached(int day) {
        return getDay() >= day;
    }

    /** Calendar progress since PD day 0 (GAME60: Minecraft time at 20 tps nominal; REAL30: wall clock). */
    Duration getElapsed();

    default OptionalInt getNextMilestone() {
        return PermadeathCalendar.nextMilestone(getDay());
    }

    /** Calendar time still needed to reach {@code day}; {@link Duration#ZERO} if already reached. */
    Duration timeUntilDay(int day);

    /**
     * Re-reads the time source and updates the effective day.
     *
     * @return the change, or {@link DayChange#NONE} if the day did not move
     */
    DayChange update();

    /** Administrative override: moves the calendar so that the effective day becomes {@code day}. */
    void setDay(int day);

    /** Human readable multi-line description for {@code /permadeath status}. */
    String describe();

    record DayChange(int previousDay, int newDay) {
        public static final DayChange NONE = new DayChange(-1, -1);

        public boolean changed() {
            return previousDay != newDay;
        }
    }
}
