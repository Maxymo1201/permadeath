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

    /**
     * Damage of one drowning hit taken by a player: 5 on D50-59 and 10 from D60 (Permadeath plugin EntityEvents,
     * both editions), -1 = vanilla (2). The faster air loss above is applied on top of this.
     */
    public static float drowningDamage(int day) {
        if (day >= 60) {
            return 10.0F;
        }
        if (day >= 50) {
            return 5.0F;
        }
        return -1.0F;
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

    /** D50-59 Death Train mobs are also immune to fire (plugin deathTrainEffects; not on D60). */
    public static boolean deathTrainFireResistance(int day) {
        return day >= 50 && day < 60;
    }

    // ---------------------------------------------------------------- weather / players (plugin tick loop)
    /**
     * D50+ random levitation: players under open sky while it rains get Levitation I with probability
     * 1/{@value #RANDOM_LEVITATION_ONE_IN} every {@value #RANDOM_LEVITATION_PERIOD_TICKS} ticks (the plugin
     * player loop), for 3-19 s.
     */
    public static final int RANDOM_LEVITATION_FROM_DAY = 50;
    public static final int RANDOM_LEVITATION_ONE_IN = 10000;
    public static final int RANDOM_LEVITATION_PERIOD_TICKS = 20;

    /** Levitation duration in ticks for a roll {@code r} in [0, 17): (3 + r) seconds. */
    public static int randomLevitationTicks(int roll) {
        return (3 + roll) * 20;
    }

    /** D60: ender pearl cooldown applied when the pearl lands (plugin PlayerTeleportEvent, 6 s). */
    public static final int D60_PEARL_COOLDOWN_TICKS = 120;

    /** D60: Slowness III left by soul sand (plugin, 30 s). */
    public static final int D60_SOUL_SAND_SLOWNESS_TICKS = 600;

    /** D50+: an exploding bed resets the phantom counter with this percent chance (plugin, 10 %). */
    public static final int D50_PHANTOM_RESET_PERCENT = 10;

    // ---------------------------------------------------------------- mobs (plugin SpawnListener)
    /** Phantom size from D20: 9, and 18 from D50. */
    public static int phantomSize(int day) {
        return day >= 50 ? 18 : 9;
    }

    /**
     * D50+: a phantom spawn also brings four Ender Ghasts when {@code nextInt(101) <= value}: 1 on D50-59
     * (2/101), 25 on D60 (26/101). The phantom itself stays.
     */
    public static int phantomGhastRoll(int day) {
        return day >= 60 ? 25 : 1;
    }

    /** Zombie Gigante: one plains zombie in {@code N} (D50-59 1/500, D60 1/125). */
    public static int giantOneIn(int day) {
        return day >= 60 ? 125 : 500;
    }

    /** Wither Skeleton Emperador: one Nether wither skeleton in {@code N} (D50-59 1/50, D60 1/13). */
    public static int emperorOneIn(int day) {
        return day >= 60 ? 13 : 50;
    }

    /** D40+ pigman classes: {@code nextInt(99) + 1 <= value} (D40-49 5, D50-59 20). */
    public static int pigmanClassChance(int day) {
        return day >= 50 ? 20 : 5;
    }

    /** Custom netherite armour drops: only D25-29, only mobs killed by a player, 10 % per piece. */
    public static boolean netheriteArmorDropDay(int day) {
        return day >= 25 && day < 30;
    }

    public static final int NETHERITE_ARMOR_DROP_PERCENT = 10;

    /** D40-49 supernova cats: 30 s fuse, explosion power 200 (plugin config default). */
    public static final int SUPERNOVA_FUSE_TICKS = 600;
    public static final float SUPERNOVA_POWER = 200.0F;

    /** Attack damage of D20+ hostile passive mobs that have no attack attribute (plugin, 8). */
    public static final double HOSTILE_PASSIVE_ATTACK_DAMAGE = 8.0;

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

    /**
     * D60 periodic Wither (one per player per interval of presence in the Overworld) and Life Orb (-16 max HP for
     * players without it once the countdown ends). The interval and the countdown depend on the build profile:
     * see {@code PermadeathTimings}.
     */
    public static final int WITHER_FROM_DAY = 60;
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
