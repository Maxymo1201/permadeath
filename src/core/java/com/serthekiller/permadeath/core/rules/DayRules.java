package com.serthekiller.permadeath.core.rules;

/**
 * Day-indexed numeric rules that are not specific to a single handler. Every threshold of the port is
 * expressed here with an explicit, inclusive "from day" so that off-by-one errors are testable.
 */
public final class DayRules {
    private DayRules() {
    }

    // ---------------------------------------------------------------- mob spawning (D10)
    /** Vanilla MobCategory.MONSTER cap. */
    public static final int VANILLA_MONSTER_CAP = 70;
    /** Doubled hostile mob cap starts on D10 (Fabric had "day > 10", i.e. D11: corrected). */
    public static final int DOUBLE_MOBS_FROM_DAY = 10;

    public static int monsterCap(int day) {
        return day >= DOUBLE_MOBS_FROM_DAY ? VANILLA_MONSTER_CAP * 2 : VANILLA_MONSTER_CAP;
    }

    public static int mobMultiplier(int day) {
        return day >= DOUBLE_MOBS_FROM_DAY ? 2 : 1;
    }

    // ---------------------------------------------------------------- drowning (D50 / D60)
    /** Air consumption multiplier: D0-49 x1 (vanilla), D50-59 x5, D60+ x10. */
    public static int drowningMultiplier(int day) {
        if (day >= 60) {
            return 10;
        }
        if (day >= 50) {
            return 5;
        }
        return 1;
    }

    /**
     * Scales the vanilla air consumption of one tick. If vanilla would not consume air (Respiration roll,
     * Water Breathing, ...), nothing is forced.
     *
     * @param vanillaDelta air vanilla would remove this tick (currentAir - vanillaNextAir)
     */
    public static int scaledAirConsumption(int vanillaDelta, int day) {
        if (vanillaDelta <= 0) {
            return vanillaDelta;
        }
        return vanillaDelta * drowningMultiplier(day);
    }

    // ---------------------------------------------------------------- blindness under rain (D40 / D50)
    /**
     * Probability denominator ("1 in N per tick") of getting Blindness while exposed to Death Train rain.
     * D40-49: 1/10000, D50+: 1/5000 (no gap at D60). 0 = rule inactive.
     */
    public static int rainBlindnessOneIn(int day) {
        if (day >= 50) {
            return 5000;
        }
        if (day >= 40) {
            return 10000;
        }
        return 0;
    }

    // ---------------------------------------------------------------- Death Train
    /**
     * Duration of the storm added by one death. Matches both the Fabric jar and PermaDeathCore for D1-D60:
     * D1-24: day hours; D25-49: (day-24) hours; D50: 30 min; D51-60: (day-49) x 30 min. D0 = 1 h (Fabric).
     */
    public static long deathTrainDurationMillis(int day) {
        long dayMod = day % 25;
        long units;
        if (dayMod == 0) {
            units = 1;
        } else if (day <= 25) {
            units = dayMod;
        } else {
            units = dayMod + 1;
        }
        long unitMillis = day >= 50 ? 30L * 60_000L : 60L * 60_000L;
        return units * unitMillis;
    }

    /** Death Train mob buff level (Strength/Resistance/Speed amplifier) or -1 if none: D25-49 → 0 (I), D50+ → 1 (II). */
    public static int deathTrainBuffAmplifier(int day) {
        int level = day / 25;
        return level <= 0 ? -1 : Math.min(level, 2) - 1;
    }

    /** From D50 a Death Train disables natural regeneration ("modo UHC"). */
    public static boolean deathTrainDisablesRegeneration(int day) {
        return day >= 50;
    }

    // ---------------------------------------------------------------- misc
    /** PvP is enabled from D40 (Fabric PvpChanges). */
    public static boolean pvpEnabled(int day) {
        return day >= 40;
    }

    /** The End can be entered from D30. */
    public static boolean endOpen(int day) {
        return day >= 30;
    }

    /** Hyper Golden Apple +: cannot be eaten before D40, max 1 before D60, max 2 from D60. */
    public static int maxHyperApples(int day) {
        if (day < 40) {
            return 0;
        }
        return day < 60 ? 1 : 2;
    }

    /** Max health penalty in HP applied to every player: D40 -8, D60 another -8. */
    public static double maxHealthPenalty(int day) {
        if (day >= 60) {
            return 16.0;
        }
        if (day >= 40) {
            return 8.0;
        }
        return 0.0;
    }

    /** D60 periodic Wither: one Wither per player every 60 real minutes of presence in the Overworld. */
    public static final long WITHER_INTERVAL_MILLIS = 60L * 60_000L;
    public static final int WITHER_FROM_DAY = 60;

    /** D60 Life Orb: 8 real hours to obtain it, then -16 max HP for players without it. */
    public static final long LIFE_ORB_COUNTDOWN_MILLIS = 8L * 60L * 60_000L;
    public static final int LIFE_ORB_FROM_DAY = 60;
    public static final double LIFE_ORB_PENALTY_HP = 16.0;

    /** Inventory slots locked from D40 (5 slots) and the extra D60 slots. */
    public static final int[] LOCKED_SLOTS_D40 = {4, 13, 22, 31, 40};
    public static final int[] EXTRA_LOCKED_SLOTS_D60 = {7, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 23, 24, 25, 26, 27, 28, 29, 30, 32, 33, 34};

    /** Recipe/datapack condition buckets: reload when one of these thresholds is crossed. */
    public static final int[] RECIPE_THRESHOLDS = {40, 50, 60};

    public static int recipeBucket(int day) {
        int bucket = 0;
        for (int t : RECIPE_THRESHOLDS) {
            if (day >= t) {
                bucket = t;
            }
        }
        return bucket;
    }
}
