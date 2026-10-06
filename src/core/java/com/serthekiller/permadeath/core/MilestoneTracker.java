package com.serthekiller.permadeath.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Idempotent milestone execution. The calendar is state based: if the day jumps from D9 to D31, every
 * milestone up to D31 (10, 20, 25, 30) is reported exactly once, in order, and recorded as executed.
 */
public final class MilestoneTracker {
    private MilestoneTracker() {
    }

    /** Milestones reached at {@code day} whose side effects have not been executed yet (ascending). */
    public static List<Integer> pending(ProgressionState state, int day) {
        List<Integer> result = new ArrayList<>();
        for (int m : PermadeathCalendar.MILESTONES) {
            if (m <= day && !state.executedMilestones.contains(m)) {
                result.add(m);
            }
        }
        return result;
    }

    public static void markExecuted(ProgressionState state, int milestone) {
        if (state.executedMilestones.add(milestone)) {
            state.markChanged();
        }
    }
}
