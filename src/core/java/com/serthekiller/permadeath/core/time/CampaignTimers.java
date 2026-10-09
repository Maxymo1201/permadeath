package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.ProgressionState;

/**
 * Global campaign timers that run on ACTIVE time: Death Train, X2 Shulker Shells, the D60 Life Orb countdown and the
 * D60 final challenge. They only advance while the server runs and at least one eligible survivor is online, so a
 * stopped or empty server never consumes them. Pure logic on {@link ProgressionState}: the game layer feeds the
 * steps of {@link EventClock} and reacts to the transitions returned by {@link #advance}.
 */
public final class CampaignTimers {
    /** Smallest storm left by an administrator's "removeHours/removeMinutes". */
    public static final long MIN_STORM_AFTER_REMOVAL_MILLIS = 1_000L;

    /** Transitions that happened during one step (each one is reported exactly once). */
    public record Step(boolean deathTrainEnded, boolean shulkerEventEnded, boolean lifeOrbExpired, boolean finalPhaseEnded) {
        public static final Step NONE = new Step(false, false, false, false);
    }

    private CampaignTimers() {
    }

    // ------------------------------------------------------------------------------------------------ Death Train

    /** Storm of one death on {@code day}, scaled to the profile and ADDED to the remaining time. @return the added time */
    public static long addDeathTrain(ProgressionState s, PermadeathTimings timings, int day) {
        long added = timings.deathTrainMillis(day);
        s.deathTrainRemainingMillis = saturatedAdd(Math.max(0L, s.deathTrainRemainingMillis), added);
        s.markChanged();
        return added;
    }

    /** Administrator time (effective real time, never scaled). */
    public static void addDeathTrainRaw(ProgressionState s, long millis) {
        s.deathTrainRemainingMillis = saturatedAdd(Math.max(0L, s.deathTrainRemainingMillis), Math.max(0L, millis));
        s.markChanged();
    }

    /** @return false when no storm is running; otherwise at least {@link #MIN_STORM_AFTER_REMOVAL_MILLIS} is left. */
    public static boolean removeDeathTrainRaw(ProgressionState s, long millis) {
        if (s.deathTrainRemainingMillis <= 0L) {
            return false;
        }
        s.deathTrainRemainingMillis = Math.max(MIN_STORM_AFTER_REMOVAL_MILLIS, s.deathTrainRemainingMillis - Math.max(0L, millis));
        s.markChanged();
        return true;
    }

    // ------------------------------------------------------------------------------------------------ Shulker event

    /** @return false when the event is already running */
    public static boolean startShulkerEvent(ProgressionState s, PermadeathTimings timings) {
        if (s.shulkerEventRemainingMillis > 0L) {
            return false;
        }
        s.shulkerEventRemainingMillis = timings.shulkerEventMillis();
        s.markChanged();
        return true;
    }

    // ------------------------------------------------------------------------------------------------ Life Orb

    public static boolean lifeOrbCountdownRunning(ProgressionState s) {
        return !s.lifeOrbActive && s.lifeOrbRemainingMillis >= 0L;
    }

    /** Starts (or restarts) the Life Orb countdown; the penalty is lifted while it runs. */
    public static void startLifeOrbCountdown(ProgressionState s, PermadeathTimings timings) {
        s.lifeOrbActive = false;
        s.lifeOrbRemainingMillis = timings.lifeOrbCountdownMillis();
        s.markChanged();
    }

    /** Before D60 (e.g. after a setday rollback) there is neither a countdown nor a penalty. */
    public static void clearLifeOrb(ProgressionState s) {
        if (s.lifeOrbActive || s.lifeOrbRemainingMillis != -1L) {
            s.lifeOrbActive = false;
            s.lifeOrbRemainingMillis = -1L;
            s.markChanged();
        }
    }

    // ------------------------------------------------------------------------------------------------ step

    /**
     * Advances every global timer by one step.
     *
     * @param stepMillis      real time since the previous tick ({@link EventClock#step()})
     * @param anyEligible     at least one eligible survivor is online; otherwise every timer is paused
     * @param finalOnCalendar REAL30 strict campaign: the final challenge and the Life Orb follow the calendar and are
     *                        set by {@link FinalChallenge#syncToCalendar} instead (they still report their transitions)
     */
    public static Step advance(ProgressionState s, long stepMillis, boolean anyEligible, boolean finalOnCalendar) {
        long active = anyEligible ? Math.max(0L, stepMillis) : 0L;
        boolean changed = false;
        boolean deathTrainEnded = false;
        boolean shulkerEnded = false;
        boolean lifeOrbExpired = false;
        boolean finalEnded = false;
        if (s.deathTrainRemainingMillis > 0L && active > 0L) {
            s.deathTrainRemainingMillis = Math.max(0L, s.deathTrainRemainingMillis - active);
            deathTrainEnded = s.deathTrainRemainingMillis == 0L;
            changed = true;
        }
        if (s.shulkerEventRemainingMillis > 0L && active > 0L) {
            s.shulkerEventRemainingMillis = Math.max(0L, s.shulkerEventRemainingMillis - active);
            shulkerEnded = s.shulkerEventRemainingMillis == 0L;
            changed = true;
        }
        long finalStep = finalOnCalendar ? 0L : active;
        if (lifeOrbCountdownRunning(s) && (finalStep > 0L || s.lifeOrbRemainingMillis == 0L)) {
            s.lifeOrbRemainingMillis = Math.max(0L, s.lifeOrbRemainingMillis - finalStep);
            if (s.lifeOrbRemainingMillis == 0L) {
                s.lifeOrbActive = true;
                s.lifeOrbRemainingMillis = -1L;
                lifeOrbExpired = true;
            }
            changed = true;
        }
        if (s.finalPhaseState == FinalPhaseState.ACTIVE && (finalStep > 0L || s.finalPhaseRemainingMillis == 0L)) {
            s.finalPhaseRemainingMillis = Math.max(0L, s.finalPhaseRemainingMillis - finalStep);
            finalEnded = s.finalPhaseRemainingMillis == 0L;
            changed = true;
        }
        if (changed) {
            s.markChanged();
        }
        if (!deathTrainEnded && !shulkerEnded && !lifeOrbExpired && !finalEnded) {
            return Step.NONE;
        }
        return new Step(deathTrainEnded, shulkerEnded, lifeOrbExpired, finalEnded);
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        return r < 0L ? Long.MAX_VALUE : r;
    }
}
