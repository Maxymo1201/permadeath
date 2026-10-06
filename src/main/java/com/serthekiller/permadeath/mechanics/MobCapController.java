package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.rules.DayRules;
import net.minecraft.world.entity.MobCategory;

/**
 * Doubled hostile mob cap from D10 (Fabric MobCapMixin returned 140 for SpawnGroup.MONSTER when day &gt; 10;
 * corrected to day &gt;= 10).
 *
 * <p>NeoForge 1.21.1 has no event to modify the per-category cap (NaturalSpawner reads
 * {@link MobCategory#getMaxInstancesPerChunk()} for both the global and the per-player local cap). The
 * field is made writable with an Access Transformer and set to an absolute value, so the multiplier is
 * applied exactly once (idempotent) and restored to vanilla when the server stops.</p>
 */
public final class MobCapController {
    private static final int VANILLA = DayRules.VANILLA_MONSTER_CAP;
    private static int applied = -1;

    private MobCapController() {
    }

    public static void apply(int day) {
        int cap = DayRules.monsterCap(day);
        if (cap != applied) {
            MobCategory.MONSTER.max = cap;
            applied = cap;
            PermadeathMod.LOGGER.info("[Permadeath] Hostile mob cap set to {} (day {})", cap, day);
        }
    }

    public static void restoreVanilla() {
        MobCategory.MONSTER.max = VANILLA;
        applied = -1;
    }

    public static int current() {
        return MobCategory.MONSTER.getMaxInstancesPerChunk();
    }
}
