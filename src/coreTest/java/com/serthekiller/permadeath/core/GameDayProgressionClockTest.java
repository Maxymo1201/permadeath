package com.serthekiller.permadeath.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDayProgressionClockTest {
    private static final long DAY = 24_000L;

    private static GameDayProgressionClock start(ProgressionState state, AtomicLong time, List<String> warnings) {
        GameDayProgressionClock c = new GameDayProgressionClock(state, time::get, warnings::add);
        c.initialize();
        return c;
    }

    @ParameterizedTest(name = "D{0} -> D{1}")
    @CsvSource({"9,10", "19,20", "24,25", "29,30", "39,40", "49,50", "59,60"})
    void boundaries(int before, int after) {
        AtomicLong time = new AtomicLong(0);
        ProgressionState state = new ProgressionState();
        GameDayProgressionClock c = start(state, time, new ArrayList<>());
        time.set(before * DAY + DAY - 1);
        c.update();
        assertEquals(before, c.getDay());
        time.set(after * DAY);
        ProgressionClock.DayChange change = c.update();
        assertEquals(after, c.getDay());
        assertTrue(change.changed());
        assertEquals(before, change.previousDay());
    }

    @Test
    void existingWorldBaseDay347() {
        AtomicLong time = new AtomicLong(347 * DAY + 6000);
        ProgressionState state = new ProgressionState();
        GameDayProgressionClock c = start(state, time, new ArrayList<>());
        assertEquals(347, state.baseWorldDay);
        long[] worldDays = {347, 357, 367, 372, 377, 387, 397, 407};
        int[] pd = {0, 10, 20, 25, 30, 40, 50, 60};
        for (int i = 0; i < worldDays.length; i++) {
            time.set(worldDays[i] * DAY + 1000);
            c.update();
            assertEquals(pd[i], c.getDay(), "world day " + worldDays[i]);
        }
        time.set(500 * DAY);
        c.update();
        assertEquals(60, c.getDay(), "D60 is final, never D70");
    }

    @Test
    void timeRollbackNeverMovesBack() {
        AtomicLong time = new AtomicLong(0);
        ProgressionState state = new ProgressionState();
        List<String> warnings = new ArrayList<>();
        GameDayProgressionClock c = start(state, time, warnings);
        time.set(40 * DAY + 100);
        c.update();
        assertEquals(40, c.getDay());
        time.set(30 * DAY); // /time set back
        c.update();
        assertEquals(40, c.getDay());
        assertFalse(warnings.isEmpty());
        // restart keeps the maximum
        GameDayProgressionClock restarted = start(state, time, warnings);
        assertEquals(40, restarted.getDay());
        // moving forward again past the maximum progresses
        time.set(41 * DAY);
        restarted.update();
        assertEquals(41, restarted.getDay());
    }

    @Test
    void jumpExecutesIntermediateMilestonesOnce() {
        AtomicLong time = new AtomicLong(0);
        ProgressionState state = new ProgressionState();
        GameDayProgressionClock c = start(state, time, new ArrayList<>());
        time.set(9 * DAY);
        c.update();
        MilestoneTracker.pending(state, c.getDay()).forEach(m -> MilestoneTracker.markExecuted(state, m));
        assertEquals(List.of(0), new ArrayList<>(state.executedMilestones));
        time.set(31 * DAY + 5);
        c.update();
        assertEquals(31, c.getDay());
        List<Integer> pending = MilestoneTracker.pending(state, c.getDay());
        assertEquals(List.of(10, 20, 25, 30), pending);
        pending.forEach(m -> MilestoneTracker.markExecuted(state, m));
        assertTrue(MilestoneTracker.pending(state, c.getDay()).isEmpty(), "idempotent");
    }

    @Test
    void sleepingAdvancesTheCalendar() {
        AtomicLong time = new AtomicLong(12 * DAY + 13_000);
        ProgressionState state = new ProgressionState();
        GameDayProgressionClock c = start(state, time, new ArrayList<>());
        c.setDay(9);
        // vanilla sleeping: dayTime = (dayTime + 24000) - (dayTime + 24000) % 24000
        long t = time.get() + DAY;
        time.set(t - t % DAY);
        c.update();
        assertEquals(10, c.getDay());
    }

    @Test
    void setDayAdjustsBaseSafely() {
        AtomicLong time = new AtomicLong(1000 * DAY + 500);
        ProgressionState state = new ProgressionState();
        GameDayProgressionClock c = start(state, time, new ArrayList<>());
        c.setDay(50);
        assertEquals(50, c.getDay());
        assertEquals(950, state.baseWorldDay);
        c.setDay(0);
        assertEquals(0, c.getDay());
        assertEquals(1000, state.baseWorldDay);
        c.setDay(75);
        assertEquals(60, c.getDay());
    }

    @Test
    void movingWorldFromReal30PreservesDay() {
        AtomicLong time = new AtomicLong(200 * DAY);
        ProgressionState state = new ProgressionState();
        state.initialized = true;
        state.mode = ProgressionMode.REAL30;
        state.maxEffectiveDay = 42;
        GameDayProgressionClock c = start(state, time, new ArrayList<>());
        assertEquals(42, c.getDay());
        assertEquals(158, state.baseWorldDay);
    }
}
