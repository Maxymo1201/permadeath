package com.serthekiller.permadeath.core.legacy;

import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.ProgressionState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFabricStateTest {
    private static final UUID A = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");

    @Test
    void parsesAllLegacyFilesAndAppliesGame60() {
        LegacyFabricState legacy = new LegacyFabricState();
        legacy.parseDateFile("01/10/2026\n", LocalDate.of(2026, 10, 26)); // Fabric: 25 days between + 1 = 26
        legacy.parseStormFile("true\n72000");
        legacy.parseWitherFile(A + ":36000\n");
        legacy.parseLifeOrbFile("-1\nfalse");
        legacy.parseMikecrackFile("true");
        legacy.parseHyperAppleJson("{\n  \"" + A + "\": 2\n}");
        assertEquals(26, legacy.legacyDay);

        ProgressionState state = new ProgressionState();
        long now = 1_800_000_000_000L;
        legacy.applyTo(state, ProgressionMode.GAME60, 500, now);
        assertTrue(state.initialized);
        assertTrue(state.legacyMigrated);
        assertEquals(26, state.maxEffectiveDay);
        assertEquals(474, state.baseWorldDay);
        // Fabric counted the storm in ticks paused while offline: the remaining time is kept as is (no profile factor).
        assertEquals(72000L * 50L, state.deathTrainRemainingMillis);
        assertEquals(36000L * 50L, state.witherRemainingMillis.get(A));
        assertEquals(-1L, state.lifeOrbRemainingMillis);
        assertTrue(state.mikecrackEnabled);
        assertEquals(2, state.hyperApplesConsumed.get(A));
    }

    @Test
    void lifeOrbDeadlineBecomesRemainingTime() {
        long now = 1_800_000_000_000L;
        LegacyFabricState running = new LegacyFabricState();
        running.parseLifeOrbFile((now + 30L * 60_000L) + "\nfalse");
        ProgressionState a = new ProgressionState();
        running.applyTo(a, ProgressionMode.REAL30, 0, now);
        assertEquals(30L * 60_000L, a.lifeOrbRemainingMillis);
        assertFalse(a.lifeOrbActive);

        LegacyFabricState capped = new LegacyFabricState();
        capped.parseLifeOrbFile((now + 7L * 3_600_000L) + "\nfalse");
        ProgressionState b = new ProgressionState();
        capped.applyTo(b, ProgressionMode.GAME60, 0, now);
        assertEquals(20L * 60_000L, b.lifeOrbRemainingMillis, "capped at the GAME60 countdown");

        LegacyFabricState expired = new LegacyFabricState();
        expired.parseLifeOrbFile((now - 1L) + "\nfalse");
        ProgressionState c = new ProgressionState();
        expired.applyTo(c, ProgressionMode.REAL30, 0, now);
        assertTrue(c.lifeOrbActive, "an expired deadline stays expired");
        assertEquals(-1L, c.lifeOrbRemainingMillis);
    }

    @Test
    void appliesReal30PreservingDayAndCapsAt60() {
        LegacyFabricState legacy = new LegacyFabricState();
        legacy.parseDateFile("01/01/2026", LocalDate.of(2026, 6, 1)); // way beyond 70 in Fabric terms
        assertEquals(60, legacy.legacyDay);
        ProgressionState state = new ProgressionState();
        long now = 1_800_000_000_000L;
        legacy.applyTo(state, ProgressionMode.REAL30, 0, now);
        assertEquals(60, state.maxEffectiveDay);
        assertEquals(now - 60 * PermadeathCalendar.REAL30_MILLIS_PER_DAY, state.startEpochMillis);
    }
}
