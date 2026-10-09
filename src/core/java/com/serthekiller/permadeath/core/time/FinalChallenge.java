package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.FinalParticipant;
import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionState;

import java.util.UUID;

/**
 * D60 final challenge (pure logic). Reaching D60 activates the D60 rules; the challenge itself starts when an eligible
 * survivor is online on D60, lasts {@link PermadeathTimings#finalPhaseMillis()} of active time and ends with a
 * per-player evaluation. The calendar stays on D60 the whole time and afterwards; there is no D61.
 *
 * <p>Victory of a participant: alive (not eliminated, not a spectator) when the challenge ends, held a Life Orb before
 * its countdown ran out and still holds it at the end. The campaign is COMPLETED when at least one participant wins,
 * FAILED otherwise, or as soon as every participant has been eliminated.</p>
 */
public final class FinalChallenge {
    private FinalChallenge() {
    }

    public static boolean canStart(ProgressionState s, int day, boolean anyEligible) {
        return s.finalPhaseState == FinalPhaseState.NOT_STARTED && day >= PermadeathCalendar.FINAL_DAY && anyEligible;
    }

    /**
     * T = 0: the final timer starts, the Life Orb countdown starts (unless an earlier countdown is still running or
     * already over) and every Wither counter starts from a full interval.
     */
    public static void start(ProgressionState s, PermadeathTimings timings, long nowEpochMillis) {
        s.finalPhaseState = FinalPhaseState.ACTIVE;
        s.finalPhaseRemainingMillis = timings.finalPhaseMillis();
        s.finalPhaseStartedEpochMillis = nowEpochMillis;
        s.finalPhaseEndedEpochMillis = 0L;
        s.finalParticipants.clear();
        if (!s.lifeOrbActive && s.lifeOrbRemainingMillis < 0L) {
            s.lifeOrbRemainingMillis = timings.lifeOrbCountdownMillis();
        }
        s.witherRemainingMillis.clear();
        s.markChanged();
    }

    /**
     * REAL30 strict campaign: D60 begins at hour 714 and the challenge ends at hour 720 of the calendar, whatever the
     * server did in between. The final timer and the Life Orb countdown are recomputed from the calendar start.
     */
    public static void syncToCalendar(ProgressionState s, PermadeathTimings timings, long nowEpochMillis) {
        if (s.finalPhaseState != FinalPhaseState.ACTIVE) {
            return;
        }
        long finalDayStart = s.startEpochMillis + timings.strictFinalDayElapsedMillis();
        long finalEnd = s.startEpochMillis + PermadeathCalendar.REAL30_FINAL_ELAPSED_MILLIS;
        s.finalPhaseRemainingMillis = Math.max(0L, finalEnd - nowEpochMillis);
        if (CampaignTimers.lifeOrbCountdownRunning(s)) {
            s.lifeOrbRemainingMillis = Math.max(0L, finalDayStart + timings.lifeOrbCountdownMillis() - nowEpochMillis);
        }
    }

    /**
     * Records what is known about an online player while the challenge runs. Only eligible players become
     * participants; a participant seen as not eligible (spectator, dead) is no longer counted as a survivor.
     */
    public static void observe(ProgressionState s, UUID uuid, String name, boolean eligible, boolean holdingLifeOrb) {
        if (s.finalPhaseState != FinalPhaseState.ACTIVE) {
            return;
        }
        FinalParticipant p = s.finalParticipants.get(uuid);
        if (p == null) {
            if (!eligible) {
                return;
            }
            p = new FinalParticipant(name);
            s.finalParticipants.put(uuid, p);
            s.markChanged();
        }
        boolean beforeDeadline = p.lifeOrbBeforeDeadline || (eligible && holdingLifeOrb && CampaignTimers.lifeOrbCountdownRunning(s));
        boolean survived = eligible && !p.eliminated;
        if (p.holdingLifeOrb != holdingLifeOrb || p.lifeOrbBeforeDeadline != beforeDeadline || p.survivedLastSeen != survived
                || !name.equals(p.name)) {
            p.holdingLifeOrb = holdingLifeOrb;
            p.lifeOrbBeforeDeadline = beforeDeadline;
            p.survivedLastSeen = survived;
            p.name = name;
            s.markChanged();
        }
    }

    /** A participant died permanently. @return true when every participant is now eliminated. */
    public static boolean markEliminated(ProgressionState s, UUID uuid) {
        FinalParticipant p = s.finalParticipants.get(uuid);
        if (s.finalPhaseState == FinalPhaseState.ACTIVE && p != null && !p.eliminated) {
            p.eliminated = true;
            p.survivedLastSeen = false;
            p.result = FinalParticipant.Result.DEFEAT;
            s.markChanged();
        }
        return allEliminated(s);
    }

    public static boolean allEliminated(ProgressionState s) {
        return !s.finalParticipants.isEmpty() && s.finalParticipants.values().stream().allMatch(p -> p.eliminated);
    }

    /**
     * The final timer ran out: evaluates every participant once. Calling it again (or on a finished challenge) does
     * nothing. @return the new state, or the current one when nothing happened
     */
    public static FinalPhaseState finish(ProgressionState s, long nowEpochMillis) {
        if (s.finalPhaseState != FinalPhaseState.ACTIVE) {
            return s.finalPhaseState;
        }
        boolean anyVictory = false;
        for (FinalParticipant p : s.finalParticipants.values()) {
            boolean victory = !p.eliminated && p.survivedLastSeen && p.lifeOrbBeforeDeadline && p.holdingLifeOrb;
            p.result = victory ? FinalParticipant.Result.VICTORY : FinalParticipant.Result.DEFEAT;
            anyVictory |= victory;
        }
        end(s, anyVictory ? FinalPhaseState.COMPLETED : FinalPhaseState.FAILED, nowEpochMillis);
        return s.finalPhaseState;
    }

    /** Every participant was eliminated before the end. */
    public static FinalPhaseState fail(ProgressionState s, long nowEpochMillis) {
        if (s.finalPhaseState != FinalPhaseState.ACTIVE) {
            return s.finalPhaseState;
        }
        for (FinalParticipant p : s.finalParticipants.values()) {
            p.result = FinalParticipant.Result.DEFEAT;
        }
        end(s, FinalPhaseState.FAILED, nowEpochMillis);
        return s.finalPhaseState;
    }

    private static void end(ProgressionState s, FinalPhaseState result, long nowEpochMillis) {
        s.finalPhaseState = result;
        s.finalPhaseRemainingMillis = 0L;
        s.finalPhaseEndedEpochMillis = nowEpochMillis;
        s.markChanged();
    }

    /** setday below D60 or /permadeath reset: the challenge can be played again. */
    public static void reset(ProgressionState s) {
        if (s.finalPhaseState == FinalPhaseState.NOT_STARTED && s.finalParticipants.isEmpty() && s.finalPhaseRemainingMillis == 0L) {
            return;
        }
        s.finalPhaseState = FinalPhaseState.NOT_STARTED;
        s.finalPhaseRemainingMillis = 0L;
        s.finalPhaseStartedEpochMillis = 0L;
        s.finalPhaseEndedEpochMillis = 0L;
        s.finalParticipants.clear();
        s.markChanged();
    }
}
