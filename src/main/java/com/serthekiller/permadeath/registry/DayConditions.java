package com.serthekiller.permadeath.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Datapack load conditions (replacement of the Fabric {@code fabric:load_conditions} resource conditions).
 * Used as {@code "neoforge:conditions": [{"type": "permadeath:min_day", "min_day": 40}]}.
 * They are evaluated when datapacks are (re)loaded; {@code DayController} triggers a reload when the day
 * crosses D40/D50/D60, exactly like the Fabric DayChangeHandler.
 */
public final class DayConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, PermadeathMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<MinDay>> MIN_DAY = CONDITIONS.register("min_day", () -> MinDay.CODEC);
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<MaxDay>> MAX_DAY = CONDITIONS.register("max_day", () -> MaxDay.CODEC);
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<BeforeDay>> BEFORE_DAY = CONDITIONS.register("before_day", () -> BeforeDay.CODEC);

    private static volatile int loadedBucket = 0;

    private DayConditions() {
    }

    /** Day used to evaluate conditions; also records which recipe threshold the loaded data corresponds to. */
    static int conditionDay() {
        int day = Permadeath.day();
        loadedBucket = DayRules.recipeBucket(day);
        return day;
    }

    public static int loadedBucket() {
        return loadedBucket;
    }

    public static void forceLoadedBucket(int bucket) {
        loadedBucket = bucket;
    }

    /** Loaded only from {@code min_day} onwards (same field name as Fabric). */
    public record MinDay(int minDay) implements ICondition {
        public static final MapCodec<MinDay> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_day").forGetter(MinDay::minDay)).apply(i, MinDay::new));

        @Override
        public boolean test(IContext context) {
            return conditionDay() >= minDay;
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    /**
     * Loaded up to {@code max_day} (inclusive). The Fabric implementation of this condition was inverted
     * ({@code day >= max_day}); it was not used by any bundled file. The intended semantics are implemented.
     */
    public record MaxDay(int maxDay) implements ICondition {
        public static final MapCodec<MaxDay> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("max_day").forGetter(MaxDay::maxDay)).apply(i, MaxDay::new));

        @Override
        public boolean test(IContext context) {
            return conditionDay() <= maxDay;
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    /** Loaded only while the day is strictly lower than {@code day} (used to remove vanilla recipes). */
    public record BeforeDay(int day) implements ICondition {
        public static final MapCodec<BeforeDay> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("day").forGetter(BeforeDay::day)).apply(i, BeforeDay::new));

        @Override
        public boolean test(IContext context) {
            return conditionDay() < day;
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }
}
