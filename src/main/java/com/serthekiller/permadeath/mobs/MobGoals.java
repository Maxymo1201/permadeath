package com.serthekiller.permadeath.mobs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * AI goals of the D30+ illagers and witches (same numbers as the Fabric goals). Like the Fabric goals they
 * do not request per-tick updates, so the cooldowns count goal ticks (every second game tick).
 */
public final class MobGoals {
    private MobGoals() {
    }

    /** Adds {@code goal} unless the mob already has a goal of the same class (goals are not saved to disk). */
    public static <T extends Goal> void ensureGoal(Mob mob, int priority, Class<T> type, Supplier<T> goal) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (type.isInstance(wrapped.getGoal())) {
                return;
            }
        }
        mob.goalSelector.addGoal(priority, goal.get());
    }

    public static void ensureMachineGun(Pillager pillager) {
        ensureGoal(pillager, 1, MachineGunGoal.class, () -> new MachineGunGoal(pillager));
    }

    public static void ensureImpossibleWitch(Witch witch) {
        ensureGoal(witch, 1, ImpossibleWitchGoal.class, () -> new ImpossibleWitchGoal(witch));
    }

    /** Pillager "ametralladora": one arrow every 3 ticks while the target is visible (10 ticks otherwise). */
    public static final class MachineGunGoal extends Goal {
        private static final int FIRE_RATE = 3;
        private final Pillager mob;
        private int cooldown;

        public MachineGunGoal(Pillager mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() != null && mob.isHolding(stack -> stack.getItem() instanceof CrossbowItem);
        }

        @Override
        public void start() {
            cooldown = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--cooldown <= 0) {
                if (mob.getSensing().hasLineOfSight(target)) {
                    shoot(target);
                    cooldown = FIRE_RATE;
                } else {
                    cooldown = 10;
                }
            }
        }

        private void shoot(LivingEntity target) {
            ItemStack ammo = new ItemStack(Items.ARROW);
            AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, ammo, 1.0F, null);
            double dx = target.getX() - mob.getX();
            double dy = target.getY(0.3333333333333333) - arrow.getY();
            double dz = target.getZ() - mob.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            arrow.shoot(dx, dy + horizontal * 0.2F, dz, 3.0F, 5.0F);
            mob.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            mob.level().addFreshEntity(arrow);
        }
    }

    /** "Bruja imposible": Instant Damage IV, Poison III (5 min) or Slowness V (20 s) every 30 ticks. */
    public static final class ImpossibleWitchGoal extends Goal {
        private static final int POTION_COLOR = 3692632;
        private final Witch witch;
        private int attackCooldown;

        public ImpossibleWitchGoal(Witch witch) {
            this.witch = witch;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = witch.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = witch.getTarget();
            if (target == null) {
                return;
            }
            double distSq = witch.distanceToSqr(target);
            boolean canSee = witch.getSensing().hasLineOfSight(target);
            if (distSq > 100.0) {
                witch.getNavigation().moveTo(target, 1.0);
            } else if (distSq < 16.0) {
                witch.getNavigation().stop();
            }
            witch.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--attackCooldown <= 0 && canSee && distSq <= 225.0) {
                throwPotion(target);
                attackCooldown = 30;
            }
        }

        private void throwPotion(LivingEntity target) {
            ThrownPotion potion = new ThrownPotion(witch.level(), witch);
            potion.setPos(witch.getX(), witch.getEyeY(), witch.getZ());
            List<MobEffectInstance> effects = new ArrayList<>();
            int roll = witch.getRandom().nextInt(3);
            if (roll == 0) {
                effects.add(new MobEffectInstance(MobEffects.HARM, 1, 3));
            } else if (roll == 1) {
                effects.add(new MobEffectInstance(MobEffects.POISON, 6000, 2));
            } else {
                effects.add(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 4));
            }
            ItemStack stack = new ItemStack(Items.SPLASH_POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(POTION_COLOR), effects));
            potion.setItem(stack);
            double dx = target.getX() - witch.getX();
            double dy = target.getY(0.6) - potion.getY();
            double dz = target.getZ() - witch.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            potion.shoot(dx, dy + horizontal * 0.2, dz, 0.75F, 4.0F);
            witch.level().playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE,
                    1.0F, 0.8F + witch.getRandom().nextFloat() * 0.4F);
            witch.level().addFreshEntity(potion);
        }
    }
}
