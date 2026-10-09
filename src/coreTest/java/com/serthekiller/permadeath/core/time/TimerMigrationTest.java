package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.ProgressionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.serthekiller.permadeath.core.time.PermadeathTimings.HOUR;
import static com.serthekiller.permadeath.core.time.PermadeathTimings.MINUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerMigrationTest {
    private static final long NOW = 1_800_000_000_000L;

    private static ProgressionState v1(int day, long stormEnd, long orbDeadline, boolean orbActive, long shulkerEnd) {
        ProgressionState s = new ProgressionState();
        s.formatVersion = 1;
        s.initialized = true;
        s.maxEffectiveDay = day;
        s.executedMilestones.addAll(List.of(0, 10, 20, 25, 30, 40, 50));
        s.lifeOrbActive = orbActive;
        s.legacyTimers = new ProgressionState.LegacyTimers(stormEnd, orbDeadline, shulkerEnd);
        return s;
    }

    @Test
    void runningStormKeepsItsObservableTimeWithoutRescaling() {
        ProgressionState s = v1(45, NOW + 3 * HOUR, -1L, false, 0L);
        UUID player = UUID.randomUUID();
        s.witherRemainingMillis.put(player, 12345L);
        List<String> log = TimerMigration.migrate(s, PermadeathTimings.GAME60, NOW);
        assertEquals(3 * HOUR, s.deathTrainRemainingMillis, "not divided by 72 again");
        assertEquals(ProgressionState.CURRENT_FORMAT_VERSION, s.formatVersion);
        assertEquals(1, s.migratedFromVersion);
        assertEquals(NOW, s.migrationEpochMillis);
        assertFalse(log.isEmpty());
        assertEquals(12345L, s.witherRemainingMillis.get(player), "other values untouched");
        assertEquals(7, s.executedMilestones.size(), "milestones kept");
        assertNull(s.legacyTimers);
        assertEquals(FinalPhaseState.NOT_STARTED, s.finalPhaseState);
    }

    @Test
    void finishedTimersAreNotReapplied() {
        ProgressionState s = v1(45, NOW - 1L, -1L, false, NOW - 5L);
        TimerMigration.migrate(s, PermadeathTimings.REAL30, NOW);
        assertEquals(0L, s.deathTrainRemainingMillis);
        assertEquals(0L, s.shulkerEventRemainingMillis);
        assertEquals(-1L, s.lifeOrbRemainingMillis);
    }

    @Test
    void runningLifeOrbOnD60StartsAMatchingFinalChallenge() {
        ProgressionState s = v1(60, 0L, NOW + HOUR, false, NOW + 30 * MINUTE);
        TimerMigration.migrate(s, PermadeathTimings.REAL30, NOW);
        assertEquals(HOUR, s.lifeOrbRemainingMillis);
        assertEquals(30 * MINUTE, s.shulkerEventRemainingMillis);
        assertEquals(FinalPhaseState.ACTIVE, s.finalPhaseState);
        assertEquals(HOUR + 2 * HOUR, s.finalPhaseRemainingMillis, "orb left + (6 h - 4 h)");
    }

    @Test
    void oldLongCountdownIsCappedToTheProfile() {
        ProgressionState s = v1(60, 0L, NOW + 7 * HOUR, false, 0L);
        TimerMigration.migrate(s, PermadeathTimings.GAME60, NOW);
        assertEquals(20 * MINUTE, s.lifeOrbRemainingMillis);
        assertEquals(30 * MINUTE, s.finalPhaseRemainingMillis);
    }

    @Test
    void expiredLifeOrbStaysActiveAndOnlyThePostDeadlinePartIsLeft() {
        ProgressionState s = v1(60, 0L, NOW - HOUR, false, 0L);
        TimerMigration.migrate(s, PermadeathTimings.GAME60, NOW);
        assertTrue(s.lifeOrbActive);
        assertEquals(-1L, s.lifeOrbRemainingMillis);
        assertEquals(FinalPhaseState.ACTIVE, s.finalPhaseState);
        assertEquals(10 * MINUTE, s.finalPhaseRemainingMillis, "30 min - 20 min");

        ProgressionState active = v1(60, 0L, -1L, true, 0L);
        TimerMigration.migrate(active, PermadeathTimings.REAL30, NOW);
        assertTrue(active.lifeOrbActive, "the penalty is not applied a second time, only kept");
        assertEquals(2 * HOUR, active.finalPhaseRemainingMillis);
    }

    @Test
    void d60WithoutCountdownWaitsForTheNormalStart() {
        ProgressionState s = v1(60, 0L, -1L, false, 0L);
        TimerMigration.migrate(s, PermadeathTimings.GAME60, NOW);
        assertEquals(FinalPhaseState.NOT_STARTED, s.finalPhaseState);
    }

    @Test
    void currentFormatIsNotMigratedTwice() {
        ProgressionState s = v1(45, NOW + HOUR, -1L, false, 0L);
        TimerMigration.migrate(s, PermadeathTimings.GAME60, NOW);
        long after = s.deathTrainRemainingMillis;
        s.legacyTimers = new ProgressionState.LegacyTimers(NOW + 9 * HOUR, -1L, 0L);
        assertTrue(TimerMigration.migrate(s, PermadeathTimings.GAME60, NOW + MINUTE).isEmpty());
        assertEquals(after, s.deathTrainRemainingMillis);
        ProgressionState fresh = new ProgressionState();
        assertTrue(TimerMigration.migrate(fresh, PermadeathTimings.GAME60, NOW).isEmpty(), "new worlds use format 2 directly");
    }
}
