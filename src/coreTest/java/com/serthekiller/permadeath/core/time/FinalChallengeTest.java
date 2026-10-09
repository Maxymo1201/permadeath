package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.FinalParticipant;
import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.serthekiller.permadeath.core.time.PermadeathTimings.HOUR;
import static com.serthekiller.permadeath.core.time.PermadeathTimings.MINUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalChallengeTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final long NOW = 1_800_000_000_000L;
    private static final long SECOND = 1_000L;

    /** Simulates {@code millis} of play in 1 s steps; the player holds the orb from {@code orbAt} on. */
    private static void play(ProgressionState s, long fromMillis, long millis, boolean eligible, long orbAt, int[] events) {
        for (long t = fromMillis; t < fromMillis + millis; t += SECOND) {
            FinalChallenge.observe(s, A, "Alex", eligible, t >= orbAt);
            CampaignTimers.Step step = CampaignTimers.advance(s, SECOND, eligible, false);
            if (step.lifeOrbExpired()) {
                events[0]++;
                events[1] = (int) ((t + SECOND) / SECOND);
            }
            if (step.finalPhaseEnded()) {
                FinalChallenge.observe(s, A, "Alex", eligible, t >= orbAt);
                FinalChallenge.finish(s, NOW + t);
                events[2]++;
                events[3] = (int) ((t + SECOND) / SECOND);
            }
        }
    }

    @Test
    void game60TimelineVictory() {
        ProgressionState s = new ProgressionState();
        assertFalse(FinalChallenge.canStart(s, 59, true));
        assertFalse(FinalChallenge.canStart(s, PermadeathCalendar.FINAL_DAY, false), "needs an eligible survivor online");
        assertTrue(FinalChallenge.canStart(s, PermadeathCalendar.FINAL_DAY, true));
        FinalChallenge.start(s, PermadeathTimings.GAME60, NOW);
        assertEquals(FinalPhaseState.ACTIVE, s.finalPhaseState);
        assertEquals(30 * MINUTE, s.finalPhaseRemainingMillis);
        assertEquals(20 * MINUTE, s.lifeOrbRemainingMillis);
        int[] events = new int[4];
        play(s, 0, 31 * MINUTE, true, 15 * MINUTE, events);
        assertEquals(1, events[0], "Life Orb deadline once");
        assertEquals(20 * 60, events[1], "Life Orb deadline at T=20:00");
        assertEquals(1, events[2], "final evaluated once");
        assertEquals(30 * 60, events[3], "final at T=30:00");
        assertEquals(FinalPhaseState.COMPLETED, s.finalPhaseState);
        assertEquals(FinalParticipant.Result.VICTORY, s.finalParticipants.get(A).result);
        assertEquals(FinalPhaseState.COMPLETED, FinalChallenge.finish(s, NOW), "no double finalization");
        assertFalse(FinalChallenge.canStart(s, 60, true), "a finished challenge never restarts by itself");
    }

    @Test
    void game60DefeatWithoutLifeOrbInTime() {
        ProgressionState s = new ProgressionState();
        FinalChallenge.start(s, PermadeathTimings.GAME60, NOW);
        int[] events = new int[4];
        play(s, 0, 31 * MINUTE, true, 25 * MINUTE, events); // orb only after the deadline
        assertEquals(FinalPhaseState.FAILED, s.finalPhaseState);
        FinalParticipant p = s.finalParticipants.get(A);
        assertFalse(p.lifeOrbBeforeDeadline);
        assertTrue(p.holdingLifeOrb);
        assertEquals(FinalParticipant.Result.DEFEAT, p.result);
    }

    @Test
    void real30TimelineAndPausesDoNotConsumeTime() {
        ProgressionState s = new ProgressionState();
        FinalChallenge.start(s, PermadeathTimings.REAL30, NOW);
        int[] events = new int[4];
        play(s, 0, 3 * HOUR, true, 0, events);
        play(s, 3 * HOUR, 10 * HOUR, false, 0, events); // nobody online: paused
        assertEquals(HOUR, s.lifeOrbRemainingMillis, "the pause consumed nothing");
        assertEquals(3 * HOUR, s.finalPhaseRemainingMillis);
        play(s, 3 * HOUR, 3 * HOUR + 10 * SECOND, true, 0, events);
        assertEquals(1, events[0]);
        assertEquals(4 * 3600, events[1], "Life Orb deadline at 4 h of active time");
        assertEquals(1, events[2]);
        assertEquals(6 * 3600, events[3], "final at 6 h of active time");
        assertEquals(FinalPhaseState.COMPLETED, s.finalPhaseState);
    }

    @Test
    void restartDoesNotRestartTheChallenge() {
        ProgressionState s = new ProgressionState();
        FinalChallenge.start(s, PermadeathTimings.GAME60, NOW);
        play(s, 0, 12 * MINUTE, true, 0, new int[4]);
        // persisted values after a restart
        assertEquals(18 * MINUTE, s.finalPhaseRemainingMillis);
        assertEquals(8 * MINUTE, s.lifeOrbRemainingMillis);
        assertFalse(FinalChallenge.canStart(s, 60, true), "ACTIVE is not started again");
    }

    @Test
    void everyParticipantEliminatedFails() {
        ProgressionState s = new ProgressionState();
        FinalChallenge.start(s, PermadeathTimings.REAL30, NOW);
        FinalChallenge.observe(s, A, "Alex", true, false);
        FinalChallenge.observe(s, B, "Bea", true, true);
        assertFalse(FinalChallenge.markEliminated(s, A));
        assertTrue(FinalChallenge.markEliminated(s, B));
        assertEquals(FinalPhaseState.FAILED, FinalChallenge.fail(s, NOW));
        assertEquals(FinalParticipant.Result.DEFEAT, s.finalParticipants.get(B).result);
        assertEquals(FinalPhaseState.FAILED, FinalChallenge.finish(s, NOW), "no double finalization");
    }

    @Test
    void perPlayerEvaluationOnReal30() {
        ProgressionState s = new ProgressionState();
        FinalChallenge.start(s, PermadeathTimings.REAL30, NOW);
        FinalChallenge.observe(s, A, "Alex", true, true);  // orb in time, keeps it, goes offline alive
        FinalChallenge.observe(s, B, "Bea", true, false);  // never gets it
        FinalChallenge.observe(s, UUID.randomUUID(), "Spectator", false, true); // never eligible: not a participant
        assertEquals(2, s.finalParticipants.size());
        s.lifeOrbActive = true;
        s.lifeOrbRemainingMillis = -1L;
        assertEquals(FinalPhaseState.COMPLETED, FinalChallenge.finish(s, NOW));
        assertEquals(FinalParticipant.Result.VICTORY, s.finalParticipants.get(A).result);
        assertEquals(FinalParticipant.Result.DEFEAT, s.finalParticipants.get(B).result);
    }

    @Test
    void strictCampaignFollowsTheCalendar() {
        ProgressionState s = new ProgressionState();
        s.startEpochMillis = NOW;
        long d60 = NOW + 714 * HOUR;
        FinalChallenge.start(s, PermadeathTimings.REAL30, d60 + 2 * HOUR); // server started 2 h after D60 began
        FinalChallenge.syncToCalendar(s, PermadeathTimings.REAL30, d60 + 2 * HOUR);
        assertEquals(4 * HOUR, s.finalPhaseRemainingMillis, "ends at hour 720");
        assertEquals(2 * HOUR, s.lifeOrbRemainingMillis, "deadline at hour 718");
        FinalChallenge.syncToCalendar(s, PermadeathTimings.REAL30, NOW + 721 * HOUR);
        CampaignTimers.Step step = CampaignTimers.advance(s, 0L, false, true);
        assertTrue(step.lifeOrbExpired());
        assertTrue(step.finalPhaseEnded());
    }

    @Test
    void resetAllowsANewChallenge() {
        ProgressionState s = new ProgressionState();
        FinalChallenge.start(s, PermadeathTimings.GAME60, NOW);
        FinalChallenge.observe(s, A, "Alex", true, false);
        FinalChallenge.reset(s);
        assertEquals(FinalPhaseState.NOT_STARTED, s.finalPhaseState);
        assertTrue(s.finalParticipants.isEmpty());
        assertTrue(FinalChallenge.canStart(s, 60, true));
    }
}
