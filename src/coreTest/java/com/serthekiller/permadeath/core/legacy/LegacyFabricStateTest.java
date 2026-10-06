package com.serthekiller.permadeath.core.legacy;

import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.ProgressionState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(now + 72000L * 50L, state.deathTrainEndEpochMillis);
        assertEquals(36000L * 50L, state.witherRemainingMillis.get(A));
        assertEquals(-1L, state.lifeOrbDeadlineEpochMillis);
        assertTrue(state.mikecrackEnabled);
        assertEquals(2, state.hyperApplesConsumed.get(A));
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
