package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.rules.DayRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.serthekiller.permadeath.core.time.PermadeathTimings.HOUR;
import static com.serthekiller.permadeath.core.time.PermadeathTimings.MINUTE;
import static com.serthekiller.permadeath.core.time.PermadeathTimings.SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PermadeathTimingsTest {

    @Test
    void profileDurations() {
        PermadeathTimings g = PermadeathTimings.forMode(ProgressionMode.GAME60);
        assertSame(PermadeathTimings.GAME60, g);
        assertEquals(8 * MINUTE, g.witherIntervalMillis());
        assertEquals(20 * MINUTE, g.lifeOrbCountdownMillis());
        assertEquals(10 * MINUTE, g.shulkerEventMillis());
        assertEquals(10 * MINUTE, g.beginningCurseMillis());
        assertEquals(10 * MINUTE, g.beginningBlessingMillis());
        assertEquals(30 * MINUTE, g.finalPhaseMillis());

        PermadeathTimings r = PermadeathTimings.forMode(ProgressionMode.REAL30);
        assertSame(PermadeathTimings.REAL30, r);
        assertEquals(30 * MINUTE, r.witherIntervalMillis());
        assertEquals(4 * HOUR, r.lifeOrbCountdownMillis());
        assertEquals(2 * HOUR, r.shulkerEventMillis());
        assertEquals(6 * HOUR, r.beginningCurseMillis());
        assertEquals(6 * HOUR, r.beginningBlessingMillis());
        assertEquals(6 * HOUR, r.finalPhaseMillis());
        assertEquals(714 * HOUR, r.strictFinalDayElapsedMillis());
    }

    @ParameterizedTest(name = "D{0}: original {1} min, GAME60 {2} s, REAL30 {3} min")
    @CsvSource({
            // reference table of the specification
            "0,60,60,30", "1,60,60,30", "10,600,500,300", "20,1200,1000,600", "25,60,60,30", "30,360,300,180",
            "40,960,800,480", "50,30,60,15", "55,180,150,90", "60,330,275,165",
            // boundaries
            "24,1440,1200,720", "49,1500,1250,750", "59,300,250,150"
    })
    void deathTrainReferenceTable(int day, long originalMinutes, long game60Seconds, long real30Minutes) {
        assertEquals(originalMinutes * MINUTE, DayRules.deathTrainDurationMillis(day), "original");
        assertEquals(game60Seconds * SECOND, PermadeathTimings.GAME60.deathTrainMillis(day), "GAME60");
        assertEquals(real30Minutes * MINUTE, PermadeathTimings.REAL30.deathTrainMillis(day), "REAL30");
    }

    @Test
    void deathTrainEveryDayFollowsTheFormulaExactly() {
        for (int day = 0; day <= 60; day++) {
            long original = DayRules.deathTrainDurationMillis(day);
            assertEquals(0L, original % (30 * MINUTE), "historical durations are multiples of 30 min (exact division) on D" + day);
            assertEquals(Math.max(60 * SECOND, original / 72), PermadeathTimings.GAME60.deathTrainMillis(day), "GAME60 D" + day);
            assertEquals(original / 2, PermadeathTimings.REAL30.deathTrainMillis(day), "REAL30 D" + day);
            assertEquals(0L, original % 72, "GAME60 division is exact on D" + day);
        }
    }

    @Test
    void deathTrainTransitions() {
        // D24 -> D25 (cycle restarts), D49 -> D50 (30 min units), D59 -> D60
        assertEquals(20 * MINUTE, PermadeathTimings.GAME60.deathTrainMillis(24));
        assertEquals(60 * SECOND, PermadeathTimings.GAME60.deathTrainMillis(25));
        assertEquals(1250 * SECOND, PermadeathTimings.GAME60.deathTrainMillis(49));
        assertEquals(60 * SECOND, PermadeathTimings.GAME60.deathTrainMillis(50));
        assertEquals(250 * SECOND, PermadeathTimings.GAME60.deathTrainMillis(59));
        assertEquals(275 * SECOND, PermadeathTimings.GAME60.deathTrainMillis(60));
        assertEquals(12 * HOUR, PermadeathTimings.REAL30.deathTrainMillis(24));
        assertEquals(30 * MINUTE, PermadeathTimings.REAL30.deathTrainMillis(25));
        assertEquals(15 * MINUTE, PermadeathTimings.REAL30.deathTrainMillis(50));
        assertEquals(165 * MINUTE, PermadeathTimings.REAL30.deathTrainMillis(60));
    }
}
