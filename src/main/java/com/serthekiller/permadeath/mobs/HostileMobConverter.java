package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.EnumSet;
import java.util.Set;

/**
 * D20+: every passive/neutral mob becomes hostile (Fabric HostileMobConverter, same goals and damages).
 */
public final class HostileMobConverter {
    private static final double BASE_ATTACK_DAMAGE = 3.0;
    /** Base hit of the "Bacalao de la muerte" before its Sharpness 50 / Knockback 100 weapon (audited value). */
    private static final double COD_BASE_DAMAGE = BASE_ATTACK_DAMAGE;
    private static final String SESSION_KEY = "hostile_convert";

    private HostileMobConverter() {
    }

    /** Mobs that are re-converted even if already processed this session (Fabric isSpecialNeutral). */
    public static boolean isSpecialNeutral(EntityType<?> type) {
        return type == EntityType.WOLF || type == EntityType.BEE || type == EntityType.POLAR_BEAR || type == EntityType.FROG
                || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.AXOLOTL || type == EntityType.PIGLIN
                || type == EntityType.FOX || type == EntityType.SNOW_GOLEM || type == EntityType.LLAMA
                || type == EntityType.LLAMA_SPIT || type == EntityType.OCELOT;
    }

    public static boolean isAlreadyHostile(LivingEntity entity) {
        return entity instanceof Mob mob && MobTracking.isSessionProcessed(mob, SESSION_KEY);
    }

    private static boolean isConvertible(LivingEntity entity) {
        if (entity instanceof Player) {
            return false;
        }
        EntityType<?> type = entity.getType();
        if (type == EntityType.ARMOR_STAND || type == EntityType.ENDERMAN) {
            return false;
        }
        if (entity instanceof Animal || entity instanceof AmbientCreature || entity instanceof WaterAnimal) {
            return true;
        }
        return type == EntityType.SNOW_GOLEM || type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER
                || type == EntityType.ALLAY || type == EntityType.AXOLOTL || type == EntityType.GLOW_SQUID
                || type == EntityType.SQUID || type == EntityType.DOLPHIN || type == EntityType.TADPOLE
                || type == EntityType.FROG || type == EntityType.WOLF || type == EntityType.BEE
                || type == EntityType.POLAR_BEAR || type == EntityType.BAT || type == EntityType.ZOGLIN
                || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.PIGLIN || type == EntityType.OCELOT
                || type == EntityType.LLAMA || type == EntityType.FOX || type == EntityType.LLAMA_SPIT;
    }

    public static void convertToHostile(LivingEntity entity) {
        if (Permadeath.day() < 20) {
            return;
        }
        if (entity instanceof IronGolem golem) {
            villageGolem(golem);
            return;
        }
        if (!isConvertible(entity)) {
            return;
        }
        if (entity.getType() == EntityType.BAT && entity instanceof Bat bat) {
            if (MobTracking.tryClaimSession(bat, SESSION_KEY)) {
                try {
                    convertBat(bat);
                } catch (RuntimeException e) {
                    PermadeathMod.LOGGER.error("Error converting bat to hostile", e);
                    MobTracking.clearSession(bat, SESSION_KEY);
                }
            }
            return;
        }
        if (entity instanceof PathfinderMob mob && MobTracking.tryClaimSession(mob, SESSION_KEY)) {
            try {
                EntityType<?> type = entity.getType();
                if (type == EntityType.WOLF || type == EntityType.POLAR_BEAR || type == EntityType.BEE || type == EntityType.FROG
                        || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.AXOLOTL || type == EntityType.PIGLIN
                        || type == EntityType.SQUID || type == EntityType.GLOW_SQUID) {
                    convertSpecialMob(mob);
                    return;
                }
                boolean hasAttackDamage = setupAttackAttributes(mob);
                clearPacificGoals(mob);
                injectHostileGoals(mob, hasAttackDamage);
            } catch (RuntimeException e) {
                PermadeathMod.LOGGER.error("Error converting {} to hostile", entity.getType(), e);
                MobTracking.clearSession(mob, SESSION_KEY);
            }
        }
    }

    /**
     * D20+: iron golems also hunt players (plugin HostileEntityListener gives every non-hostile mob a player
     * target; Fabric skipped golems). Golems built by players stay friendly: vanilla
     * {@link IronGolem#canAttackType} refuses players for them. Goals are not saved, so this runs once per load.
     */
    private static void villageGolem(IronGolem golem) {
        if (golem.getTags().contains(ExplodingAnimals.GALACTIC_GOLEM_TAG) || !MobTracking.tryClaimSession(golem, SESSION_KEY)) {
            return;
        }
        golem.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(golem, Player.class, true));
    }

    /** D40+: farm animals become Ravagers; D50+: Ultra Ravagers and chickens become silverfish. */
    public static boolean convertToRavager(LivingEntity entity, ServerLevel level) {
        int day = Permadeath.day();
        if (entity instanceof Pig pig && pig.hasCustomName()) {
            return true;
        }
        if (day >= 40 && day < 50) {
            Ravager ravager = new Ravager(EntityType.RAVAGER, level);
            ravager.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
            level.addFreshEntity(ravager);
            entity.discard();
            return true;
        }
        if (day >= 50) {
            if (entity instanceof Chicken) {
                Silverfish silverfish = new Silverfish(EntityType.SILVERFISH, level);
                silverfish.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                level.addFreshEntity(silverfish);
            } else {
                Ravager ravager = new Ravager(EntityType.RAVAGER, level);
                ravager.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                SpecialMobs.makeGoldUltraRavager(ravager);
                level.addFreshEntity(ravager);
            }
            entity.discard();
            return true;
        }
        return true;
    }

    private static void convertSpecialMob(PathfinderMob mob) {
        EntityType<?> type = mob.getType();
        GoalSelector goals = mob.goalSelector;
        GoalSelector targets = mob.targetSelector;
        clearNonPlayerTargets(mob);
        double damage = BASE_ATTACK_DAMAGE;
        if (type == EntityType.WOLF) {
            damage = 4.0;
        } else if (type == EntityType.POLAR_BEAR) {
            damage = 6.0;
        } else if (type == EntityType.BEE) {
            damage = Permadeath.day() >= 50 ? 30.0 : 2.0;
        } else if (type == EntityType.FROG || type == EntityType.SQUID || type == EntityType.GLOW_SQUID) {
            damage = 2.0;
        } else if (type == EntityType.AXOLOTL) {
            damage = 2.5;
        } else if (type == EntityType.PIGLIN) {
            damage = 5.0;
        }
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        // Zombified piglins already attack: they keep their vanilla, pigman class or Carlos value.
        if (attack != null && type != EntityType.ZOMBIFIED_PIGLIN) {
            attack.setBaseValue(damage);
        }
        targets.addGoal(1, new PersistentPlayerTargetGoal(mob));
        Player nearest = mob.level().getNearestPlayer(mob, 64.0);
        if (nearest != null && (nearest.isCreative() || nearest.isSpectator())) {
            nearest = null;
        }
        if (mob instanceof ZombifiedPiglin zp) {
            zp.setRemainingPersistentAngerTime(Integer.MAX_VALUE);
            zp.setAggressive(true);
            if (nearest != null) {
                zp.setTarget(nearest);
                zp.setLastHurtByMob(nearest);
            }
            goals.addGoal(2, new MeleeAttackGoal(mob, 1.0, false));
        } else if (mob instanceof Piglin piglin) {
            clearAllGoals(mob);
            goals.addGoal(1, new PiglinAggressiveAttackGoal(piglin, damage));
            goals.addGoal(2, new FloatGoal(mob));
            goals.addGoal(3, new RandomStrollGoal(mob, 0.8));
            goals.addGoal(4, new LookAtPlayerGoal(mob, Player.class, 8.0F));
            goals.addGoal(5, new RandomLookAroundGoal(mob));
            piglin.setAggressive(true);
            if (nearest != null) {
                piglin.setTarget(nearest);
                piglin.setLastHurtByMob(nearest);
            }
        } else if (mob instanceof Axolotl axolotl) {
            clearAllGoals(mob);
            goals.addGoal(1, new AxolotlAggressiveAttackGoal(axolotl, damage));
            goals.addGoal(3, new RandomStrollGoal(mob, 0.8));
            goals.addGoal(4, new LookAtPlayerGoal(mob, Player.class, 8.0F));
            goals.addGoal(5, new RandomLookAroundGoal(mob));
            if (nearest != null) {
                axolotl.setTarget(nearest);
            }
            goals.addGoal(1, new CustomMeleeAttackGoal(mob, 1.2, true, damage));
        } else if (mob instanceof NeutralMob neutral) {
            neutral.setRemainingPersistentAngerTime(Integer.MAX_VALUE);
            if (nearest != null) {
                mob.setTarget(nearest);
                neutral.setLastHurtByMob(nearest);
            }
            if (mob instanceof Bee bee && bee.getTarget() instanceof Player) {
                bee.setHasStung(false);
            }
            if (mob instanceof Wolf wolf) {
                wolf.setLastHurtByMob(nearest);
            }
        }
        if (type == EntityType.FROG && mob instanceof Frog frog) {
            clearAllGoals(mob);
            goals.addGoal(1, new FrogTongueAttackGoal(frog, damage));
            goals.addGoal(2, new FloatGoal(mob));
        }
        if (type == EntityType.SQUID || type == EntityType.GLOW_SQUID) {
            clearAllGoals(mob);
            goals.addGoal(1, new SquidAggressiveAttackGoal(mob, damage));
        }
    }

    private static void convertBat(Bat bat) {
        clearAllGoals(bat);
        AttributeInstance attack = bat.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(1.0);
        }
        bat.setResting(false);
        bat.goalSelector.addGoal(1, new BatAggressiveFlightGoal(bat, 1.0));
    }

    private static void clearAllGoals(Mob mob) {
        Set<WrappedGoal> goals = Set.copyOf(mob.goalSelector.getAvailableGoals());
        for (WrappedGoal wrapped : goals) {
            mob.goalSelector.removeGoal(wrapped.getGoal());
        }
    }

    private static void clearNonPlayerTargets(PathfinderMob mob) {
        Set<WrappedGoal> goals = Set.copyOf(mob.targetSelector.getAvailableGoals());
        for (WrappedGoal wrapped : goals) {
            Goal goal = wrapped.getGoal();
            if (!(goal instanceof NearestAttackableTargetGoal) && !(goal instanceof PersistentPlayerTargetGoal) && !(goal instanceof HurtByTargetGoal)) {
                mob.targetSelector.removeGoal(goal);
            }
        }
    }

    /**
     * Mobs that already have an attack attribute keep their vanilla damage (plugin; Fabric lowered every one to 3,
     * hoglins and pandas included).
     */
    private static boolean setupAttackAttributes(PathfinderMob mob) {
        return mob.getAttribute(Attributes.ATTACK_DAMAGE) != null;
    }

    private static void clearPacificGoals(PathfinderMob mob) {
        Set<WrappedGoal> goals = Set.copyOf(mob.goalSelector.getAvailableGoals());
        for (WrappedGoal wrapped : goals) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof PanicGoal || goal instanceof AvoidEntityGoal || goal instanceof RandomStrollGoal
                    || goal instanceof LookAtPlayerGoal || goal instanceof BreedGoal || goal instanceof FollowParentGoal
                    || goal instanceof TemptGoal || goal instanceof RunAroundLikeCrazyGoal || goal instanceof TryFindWaterGoal) {
                mob.goalSelector.removeGoal(goal);
            }
        }
    }

    private static void injectHostileGoals(PathfinderMob mob, boolean hasAttackDamage) {
        if (mob instanceof Cod && Permadeath.day() >= 50) {
            // "Bacalao de la muerte": a single melee goal hitting like a Sharpness 50 / Knockback 100 weapon.
            mob.goalSelector.addGoal(1, new CustomMeleeAttackGoal(mob, 1.2, false, COD_BASE_DAMAGE));
            MobUtil.name(mob, "§6Bacalao de la Muerte");
        } else if (hasAttackDamage) {
            mob.goalSelector.addGoal(1, new MeleeAttackGoal(mob, 1.2, false));
        } else {
            // Plugin: mobs without an attack attribute (cows, villagers, horses...) hit for 8 (Fabric 3).
            mob.goalSelector.addGoal(1, new CustomMeleeAttackGoal(mob, 1.2, false, DayRules.HOSTILE_PASSIVE_ATTACK_DAMAGE));
        }
        mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, 10, true, false, null));
        mob.targetSelector.addGoal(2, new HurtByTargetGoal(mob));
    }

    // ------------------------------------------------------------------------------------- goals

    private static final class AxolotlAggressiveAttackGoal extends Goal {
        private final Axolotl axolotl;
        private final double attackDamage;
        private Player targetPlayer;
        private int ticksUntilNextAttack;

        AxolotlAggressiveAttackGoal(Axolotl axolotl, double attackDamage) {
            this.axolotl = axolotl;
            this.attackDamage = attackDamage;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player nearest = axolotl.level().getNearestPlayer(axolotl, 32.0);
            if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                targetPlayer = nearest;
                axolotl.setTarget(nearest);
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetPlayer != null && targetPlayer.isAlive() && axolotl.distanceToSqr(targetPlayer) < 1024.0;
        }

        @Override
        public void start() {
            axolotl.setAggressive(true);
            ticksUntilNextAttack = 0;
        }

        @Override
        public void stop() {
            targetPlayer = null;
            axolotl.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (targetPlayer == null) {
                return;
            }
            axolotl.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);
            axolotl.setTarget(targetPlayer);
            if (axolotl.isInWater()) {
                axolotl.setNoGravity(false);
                axolotl.getNavigation().moveTo(targetPlayer, 1.2);
            } else {
                axolotl.getNavigation().moveTo(targetPlayer, 0.2);
            }
            double distance = axolotl.distanceToSqr(targetPlayer);
            ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
            double reach = axolotl.isInWater() ? 5.0 : 4.0;
            if (distance <= reach && ticksUntilNextAttack <= 0) {
                targetPlayer.hurt(axolotl.damageSources().mobAttack(axolotl), (float) attackDamage);
                ticksUntilNextAttack = 20;
            }
        }
    }

    private static final class BatAggressiveFlightGoal extends Goal {
        private final Bat bat;
        private final double attackDamage;
        private Player targetPlayer;
        private int ticksUntilNextAttack;

        BatAggressiveFlightGoal(Bat bat, double attackDamage) {
            this.bat = bat;
            this.attackDamage = attackDamage;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player nearest = bat.level().getNearestPlayer(bat, 32.0);
            if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                targetPlayer = nearest;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetPlayer != null && targetPlayer.isAlive() && bat.distanceToSqr(targetPlayer) < 1024.0;
        }

        @Override
        public void start() {
            bat.setResting(false);
            ticksUntilNextAttack = 0;
        }

        @Override
        public void stop() {
            targetPlayer = null;
            bat.setDeltaMovement(Vec3.ZERO);
        }

        @Override
        public void tick() {
            if (targetPlayer == null) {
                return;
            }
            bat.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);
            Vec3 direction = new Vec3(targetPlayer.getX() - bat.getX(),
                    targetPlayer.getY() + targetPlayer.getEyeHeight() * 0.5 - bat.getY(),
                    targetPlayer.getZ() - bat.getZ());
            double distance = direction.lengthSqr();
            if (distance > 0.01) {
                Vec3 normalized = direction.normalize();
                bat.setDeltaMovement(bat.getDeltaMovement().add(normalized.scale(0.15)).scale(0.8));
                float yaw = (float) (Mth.atan2(direction.x, direction.z) * (180.0 / Math.PI));
                bat.setYRot(-yaw);
                bat.yBodyRot = bat.getYRot();
            }
            ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
            if (distance <= 4.0 && ticksUntilNextAttack <= 0) {
                targetPlayer.hurt(bat.damageSources().mobAttack(bat), (float) attackDamage);
                ticksUntilNextAttack = 20;
            }
        }
    }

    /**
     * Melee for mobs without an attack attribute. For cods on D50+ ("Bacalao de la muerte") the hit is
     * computed with real enchantment data: a Sharpness L / Knockback C weapon (Java: +0.5*50+0.5 = +25.5
     * damage, knockback strength 100*0.5 = 50, reduced by knockback resistance), instead of the Fabric
     * fixed 59 damage + raw 50 velocity that ignored armor knockback resistance.
     */
    private static final class CustomMeleeAttackGoal extends Goal {
        private final PathfinderMob mob;
        private final double speedModifier;
        private final boolean followingTargetEvenIfNotSeen;
        private final double attackDamage;
        private int ticksUntilNextAttack;

        CustomMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen, double attackDamage) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
            this.attackDamage = attackDamage;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return followingTargetEvenIfNotSeen || mob.getSensing().hasLineOfSight(target);
        }

        @Override
        public void start() {
            mob.setAggressive(true);
            ticksUntilNextAttack = 0;
        }

        @Override
        public void stop() {
            mob.setAggressive(false);
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            double reach = mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + target.getBbWidth();
            mob.getNavigation().moveTo(target, speedModifier);
            ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
            if (distance <= reach && ticksUntilNextAttack <= 0) {
                performAttack(target);
                ticksUntilNextAttack = 20;
            }
        }

        private void performAttack(LivingEntity target) {
            DamageSource source = mob.damageSources().mobAttack(mob);
            float damage = (float) attackDamage;
            if (mob instanceof Cod && Permadeath.day() >= 50 && mob.level() instanceof ServerLevel level) {
                ItemStack weapon = DeathCodWeapon.get(level);
                damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
                float knockback = EnchantmentHelper.modifyKnockback(level, weapon, target, source, 0.0F);
                if (target.hurt(source, damage) && knockback > 0.0F) {
                    target.knockback(knockback * 0.5F, Mth.sin(mob.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(mob.getYRot() * Mth.DEG_TO_RAD));
                    target.hurtMarked = true;
                }
                return;
            }
            target.hurt(source, damage);
        }
    }

    /** Virtual weapon of the death cod: Sharpness 50 + Knockback 100. */
    static final class DeathCodWeapon {
        private static ItemStack cached;
        private static Object cachedFor;

        static ItemStack get(ServerLevel level) {
            Object key = level.registryAccess();
            if (cached == null || cachedFor != key) {
                cached = MobUtil.enchanted(level, new ItemStack(Items.WOODEN_SWORD),
                        MobUtil.ench(Enchantments.SHARPNESS, 50), MobUtil.ench(Enchantments.KNOCKBACK, 100));
                cachedFor = key;
            }
            return cached;
        }
    }

    private static final class FrogTongueAttackGoal extends Goal {
        private final Frog frog;
        private final double attackDamage;
        private Player targetPlayer;
        private int attackCooldown;

        FrogTongueAttackGoal(Frog frog, double attackDamage) {
            this.frog = frog;
            this.attackDamage = attackDamage;
        }

        @Override
        public boolean canUse() {
            Player nearest = frog.level().getNearestPlayer(frog.getX(), frog.getY(), frog.getZ(), 6.0, false);
            if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                targetPlayer = nearest;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetPlayer != null && targetPlayer.isAlive();
        }

        @Override
        public void start() {
            frog.setTarget(targetPlayer);
        }

        @Override
        public void tick() {
            if (targetPlayer == null) {
                return;
            }
            frog.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);
            double distance = frog.distanceTo(targetPlayer);
            if (distance <= 6.0 && attackCooldown <= 0) {
                targetPlayer.hurt(frog.damageSources().mobAttack(frog), (float) attackDamage);
                Vec3 pull = new Vec3(frog.getX() - targetPlayer.getX(), 0.1, frog.getZ() - targetPlayer.getZ()).normalize();
                targetPlayer.setDeltaMovement(pull.x * 0.4, 0.3, pull.z * 0.4);
                attackCooldown = 40;
            } else if (distance > 3.0 && frog.onGround()) {
                Vec3 direction = new Vec3(targetPlayer.getX() - frog.getX(), 0.0, targetPlayer.getZ() - frog.getZ()).normalize();
                float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F;
                frog.setYRot(yaw);
                frog.yBodyRot = yaw;
                frog.yHeadRot = yaw;
                frog.setDeltaMovement(direction.x * 0.5, 0.5, direction.z * 0.5);
            }
            if (attackCooldown > 0) {
                attackCooldown--;
            }
        }
    }

    private static final class PersistentPlayerTargetGoal extends Goal {
        private final PathfinderMob mob;
        private int searchCooldown;

        PersistentPlayerTargetGoal(PathfinderMob mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            if (searchCooldown > 0) {
                searchCooldown--;
                return;
            }
            searchCooldown = 20;
            Player nearest = mob.level().getNearestPlayer(mob, 32.0);
            if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                mob.setTarget(nearest);
                if (mob instanceof NeutralMob neutral) {
                    neutral.setRemainingPersistentAngerTime(Integer.MAX_VALUE);
                    neutral.setLastHurtByMob(nearest);
                    if (mob instanceof ZombifiedPiglin zp) {
                        zp.setAggressive(true);
                    }
                }
                if (mob instanceof Piglin piglin) {
                    piglin.setAggressive(true);
                    piglin.setLastHurtByMob(nearest);
                }
            }
        }
    }

    private static final class PiglinAggressiveAttackGoal extends Goal {
        private final Piglin piglin;
        private final double attackDamage;
        private Player targetPlayer;
        private int ticksUntilNextAttack;

        PiglinAggressiveAttackGoal(Piglin piglin, double attackDamage) {
            this.piglin = piglin;
            this.attackDamage = attackDamage;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player nearest = piglin.level().getNearestPlayer(piglin, 32.0);
            if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                targetPlayer = nearest;
                piglin.setTarget(nearest);
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetPlayer != null && targetPlayer.isAlive() && piglin.distanceToSqr(targetPlayer) < 1024.0;
        }

        @Override
        public void start() {
            piglin.setAggressive(true);
            ticksUntilNextAttack = 0;
        }

        @Override
        public void stop() {
            targetPlayer = null;
            piglin.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (targetPlayer == null) {
                return;
            }
            piglin.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);
            piglin.setTarget(targetPlayer);
            piglin.setAggressive(true);
            double distance = piglin.distanceToSqr(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ());
            piglin.getNavigation().moveTo(targetPlayer, 1.0);
            ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
            if (distance <= 4.0 && ticksUntilNextAttack <= 0) {
                targetPlayer.hurt(piglin.damageSources().mobAttack(piglin), (float) attackDamage);
                ticksUntilNextAttack = 20;
            }
        }
    }

    private static final class SquidAggressiveAttackGoal extends Goal {
        private final PathfinderMob squid;
        private final double attackDamage;
        private Player targetPlayer;
        private int ticksUntilNextAttack;

        SquidAggressiveAttackGoal(PathfinderMob squid, double attackDamage) {
            this.squid = squid;
            this.attackDamage = attackDamage;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player nearest = squid.level().getNearestPlayer(squid, 32.0);
            if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                targetPlayer = nearest;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetPlayer != null && targetPlayer.isAlive() && squid.distanceToSqr(targetPlayer) < 1024.0;
        }

        @Override
        public void start() {
            squid.setTarget(targetPlayer);
            ticksUntilNextAttack = 0;
        }

        @Override
        public void stop() {
            targetPlayer = null;
            squid.setDeltaMovement(squid.getDeltaMovement().multiply(0.2, 0.2, 0.2));
        }

        @Override
        public void tick() {
            if (targetPlayer == null) {
                return;
            }
            squid.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);
            squid.setTarget(targetPlayer);
            Vec3 direction = new Vec3(targetPlayer.getX() - squid.getX(), targetPlayer.getY() + 0.5 - squid.getY(), targetPlayer.getZ() - squid.getZ());
            double distance = direction.lengthSqr();
            if (distance > 1.0) {
                squid.setDeltaMovement(squid.getDeltaMovement().add(direction.normalize().scale(0.1)).scale(0.9));
            }
            ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
            if (distance <= 6.0 && ticksUntilNextAttack <= 0) {
                targetPlayer.hurt(squid.damageSources().mobAttack(squid), (float) attackDamage);
                ticksUntilNextAttack = 20;
            }
        }
    }
}
