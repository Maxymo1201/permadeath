package com.serthekiller.permadeath.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealTimeProgressionClockTest {
    private static final Instant T0 = Instant.parse("2026-03-28T22:00:00Z");

    private static RealTimeProgressionClock newClock(ProgressionState state, MutableClock clock, List<String> warnings) {
        RealTimeProgressionClock c = new RealTimeProgressionClock(state, clock, warnings::add);
        c.initialize();
        return c;
    }

    @ParameterizedTest(name = "T+{0}h{1}m => D{2}")
    @CsvSource({
            "0,0,0",
            "119,59,9",
            "120,0,10",
            "239,59,19",
            "240,0,20",
            "299,59,24",
            "300,0,25",
            "359,59,29",
            "360,0,30",
            "479,59,39",
            "480,0,40",
            "599,59,49",
            "600,0,50",
            "719,59,59",
            "720,0,60",
            "721,0,60",
            "1000,0,60"
    })
    void dayTable(long hours, long minutes, int expectedDay) {
        MutableClock clock = new MutableClock(T0, ZoneId.of("UTC"));
        ProgressionState state = new ProgressionState();
        RealTimeProgressionClock c = newClock(state, clock, new ArrayList<>());
        clock.advance(Duration.ofHours(hours).plusMinutes(minutes));
        c.update();
        assertEquals(expectedDay, c.getDay());
        assertTrue(c.getDay() <= 60, "never beyond D60");
    }

    @Test
    void milestoneInstantsAreExact() {
        MutableClock clock = new MutableClock(T0, ZoneId.of("UTC"));
        ProgressionState state = new ProgressionState();
        RealTimeProgressionClock c = newClock(state, clock, new ArrayList<>());
        int[] days = {0, 10, 20, 25, 30, 40, 50, 60};
        long[] hours = {0, 120, 240, 300, 360, 480, 600, 720};
        for (int i = 0; i < days.length; i++) {
            clock.set(T0.plus(Duration.ofHours(hours[i])));
            c.update();
            assertEquals(days[i], c.getDay(), "T+" + hours[i] + "h");
            clock.set(T0.plus(Duration.ofHours(hours[i])).minusMillis(1));
        }
        assertEquals(T0.plus(Duration.ofHours(720)), c.finalDayInstant());
        assertEquals(T0.plus(Duration.ofHours(300)), c.dayInstant(25));
    }

    @Test
    void serverOfflineTimeKeepsCounting() {
        MutableClock clock = new MutableClock(T0, ZoneId.of("UTC"));
        ProgressionState state = new ProgressionState();
        RealTimeProgressionClock c = newClock(state, clock, new ArrayList<>());
        clock.advance(Duration.ofHours(200));
        c.update();
        assertEquals(16, c.getDay());
        // "/stop": only the persisted state survives. 20 h later the server starts again.
        clock.advance(Duration.ofHours(20));
        RealTimeProgressionClock restarted = newClock(state, clock, new ArrayList<>());
        assertEquals(Duration.ofHours(220), restarted.getElapsed());
        assertEquals(18, restarted.getDay());
    }

    @Test
    void tpsHasNoInfluence() {
        // The clock never looks at ticks: updating 10 times or 10 000 times over 5 real days gives D10.
        MutableClock clock = new MutableClock(T0, ZoneId.of("UTC"));
        ProgressionState state = new ProgressionState();
        RealTimeProgressionClock c = newClock(state, clock, new ArrayList<>());
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofHours(12));
            c.update();
        }
        assertEquals(10, c.getDay());
        assertEquals(Duration.ofDays(5), c.getElapsed());
    }

    @Test
    void timezoneAndDstCannotChangeProgress() {
        Instant beforeDst = Instant.parse("2026-03-29T00:30:00Z");
        ProgressionState utcState = new ProgressionState();
        ProgressionState madridState = new ProgressionState();
        MutableClock utc = new MutableClock(beforeDst, ZoneId.of("UTC"));
        MutableClock madrid = new MutableClock(beforeDst, ZoneId.of("Europe/Madrid"));
        RealTimeProgressionClock a = newClock(utcState, utc, new ArrayList<>());
        RealTimeProgressionClock b = newClock(madridState, madrid, new ArrayList<>());
        // Crossing the 2026-03-29 DST change in Madrid: 12 real hours are still exactly one PD day.
        utc.advance(Duration.ofHours(12));
        madrid.advance(Duration.ofHours(12));
        a.update();
        b.update();
        assertEquals(1, a.getDay());
        assertEquals(1, b.getDay());
        assertEquals(a.getElapsed(), b.getElapsed());
    }

    @Test
    void clockRegressionNeverMovesBack() {
        MutableClock clock = new MutableClock(T0, ZoneId.of("UTC"));
        ProgressionState state = new ProgressionState();
        List<String> warnings = new ArrayList<>();
        RealTimeProgressionClock c = newClock(state, clock, warnings);
        clock.advance(Duration.ofHours(485));
        c.update();
        assertEquals(40, c.getDay());
        clock.advance(Duration.ofHours(-130)); // NTP/admin moves the host clock back to T+355h
        c.update();
        assertEquals(40, c.getDay());
        assertFalse(warnings.isEmpty(), "regression must be logged");
        // after a restart the persisted maximum still protects the day
        RealTimeProgressionClock restarted = newClock(state, clock, warnings);
        assertEquals(40, restarted.getDay());
        // once real time passes the old maximum it progresses again
        clock.advance(Duration.ofHours(130 + 115));
        restarted.update();
        assertEquals(50, restarted.getDay());
    }

    @Test
    void setDayIsCoherentAndPersistent() {
        MutableClock clock = new MutableClock(T0, ZoneId.of("UTC"));
        ProgressionState state = new ProgressionState();
        RealTimeProgressionClock c = newClock(state, clock, new ArrayList<>());
        c.setDay(25);
        assertEquals(25, c.getDay());
        assertEquals(Duration.ofHours(300), c.getElapsed());
        clock.advance(Duration.ofHours(12));
        c.update();
        assertEquals(26, c.getDay());
        // going back explicitly is allowed and resets milestones above the new day
        state.executedMilestones.add(30);
        c.setDay(10);
        assertEquals(10, c.getDay());
        assertFalse(state.executedMilestones.contains(30));
        c.setDay(99);
        assertEquals(60, c.getDay(), "clamped to the final day");
    }

    @Test
    void movingWorldFromGame60PreservesDay() {
        MutableClock clock = new MutableClock(T0, ZoneId.of("UTC"));
        ProgressionState state = new ProgressionState();
        state.initialized = true;
        state.mode = ProgressionMode.GAME60;
        state.maxEffectiveDay = 33;
        List<String> warnings = new ArrayList<>();
        RealTimeProgressionClock c = newClock(state, clock, warnings);
        assertEquals(33, c.getDay());
        assertEquals(ProgressionMode.REAL30, state.mode);
        assertFalse(warnings.isEmpty());
    }
}
