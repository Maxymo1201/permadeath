package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/**
 * Entity replacements of the D30+ phases (Fabric handleMobReplacement). The replaced entity is always
 * discarded; the replacement is only spawned while fewer than 5 of its kind are nearby (radius 50/70).
 * Members of a custom stack ("ProcessedStack": Jess, Carlos, ...) are never replaced.
 */
public final class MobReplacements {
    private MobReplacements() {
    }

    /** @return true when {@code entity} has been replaced (and discarded). */
    public static boolean tryReplace(LivingEntity entity, ServerLevel level, int day) {
        if (entity.isRemoved() || entity.getTags().contains(SpecialMobs.PROCESSED_STACK)) {
            return entity.isRemoved();
        }
        if (day < 30) {
            return false;
        }
        if (entity instanceof Squid) {
            if (day >= 60) {
                if (count(level, entity, ElderGuardian.class, 50.0) < 5) {
                    ElderGuardian guardian = new ElderGuardian(EntityType.ELDER_GUARDIAN, level);
                    guardian.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 2));
                    guardian.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 1));
                    place(level, guardian, entity);
                }
            } else if (count(level, entity, Guardian.class, 50.0) < 5) {
                Guardian guardian = new Guardian(EntityType.GUARDIAN, level);
                if (day >= 40) {
                    guardian.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 2));
                    guardian.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 1));
                } else {
                    guardian.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1));
                }
                place(level, guardian, entity);
            }
            entity.discard();
            return true;
        }
        if (day >= 60 && entity instanceof Guardian && !(entity instanceof ElderGuardian)) {
            if (count(level, entity, ElderGuardian.class, 50.0) < 5) {
                ElderGuardian guardian = new ElderGuardian(EntityType.ELDER_GUARDIAN, level);
                guardian.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 2));
                guardian.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 1));
                place(level, guardian, entity);
            }
            entity.discard();
            return true;
        }
        if (entity instanceof Bat) {
            if (count(level, entity, Blaze.class, 70.0) < 5) {
                Blaze blaze = new Blaze(EntityType.BLAZE, level);
                if (day >= 50) {
                    // Fabric called setHealth(200) on a 20 HP blaze (clamped to 20); the plugin gives D50 blazes 200 HP.
                    MobUtil.setMaxHealth(blaze, 200.0);
                }
                blaze.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 1));
                place(level, blaze, entity);
            }
            entity.discard();
            return true;
        }
        if (day < 40) {
            return false;
        }
        if (entity instanceof Zombie && !(entity instanceof ZombifiedPiglin) && !(entity instanceof Drowned)) {
            if (count(level, entity, Vindicator.class, 70.0) < 5) {
                Vindicator vindicator = new Vindicator(EntityType.VINDICATOR, level);
                if (day >= 50) {
                    vindicator.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_AXE), ench(Enchantments.SHARPNESS, 5)));
                } else {
                    vindicator.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                }
                vindicator.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 0));
                MobUtil.multiplyMaxHealth(vindicator, 2.0);
                boolean evoker = day >= 60 ? level.random.nextInt(100) <= 50 : day >= 50 && level.random.nextInt(100) == 1;
                if (evoker) {
                    place(level, new Evoker(EntityType.EVOKER, level), entity);
                } else {
                    place(level, vindicator, entity);
                }
            }
            entity.discard();
            return true;
        }
        if (entity instanceof Wolf) {
            if (count(level, entity, Cat.class, 50.0) < 5) {
                place(level, new Cat(EntityType.CAT, level), entity);
            }
            entity.discard();
            return true;
        }
        if (day >= 50 && entity instanceof Salmon) {
            if (count(level, entity, Pufferfish.class, 50.0) < 5) {
                place(level, new Pufferfish(EntityType.PUFFERFISH, level), entity);
            }
            entity.discard();
            return true;
        }
        if (day >= 60 && entity instanceof Villager) {
            place(level, new Vindicator(EntityType.VINDICATOR, level), entity);
            entity.discard();
            return true;
        }
        return false;
    }

    private static int count(ServerLevel level, LivingEntity around, Class<? extends LivingEntity> type, double radius) {
        AABB box = around.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(type, box, e -> !e.isSpectator()).size();
    }

    private static void place(ServerLevel level, Mob replacement, LivingEntity original) {
        replacement.setPos(original.getX(), original.getY(), original.getZ());
        replacement.setYRot(original.getYRot());
        level.addFreshEntity(replacement);
    }
}
