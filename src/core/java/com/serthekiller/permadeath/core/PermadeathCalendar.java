package com.serthekiller.permadeath.core;

import java.util.List;
import java.util.OptionalInt;

/**
 * Constants and pure helpers shared by both calendars.
 */
public final class PermadeathCalendar {
    /** D60 is the final phase of these builds; the calendar never goes beyond it. */
    public static final int FINAL_DAY = 60;
    public static final int FIRST_DAY = 0;

    /** Days at which the rule set changes. */
    public static final List<Integer> MILESTONES = List.of(0, 10, 20, 25, 30, 40, 50, 60);

    /** Minecraft day length in Overworld day-time ticks. */
    public static final long TICKS_PER_MINECRAFT_DAY = 24_000L;

    /** REAL30: 1 Permadeath day = 12 real hours. */
    public static final long REAL30_MILLIS_PER_DAY = 12L * 60L * 60L * 1000L;
    /** REAL30: 1 Permadeath hour = 30 real minutes (calendar subdivision only). */
    public static final long REAL30_MILLIS_PER_PD_HOUR = REAL30_MILLIS_PER_DAY / 24L;
    /** REAL30: 1 Permadeath minute = 30 real seconds (calendar subdivision only). */
    public static final long REAL30_MILLIS_PER_PD_MINUTE = REAL30_MILLIS_PER_PD_HOUR / 60L;
    /** REAL30: D60 is reached after exactly 720 real hours. */
    public static final long REAL30_FINAL_ELAPSED_MILLIS = FINAL_DAY * REAL30_MILLIS_PER_DAY;

    private PermadeathCalendar() {
    }

    public static int clampDay(long day) {
        if (day < FIRST_DAY) {
            return FIRST_DAY;
        }
        return (int) Math.min(day, FINAL_DAY);
    }

    /**
     * Phase index used by the per-phase handlers: 0 = D0-9, 1 = D10-19, ..., 5 = D50-59, 6 = D60 (final).
     */
    public static int phaseForDay(int day) {
        return clampDay(day) / 10;
    }

    public static String phaseName(int phase) {
        return switch (phase) {
            case 0 -> "Fase 1: Días 0-9";
            case 1 -> "Fase 2: Días 10-19";
            case 2 -> "Fase 3: Días 20-29";
            case 3 -> "Fase 4: Días 30-39";
            case 4 -> "Fase 5: Días 40-49";
            case 5 -> "Fase 6: Días 50-59";
            default -> "Fase 7: Día 60 (final)";
        };
    }

    /** Next milestone strictly after {@code day}, or empty once D60 is reached. */
    public static OptionalInt nextMilestone(int day) {
        for (int m : MILESTONES) {
            if (m > day) {
                return OptionalInt.of(m);
            }
        }
        return OptionalInt.empty();
    }

    /** All milestones that are reached at {@code day} (inclusive). */
    public static List<Integer> milestonesReachedAt(int day) {
        return MILESTONES.stream().filter(m -> m <= day).toList();
    }
}
