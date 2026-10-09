package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.ProgressionState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.serthekiller.permadeath.core.time.PermadeathTimings.HOUR;
import static com.serthekiller.permadeath.core.time.PermadeathTimings.MINUTE;
import static com.serthekiller.permadeath.core.time.PermadeathTimings.SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTimersTest {
    private static final long TICK = 50L;

    /** Runs {@code millis} of active time in 50 ms ticks and counts every transition. */
    private static int[] run(ProgressionState s, long millis, boolean eligible) {
        int[] counts = new int[4];
        for (long t = 0; t < millis; t += TICK) {
            CampaignTimers.Step step = CampaignTimers.advance(s, Math.min(TICK, millis - t), eligible, false);
            counts[0] += step.deathTrainEnded() ? 1 : 0;
            counts[1] += step.shulkerEventEnded() ? 1 : 0;
            counts[2] += step.lifeOrbExpired() ? 1 : 0;
            counts[3] += step.finalPhaseEnded() ? 1 : 0;
        }
        return counts;
    }

    @Test
    void deathsAccumulateWithoutRestartingTheStorm() {
        ProgressionState s = new ProgressionState();
        assertEquals(10 * MINUTE, CampaignTimers.addDeathTrain(s, PermadeathTimings.GAME60, 12), "D12: 12 h / 72");
        run(s, 4 * MINUTE, true);
        assertEquals(6 * MINUTE, s.deathTrainRemainingMillis);
        // two simultaneous deaths on D40: both durations are added to what is left
        CampaignTimers.addDeathTrain(s, PermadeathTimings.GAME60, 40);
        CampaignTimers.addDeathTrain(s, PermadeathTimings.GAME60, 40);
        assertEquals(6 * MINUTE + 2 * (13 * MINUTE + 20 * SECOND), s.deathTrainRemainingMillis);
    }

    @Test
    void scaleIsAppliedOnceAndAdminTimeIsNeverScaled() {
        ProgressionState s = new ProgressionState();
        long added = CampaignTimers.addDeathTrain(s, PermadeathTimings.REAL30, 10);
        assertEquals(5 * HOUR, added);
        CampaignTimers.addDeathTrainRaw(s, 2 * HOUR);
        assertEquals(7 * HOUR, s.deathTrainRemainingMillis, "administrator hours are effective hours");
        assertTrue(CampaignTimers.removeDeathTrainRaw(s, 10 * HOUR));
        assertEquals(CampaignTimers.MIN_STORM_AFTER_REMOVAL_MILLIS, s.deathTrainRemainingMillis);
        s.deathTrainRemainingMillis = 0L;
        assertFalse(CampaignTimers.removeDeathTrainRaw(s, HOUR), "no storm to shorten");
    }

    @Test
    void stormEndsOnceAndOnlyCountsWithEligibleSurvivorsOnline() {
        ProgressionState s = new ProgressionState();
        AtomicInteger saves = new AtomicInteger();
        s.setChangeListener(saves::incrementAndGet);
        CampaignTimers.addDeathTrain(s, PermadeathTimings.GAME60, 0); // 60 s minimum
        int[] paused = run(s, 10 * MINUTE, false);
        assertEquals(60 * SECOND, s.deathTrainRemainingMillis, "no eligible survivor online: paused");
        assertEquals(0, paused[0]);
        int[] counted = run(s, 2 * MINUTE, true);
        assertEquals(0L, s.deathTrainRemainingMillis);
        assertEquals(1, counted[0], "the end is reported exactly once");
        assertTrue(saves.get() > 0, "the state is marked for saving");
    }

    @Test
    void restartKeepsTheRemainingTime() {
        ProgressionState s = new ProgressionState();
        CampaignTimers.addDeathTrain(s, PermadeathTimings.REAL30, 60); // 2h45m
        run(s, 45 * MINUTE, true);
        // the server stops: nothing runs; the persisted remaining time is the only state
        long persisted = s.deathTrainRemainingMillis;
        ProgressionState reloaded = new ProgressionState();
        reloaded.deathTrainRemainingMillis = persisted;
        assertEquals(2 * HOUR, reloaded.deathTrainRemainingMillis);
        run(reloaded, 2 * HOUR, true);
        assertEquals(0L, reloaded.deathTrainRemainingMillis);
    }

    @Test
    void shulkerEventDurationAndNoDuplicates() {
        ProgressionState s = new ProgressionState();
        assertTrue(CampaignTimers.startShulkerEvent(s, PermadeathTimings.GAME60));
        assertFalse(CampaignTimers.startShulkerEvent(s, PermadeathTimings.GAME60), "already running");
        int[] c = run(s, 10 * MINUTE - TICK, true);
        assertEquals(TICK, s.shulkerEventRemainingMillis);
        assertEquals(0, c[1]);
        c = run(s, TICK, true);
        assertEquals(1, c[1]);
        assertEquals(0L, s.shulkerEventRemainingMillis);
        assertTrue(CampaignTimers.startShulkerEvent(s, PermadeathTimings.REAL30));
        assertEquals(2 * HOUR, s.shulkerEventRemainingMillis);
    }

    @Test
    void lifeOrbCountdownExpiresExactlyOnce() {
        ProgressionState s = new ProgressionState();
        CampaignTimers.startLifeOrbCountdown(s, PermadeathTimings.GAME60);
        int[] c = run(s, 20 * MINUTE - TICK, true);
        assertFalse(s.lifeOrbActive);
        assertEquals(0, c[2]);
        c = run(s, 5 * MINUTE, true);
        assertEquals(1, c[2]);
        assertTrue(s.lifeOrbActive);
        assertEquals(-1L, s.lifeOrbRemainingMillis);
        CampaignTimers.clearLifeOrb(s);
        assertFalse(s.lifeOrbActive);
    }

    @Test
    void finalTimerOnlyRunsWhileActive() {
        ProgressionState s = new ProgressionState();
        s.finalPhaseRemainingMillis = 5 * MINUTE;
        run(s, MINUTE, true);
        assertEquals(5 * MINUTE, s.finalPhaseRemainingMillis, "NOT_STARTED: nothing runs");
        s.finalPhaseState = FinalPhaseState.ACTIVE;
        int[] c = run(s, 5 * MINUTE, true);
        assertEquals(1, c[3]);
    }
}
