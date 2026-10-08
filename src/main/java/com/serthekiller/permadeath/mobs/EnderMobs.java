package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

/**
 * Ender variants shared by the D30-D60 phases (Fabric DayXXHandler#teleportRandomly, #dodgeWithTeleport,
 * #spawnEnderGhast).
 *
 * <p>The Fabric {@code applyEndermanBehavior} goal is not ported: it captured {@code creeper.getTarget()} when
 * the creeper joined the world (always {@code null}), so its {@code canStart} was always false and it never
 * ran. Ender creepers keep the teleport-on-damage dodge (Fabric ALLOW_DAMAGE, plugin EntityTeleport).</p>
 */
public final class EnderMobs {
    public static final String ENDER_CREEPER_NAME = "§6Ender Creeper";
    public static final String ENDER_QUANTUM_CREEPER_NAME = "§6Ender Quantum Creeper";
    public static final String QUANTUM_CREEPER_NAME = "§6Quantum Creeper";

    private EnderMobs() {
    }

    /** Enderman-like random teleport (±24 blocks horizontally, up to +31 vertically). */
    public static boolean teleportRandomly(LivingEntity entity) {
        if (entity.level().isClientSide() || !entity.isAlive()) {
            return false;
        }
        double x = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 48.0;
        double y = entity.getY() + entity.getRandom().nextInt(32);
        double z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 48.0;
        boolean success = entity.randomTeleport(x, y, z, true);
        if (success) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        return success;
    }

    /** Up to 64 attempts, like the Fabric mod. */
    public static boolean dodgeWithTeleport(LivingEntity entity) {
        for (int i = 0; i < 64; i++) {
            if (teleportRandomly(entity)) {
                return true;
            }
        }
        return false;
    }

    /** D40-49: projectiles and explosions are dodged. */
    public static boolean isDodgeableD40(DamageSource source) {
        return source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    /** D50+: also fire and drowning. */
    public static boolean isDodgeableD50(DamageSource source) {
        return isDodgeableD40(source) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.DROWN);
    }

    /**
     * Ender (Quantum) Creepers dodge every damage except melee and the void (plugin EntityTeleport: any cause but
     * ENTITY_ATTACK and VOID, in every dimension). Fabric only dodged projectiles/explosions (D40, only in the End)
     * and also fire/drowning from D50, so potions, falls and lava still killed them.
     */
    public static boolean isDodgeableEnderCreeper(DamageSource source) {
        return !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL));
    }

    public static boolean isEnderGhast(LivingEntity entity) {
        return entity instanceof Ghast && entity.hasCustomName() && entity.getCustomName() != null
                && entity.getCustomName().getString().toUpperCase().contains("ENDER GHAST");
    }

    public static boolean isDefinitiveGhast(LivingEntity entity) {
        return entity instanceof Ghast && MobUtil.nameContains(entity, "Ghast Definitivo");
    }

    public static boolean isEnderCreeper(LivingEntity entity) {
        return entity instanceof Creeper && (MobUtil.nameContains(entity, "Ender Creeper") || MobUtil.nameContains(entity, "Ender Quantum Creeper"));
    }

    public static boolean isDragonAlive(ServerLevel level) {
        return !level.getEntities(EntityType.ENDER_DRAGON, EnderDragon::isAlive).isEmpty();
    }

    /**
     * Ender Ghast: 75 HP, 9% chance per goal evaluation to teleport. In The Beginning (D50+) it becomes the
     * "Ghast Definitivo" with 240 HP.
     */
    public static Ghast spawnEnderGhast(ServerLevel level, double x, double y, double z) {
        Ghast ghast = new Ghast(EntityType.GHAST, level);
        ghast.setPos(x, y, z);
        if (level.dimension() == BeginningDimension.LEVEL_KEY) {
            // Plugin: "Ender Ghast Definitivo" with 150 HP (Fabric 240).
            MobUtil.name(ghast, "§6Ghast Definitivo");
            MobUtil.setMaxHealth(ghast, 150.0);
        } else {
            MobUtil.name(ghast, "§6Ender Ghast");
            MobUtil.setMaxHealth(ghast, 75.0);
        }
        addTeleportGoal(ghast);
        level.addFreshEntity(ghast);
        return ghast;
    }

    /** Re-adds the teleport goal after a reload (goals are not persisted). */
    public static void addTeleportGoal(Ghast ghast) {
        MobGoals.ensureGoal(ghast, 1, RandomTeleportGoal.class, () -> new RandomTeleportGoal(ghast, 0.09F));
    }

    /** Fabric anonymous Goal: canStart = random &lt; chance, start = teleport. */
    public static final class RandomTeleportGoal extends Goal {
        private final LivingEntity mob;
        private final float chance;

        public RandomTeleportGoal(LivingEntity mob, float chance) {
            this.mob = mob;
            this.chance = chance;
        }

        @Override
        public boolean canUse() {
            return mob.getRandom().nextFloat() < chance;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            teleportRandomly(mob);
        }
    }

    /** Short random hop of the "ghast feliz" carrying a pigman (Fabric teleportGhastPigman: 80% of the hits). */
    public static void teleportGhastPigman(LivingEntity ghast) {
        if (ghast.getRandom().nextFloat() >= 0.8F) {
            return;
        }
        Level level = ghast.level();
        for (int i = 0; i < 32; i++) {
            double x = ghast.getX() + (ghast.getRandom().nextDouble() - 0.5) * 16.0;
            double y = ghast.getY() + (ghast.getRandom().nextInt(16) - 8);
            double z = ghast.getZ() + (ghast.getRandom().nextDouble() - 0.5) * 16.0;
            if (level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
                ghast.teleportTo(x, y, z);
                if (ghast.isVehicle()) {
                    level.playSound(null, x, y, z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
                    ghast.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                    break;
                }
            }
        }
    }
}
