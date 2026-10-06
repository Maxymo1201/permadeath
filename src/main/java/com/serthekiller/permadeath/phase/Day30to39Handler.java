package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.MobGoals;
import com.serthekiller.permadeath.mobs.MobReplacements;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.RandomEffects;
import com.serthekiller.permadeath.mobs.SkeletonClasses;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/**
 * Fase 4 (D30-39): charged creepers, squids → guardians, bats → blazes, skeleton classes, Ender creepers and
 * Ender ghasts in the End, illager/golem/enderman buffs, The End opens (see EndAccess), totems may fail
 * (TotemSystem, global).
 */
public final class Day30to39Handler implements PhaseHandler {
    private final Set<UUID> processedEndermen = new HashSet<>();

    @Override
    public String name() {
        return "Fase 4: Días 30-39";
    }

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        Texts.broadcast(overworld.getServer(), "§e=== Fase 4: Días 30-39 ===\n");
        PhaseCommon.forEachLoadedEntity(overworld.getServer(), entity -> {
            if (entity instanceof LivingEntity living && entity.level() instanceof ServerLevel level
                    && !MobReplacements.tryReplace(living, level, Permadeath.day())) {
                PhaseCommon.convertIfNeeded(living);
            }
        });
    }

    @Override
    public void onPhaseEnd(ServerLevel overworld) {
        processedEndermen.clear();
    }

    @Override
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (entity instanceof Creeper creeper && entity.level().dimension() == Level.END && source.getDirectEntity() instanceof Projectile) {
            EnderMobs.dodgeWithTeleport(creeper);
            event.setCanceled(true);
            return;
        }
        if (EnderMobs.isEnderGhast(entity) && source.getDirectEntity() instanceof AbstractArrow
                && entity.getRandom().nextFloat() < 0.1F && EnderMobs.teleportRandomly(entity)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onEntityJoin(Entity entity, ServerLevel level, boolean loadedFromDisk) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (living instanceof EnderMan enderman && level.dimension() == Level.END && handleEndEnderman(enderman, level)) {
            return;
        }
        if (living instanceof Creeper creeper) {
            PhaseCommon.makePowered(creeper);
            if (level.dimension() == Level.END) {
                creeper.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
            }
        }
        if (MobReplacements.tryReplace(living, level, Permadeath.day())) {
            return;
        }
        if (living instanceof Skeleton skeleton && !MobTracking.isProcessed(skeleton, SkeletonClasses.CLASSED_KEY)) {
            MobTracking.markProcessed(skeleton, SkeletonClasses.CLASSED_KEY);
            if (skeleton.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() && !(skeleton.getVehicle() instanceof Spider)) {
                SkeletonClasses.applyRandomClass(skeleton, level, null, SkeletonClasses.Tier.D30);
                if (skeleton.isRemoved()) {
                    return;
                }
            }
        }
        applyEffects(living, level);
        insertEffect(living, level);
        SpecialMobs.transformGiantMobs(living, level);
        if (living instanceof Phantom phantom) {
            PhaseCommon.enlargePhantom(phantom);
        }
        PhaseCommon.convertIfNeeded(living);
    }

    /** End endermen: 11% Ender Creeper, 4% Ender Ghast (only while the dragon is dead). */
    private boolean handleEndEnderman(EnderMan enderman, ServerLevel level) {
        if (enderman.isRemoved() || processedEndermen.contains(enderman.getUUID())) {
            return enderman.isRemoved();
        }
        int chance = level.random.nextInt(100);
        if (chance <= 10) {
            Creeper creeper = new Creeper(EntityType.CREEPER, level);
            creeper.setPos(enderman.getX(), enderman.getY(), enderman.getZ());
            level.addFreshEntity(creeper);
            creeper.setCustomName(net.minecraft.network.chat.Component.literal(EnderMobs.ENDER_CREEPER_NAME));
            enderman.discard();
            return true;
        }
        if (chance < 15) {
            if (!EnderMobs.isDragonAlive(level)) {
                EnderMobs.spawnEnderGhast(level, enderman.getX(), enderman.getY() + 3.0, enderman.getZ());
                enderman.discard();
                return true;
            }
        } else {
            processedEndermen.add(enderman.getUUID());
        }
        return false;
    }

    private static void applyEffects(LivingEntity entity, ServerLevel level) {
        int day = Permadeath.day();
        if (entity instanceof Spider spider) {
            if (MobTracking.tryClaim(spider, "spider_effects")) {
                RandomEffects.apply(spider, day, 5, level.random);
                if (!(spider instanceof CaveSpider) && spider.getPassengers().isEmpty()) {
                    SkeletonClasses.addRider(spider, level, SkeletonClasses.Tier.D30);
                }
            }
        } else if (entity instanceof Silverfish silverfish) {
            if (MobTracking.tryClaim(silverfish, "spider_effects")) {
                RandomEffects.apply(silverfish, day, 5, level.random);
                silverfish.setCustomName(net.minecraft.network.chat.Component.literal("§6Silverfish de la Muerte"));
            }
        } else if (entity instanceof Endermite endermite && MobTracking.tryClaim(endermite, "spider_effects")) {
            RandomEffects.apply(endermite, day, 5, level.random);
        }
    }

    private static void insertEffect(LivingEntity entity, ServerLevel level) {
        if (entity instanceof Pillager pillager) {
            // Goals are not saved: the machine gun is re-added on every load (Fabric lost it after a restart).
            if (MobTracking.tryClaim(pillager, "insert_effect_d30_39")) {
                pillager.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
                pillager.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.CROSSBOW), ench(Enchantments.QUICK_CHARGE, 1)));
            }
            MobGoals.ensureMachineGun(pillager);
            return;
        }
        if (!(entity instanceof net.minecraft.world.entity.Mob mob) || !MobTracking.tryClaim(mob, "insert_effect_d30_39")) {
            return;
        }
        if (entity instanceof IronGolem golem) {
            golem.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 3, false, true));
        } else if (entity instanceof EnderMan enderman) {
            enderman.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 1, false, true));
        } else if (entity instanceof ZombifiedPiglin zp) {
            MobUtil.equipArmor(zp, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
            MobUtil.name(zp, "§6Pigman Full Diamante");
        } else if (entity instanceof Piglin piglin) {
            MobUtil.equipArmor(piglin, new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.GOLDEN_CHESTPLATE),
                    new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.GOLDEN_BOOTS));
            MobUtil.name(piglin, "§6Piglin Full Oro");
        }
    }

    @Override
    public void onSleepAttempt(CanPlayerSleepEvent event) {
        PhaseCommon.denySleep(event, PhaseCommon.PhantomReset.ALWAYS_WITH_MESSAGE);
    }

    @Override
    public void onDeath(LivingEntity entity, DamageSource source, ServerLevel level) {
        SpecialMobs.handleGiantMobArmorDrops(entity, level);
        PhaseCommon.ravagerTotemDrop(entity, level);
    }

    @Override
    public void onDrops(LivingDropsEvent event) {
        if (clearsLoot(event.getEntity())) {
            PhaseCommon.clearDrops(event);
        }
    }

    static boolean clearsLoot(LivingEntity entity) {
        return Day20to29Handler.clearsLoot(entity) || entity instanceof Slime || entity instanceof MagmaCube || entity instanceof Ghast;
    }

    @Override
    public void onLevelTick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            PhaseCommon.bedrockLevitation(player, false);
        }
    }
}
