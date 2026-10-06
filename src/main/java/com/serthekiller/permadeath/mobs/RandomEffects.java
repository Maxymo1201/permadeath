package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Random infinite effects of spiders, cave spiders, silverfish, endermites, named pigs, phantoms (D50+) and
 * vindicators (D60). The pool and its amplifiers are the historical ones (PermaDeathCore addMobEffects, also
 * used by the Fabric D10-19 handler): Speed III, Regeneration IV, Strength IV, Invisibility, Jump Boost V,
 * Slow Falling, Resistance III and, before D50, Glowing. The Fabric D20+ pools had Strength I/Speed IV/Glowing
 * III, an inconsistency with its own D10 pool and the plugin; the historical amplifiers are used for every day.
 */
public final class RandomEffects {
    private record Entry(Holder<MobEffect> effect, int amplifier) {
    }

    private RandomEffects() {
    }

    private static List<Entry> pool(int day) {
        List<Entry> pool = new ArrayList<>(8);
        pool.add(new Entry(MobEffects.MOVEMENT_SPEED, 2));
        pool.add(new Entry(MobEffects.REGENERATION, 3));
        pool.add(new Entry(MobEffects.DAMAGE_BOOST, 3));
        pool.add(new Entry(MobEffects.INVISIBILITY, 0));
        pool.add(new Entry(MobEffects.JUMP, 4));
        pool.add(new Entry(MobEffects.SLOW_FALLING, 0));
        pool.add(new Entry(MobEffects.DAMAGE_RESISTANCE, 2));
        if (day < 50) {
            pool.add(new Entry(MobEffects.GLOWING, 0));
        }
        return pool;
    }

    /** Number of effects of a spider spawned on {@code day}: 1-3 (D10-19), 1-4 (D20-24), 5 (D25+). */
    public static int spiderEffectCount(int day, RandomSource random) {
        if (day < 20) {
            return random.nextInt(3) + 1;
        }
        if (day < 25) {
            return random.nextInt(4) + 1;
        }
        return 5;
    }

    /** Applies {@code count} distinct random effects (shuffled pool without replacement). */
    public static void apply(LivingEntity entity, int day, int count, RandomSource random) {
        List<Entry> pool = pool(day);
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Entry tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        int applied = 0;
        for (Entry entry : pool) {
            if (applied >= count) {
                break;
            }
            entity.addEffect(new MobEffectInstance(entry.effect(), MobUtil.INFINITE, entry.amplifier(), false, true));
            applied++;
        }
    }
}
