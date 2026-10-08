package com.serthekiller.permadeath.core.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {

    @ParameterizedTest(name = "D{0}: {1}% fail, {2} totems")
    @CsvSource({
            "0,0,1", "29,0,1",
            "30,1,1", "39,1,1",
            "40,3,2", "49,3,2",
            "50,5,2", "59,5,2",
            "60,7,3"
    })
    void totemTable(int day, int failure, int totems) {
        TotemRules.TotemRule rule = TotemRules.forDay(day);
        assertEquals(failure, rule.failurePercent());
        assertEquals(100 - failure, rule.successPercent());
        assertEquals(totems, rule.requiredTotems());
        assertEquals(failure, TotemRules.getFailureChance(day));
        assertEquals(totems, TotemRules.getRequiredTotems(day));
    }

    @Test
    void totemRollBoundaries() {
        assertFalse(TotemRules.forDay(29).fails(0), "D29 never fails");
        assertTrue(TotemRules.forDay(30).fails(0));
        assertFalse(TotemRules.forDay(30).fails(1));
        assertTrue(TotemRules.forDay(60).fails(6));
        assertFalse(TotemRules.forDay(60).fails(7));
    }

    @Test
    void totemRngSmokeTest() {
        Random random = new Random(1234L);
        int fails = 0;
        int samples = 200_000;
        TotemRules.TotemRule rule = TotemRules.forDay(60);
        for (int i = 0; i < samples; i++) {
            if (rule.fails(random.nextInt(100))) {
                fails++;
            }
        }
        double rate = fails / (double) samples;
        assertTrue(rate > 0.065 && rate < 0.075, "~7% observed " + rate);
    }

    @ParameterizedTest(name = "drowning D{0} x{1}")
    @CsvSource({"0,1", "49,1", "50,5", "59,5", "60,10"})
    void drowning(int day, int multiplier) {
        assertEquals(multiplier, DayRules.drowningMultiplier(day));
        assertEquals(multiplier, DayRules.scaledAirConsumption(1, day));
        assertEquals(0, DayRules.scaledAirConsumption(0, day), "Respiration/Water Breathing: no forced consumption");
    }

    @ParameterizedTest(name = "mobs D{0}: cap {1}")
    @CsvSource({"0,70", "9,70", "10,140", "11,140", "60,140"})
    void mobCap(int day, int cap) {
        assertEquals(cap, DayRules.monsterCap(day));
    }

    @ParameterizedTest(name = "blindness D{0}: 1/{1}")
    @CsvSource({"39,0", "40,10000", "49,10000", "50,5000", "59,5000", "60,5000"})
    void rainBlindness(int day, int oneIn) {
        assertEquals(oneIn, DayRules.rainBlindnessOneIn(day));
    }

    @ParameterizedTest(name = "death train D{0}: {1} min")
    @CsvSource({
            "0,60", "1,60", "10,600", "24,1440", "25,60", "26,120", "40,960", "49,1500",
            "50,30", "51,60", "55,180", "59,300", "60,330"
    })
    void deathTrainDuration(int day, long minutes) {
        assertEquals(minutes * 60_000L, DayRules.deathTrainDurationMillis(day));
    }

    @Test
    void deathTrainDurationMatchesPermaDeathCore() {
        // PermaDeathCore PlayerEvents#loadTicks (historical reference) for D1..D60
        for (int day = 1; day <= 60; day++) {
            long seconds;
            if (day <= 24) {
                seconds = day * 3600L;
            } else if (day < 50) {
                seconds = (day - 24) * 3600L;
            } else if (day == 50) {
                seconds = 1800L;
            } else {
                seconds = (day - 49) * 3600L / 2;
            }
            assertEquals(seconds * 1000L, DayRules.deathTrainDurationMillis(day), "day " + day);
        }
    }

    @Test
    void deathTrainBuffs() {
        assertEquals(-1, DayRules.deathTrainBuffAmplifier(24));
        assertEquals(0, DayRules.deathTrainBuffAmplifier(25));
        assertEquals(0, DayRules.deathTrainBuffAmplifier(49));
        assertEquals(1, DayRules.deathTrainBuffAmplifier(50));
        assertEquals(1, DayRules.deathTrainBuffAmplifier(60));
        assertFalse(DayRules.deathTrainDisablesRegeneration(49));
        assertTrue(DayRules.deathTrainDisablesRegeneration(50));
    }

    @Test
    void miscThresholds() {
        assertFalse(DayRules.pvpEnabled(39));
        assertTrue(DayRules.pvpEnabled(40));
        assertFalse(DayRules.endOpen(29));
        assertTrue(DayRules.endOpen(30));
        assertEquals(0, DayRules.maxHyperApples(39));
        assertEquals(1, DayRules.maxHyperApples(40));
        assertEquals(1, DayRules.maxHyperApples(59));
        assertEquals(2, DayRules.maxHyperApples(60));
        assertEquals(0.0, DayRules.maxHealthPenalty(39));
        assertEquals(8.0, DayRules.maxHealthPenalty(40));
        assertEquals(16.0, DayRules.maxHealthPenalty(60));
        assertEquals(0, DayRules.recipeBucket(39));
        assertEquals(40, DayRules.recipeBucket(49));
        assertEquals(50, DayRules.recipeBucket(59));
        assertEquals(60, DayRules.recipeBucket(60));
    }

    @ParameterizedTest(name = "drowning hit D{0} = {1}")
    @CsvSource({"0,-1", "49,-1", "50,5", "59,5", "60,10"})
    void drowningDamage(int day, float damage) {
        assertEquals(damage, DayRules.drowningDamage(day));
    }

    @Test
    void deathTrainFireResistanceOnlyD50to59() {
        assertFalse(DayRules.deathTrainFireResistance(49));
        assertTrue(DayRules.deathTrainFireResistance(50));
        assertTrue(DayRules.deathTrainFireResistance(59));
        assertFalse(DayRules.deathTrainFireResistance(60));
    }

    @Test
    void pluginPlayerNumbers() {
        assertEquals(60, DayRules.randomLevitationTicks(0));
        assertEquals(380, DayRules.randomLevitationTicks(16));
        assertEquals(10000, DayRules.RANDOM_LEVITATION_ONE_IN);
        assertEquals(20, DayRules.RANDOM_LEVITATION_PERIOD_TICKS);
        assertEquals(120, DayRules.D60_PEARL_COOLDOWN_TICKS);
        assertEquals(600, DayRules.D60_SOUL_SAND_SLOWNESS_TICKS);
        assertEquals(10, DayRules.D50_PHANTOM_RESET_PERCENT);
    }

    @ParameterizedTest(name = "mobs D{0}")
    @CsvSource({
            // day, phantom size, ghast roll, giant 1/N, emperor 1/N, pigman class /99
            "40,9,1,500,50,5",
            "49,9,1,500,50,5",
            "50,18,1,500,50,20",
            "59,18,1,500,50,20",
            "60,18,25,125,13,20"
    })
    void pluginMobNumbers(int day, int phantomSize, int ghastRoll, int giant, int emperor, int pigman) {
        assertEquals(phantomSize, DayRules.phantomSize(day));
        assertEquals(ghastRoll, DayRules.phantomGhastRoll(day));
        assertEquals(giant, DayRules.giantOneIn(day));
        assertEquals(emperor, DayRules.emperorOneIn(day));
        assertEquals(pigman, DayRules.pigmanClassChance(day));
    }

    @Test
    void netheriteArmorDropsOnlyD25to29() {
        assertFalse(DayRules.netheriteArmorDropDay(24));
        assertTrue(DayRules.netheriteArmorDropDay(25));
        assertTrue(DayRules.netheriteArmorDropDay(29));
        assertFalse(DayRules.netheriteArmorDropDay(30));
        assertEquals(10, DayRules.NETHERITE_ARMOR_DROP_PERCENT);
        assertEquals(600, DayRules.SUPERNOVA_FUSE_TICKS);
        assertEquals(200.0F, DayRules.SUPERNOVA_POWER);
        assertEquals(8.0, DayRules.HOSTILE_PASSIVE_ATTACK_DAMAGE);
    }
}
