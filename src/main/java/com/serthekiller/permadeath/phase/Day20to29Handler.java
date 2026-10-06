package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.RandomEffects;
import com.serthekiller.permadeath.mobs.SkeletonClasses;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * Fase 3 (D20-29): every mob becomes hostile, beds do not work (phantom counter reset), spiders get random
 * effects and a skeleton rider, D25+ giant slimes/magma cubes/ghasts, many mobs stop dropping loot.
 */
public final class Day20to29Handler implements PhaseHandler {
    @Override
    public String name() {
        return "Fase 3: Días 20-29";
    }

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        Texts.broadcast(overworld.getServer(), "§e=== Fase 3: Días 20-29 ===\n");
        PhaseCommon.forEachLoadedEntity(overworld.getServer(), entity -> {
            if (entity instanceof LivingEntity living) {
                PhaseCommon.convertIfNeeded(living);
            }
        });
    }

    @Override
    public void onEntityJoin(Entity entity, ServerLevel level, boolean loadedFromDisk) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        SpecialMobs.transformGiantMobs(living, level);
        applySpiderEffectsAndRider(living, level);
        if (living instanceof Phantom phantom) {
            PhaseCommon.enlargePhantom(phantom);
        }
        PhaseCommon.convertIfNeeded(living);
    }

    private static void applySpiderEffectsAndRider(LivingEntity entity, ServerLevel level) {
        if (entity instanceof Spider spider && MobTracking.tryClaim(spider, "spider_effects")) {
            int day = Permadeath.day();
            RandomEffects.apply(spider, day, RandomEffects.spiderEffectCount(day, level.random), level.random);
            if (!(spider instanceof CaveSpider) && spider.getPassengers().isEmpty()) {
                SkeletonClasses.addD20Rider(spider, level);
            }
        }
    }

    @Override
    public void onSleepAttempt(CanPlayerSleepEvent event) {
        PhaseCommon.denySleep(event, PhaseCommon.PhantomReset.ALWAYS_SILENT);
    }

    @Override
    public void onDeath(LivingEntity entity, DamageSource source, ServerLevel level) {
        if (Permadeath.day() >= 25) {
            SpecialMobs.handleCaveSpiderHelmetDrop(entity, level);
            SpecialMobs.handleGiantMobArmorDrops(entity, level);
        }
        PhaseCommon.ravagerTotemDrop(entity, level);
    }

    @Override
    public void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if ((entity instanceof Slime || entity instanceof Ghast) && !entity.hasCustomName()) {
            PhaseCommon.clearDrops(event);
            return;
        }
        if (clearsLoot(entity)) {
            // Fabric kept netherite armour items lying around; only the mob's own loot is removed here.
            event.getDrops().removeIf(item -> !isPermadeathNetherite(item));
        }
    }

    static boolean clearsLoot(LivingEntity entity) {
        return entity instanceof IronGolem || entity instanceof Zoglin || entity instanceof ZombifiedPiglin
                || entity instanceof Guardian || entity instanceof EnderMan || entity instanceof Witch
                || entity instanceof WitherSkeleton || entity instanceof Skeleton || entity instanceof Evoker
                || entity instanceof Phantom || entity instanceof Drowned || entity instanceof Blaze
                || entity instanceof Piglin
                || com.serthekiller.permadeath.util.MobUtil.nameContains(entity, "GIGA Slime")
                || com.serthekiller.permadeath.util.MobUtil.nameContains(entity, "GIGA MagmaCube")
                || com.serthekiller.permadeath.util.MobUtil.nameContains(entity, "Demoníaco");
    }

    private static boolean isPermadeathNetherite(ItemEntity item) {
        return ModItems.NETHERITE.contains(item.getItem());
    }
}
