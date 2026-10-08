package com.serthekiller.permadeath.end;

import com.serthekiller.permadeath.mechanics.GameplayRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Dragon fireball impacts (Fabric DragonFireballMixin). The Fabric mixin ran where vanilla spawns the dragon
 * breath cloud, i.e. after the "not the owner" check and after level event 2006; here the impact event is
 * cancelled and the same steps are replayed.
 * <pre>
 * Normal:  &lt;22 white cloud, &lt;40 harming hearts, &lt;55 black (blindness/wither/slowness III), &lt;68 vanilla breath,
 *          &lt;79 gray, &lt;88 green, &lt;94 purple (poison II), &lt;97 2x2 lava, &lt;99 lightning cloud, else 2x2 bedrock
 * Enraged: &lt;20 white, &lt;40 black, &lt;60 night vision II to the level's players, &lt;80 gray, else lightning cloud
 * </pre>
 */
public final class DragonFireballs {
    private DragonFireballs() {
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof DragonFireball ball) || !(ball.level() instanceof ServerLevel level)) {
            return;
        }
        HitResult result = event.getRayTraceResult();
        if (result.getType() == HitResult.Type.ENTITY && ((EntityHitResult) result).getEntity() == ball.getOwner()) {
            return; // vanilla ignores hits on its owner as well
        }
        boolean enraged = ball.getOwner() instanceof EnderDragon dragon && EnderDragonDemon.isEnraged(dragon);
        double roll = level.getRandom().nextDouble() * 100.0;
        Runnable action;
        if (enraged) {
            if (roll < 20.0) {
                action = () -> cloud(ball, level, "WHITE");
            } else if (roll < 40.0) {
                action = () -> cloud(ball, level, "BLACK");
            } else if (roll < 60.0) {
                action = () -> cloud(ball, level, "NIGHT_VISION");
            } else if (roll < 80.0) {
                action = () -> cloud(ball, level, "GRAY");
            } else {
                action = () -> lightningCloud(ball, level);
            }
        } else if (roll < 22.0) {
            action = () -> cloud(ball, level, "WHITE");
        } else if (roll < 40.0) {
            action = () -> cloud(ball, level, "HEARTS");
        } else if (roll < 55.0) {
            action = () -> cloud(ball, level, "BLACK");
        } else if (roll < 68.0) {
            return; // vanilla dragon breath
        } else if (roll < 79.0) {
            action = () -> cloud(ball, level, "GRAY");
        } else if (roll < 88.0) {
            action = () -> cloud(ball, level, "GREEN");
        } else if (roll < 94.0) {
            action = () -> cloud(ball, level, "PURPLE");
        } else if (roll < 97.0) {
            action = () -> blockArea(ball, level, Blocks.LAVA.defaultBlockState());
        } else if (roll < 99.0) {
            action = () -> lightningCloud(ball, level);
        } else {
            action = () -> blockArea(ball, level, Blocks.BEDROCK.defaultBlockState());
        }
        level.levelEvent(2006, ball.blockPosition(), ball.isSilent() ? -1 : 1);
        action.run();
        ball.discard();
        event.setCanceled(true);
    }

    private static LivingEntity owner(DragonFireball ball) {
        Entity owner = ball.getOwner();
        return owner instanceof LivingEntity living ? living : null;
    }

    private static void cloud(DragonFireball ball, ServerLevel level, String type) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, ball.getX(), ball.getY(), ball.getZ());
        cloud.setOwner(owner(ball));
        cloud.setDuration(600);
        cloud.setWaitTime(0);
        switch (type) {
            case "WHITE" -> {
                cloud.setParticle(ParticleTypes.CLOUD);
                cloud.setRadius(6.0F);
            }
            case "BLACK" -> {
                cloud.setParticle(ParticleTypes.SMOKE);
                cloud.setRadius(4.0F);
                cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 600, 2));
                cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 2));
                cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 2));
            }
            case "PURPLE" -> {
                cloud.setParticle(ParticleTypes.PORTAL);
                cloud.setRadius(4.0F);
                cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
            }
            case "NIGHT_VISION" -> {
                for (ServerPlayer player : level.players()) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 1));
                }
                return;
            }
            case "GREEN" -> {
                cloud.setParticle(ParticleTypes.HAPPY_VILLAGER);
                cloud.setRadius(7.0F);
            }
            case "GRAY" -> {
                cloud.setParticle(ParticleTypes.MYCELIUM);
                cloud.setRadius(2.5F);
            }
            case "HEARTS" -> {
                cloud.setRadius(1.5F);
                cloud.setParticle(ParticleTypes.SQUID_INK);
                cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 2));
            }
            default -> throw new IllegalArgumentException(type);
        }
        level.addFreshEntity(cloud);
    }

    private static void lightningCloud(DragonFireball ball, ServerLevel level) {
        BlockPos impact = BlockPos.containing(ball.getX(), ball.getY(), ball.getZ());
        BlockPos ground = impact;
        for (int i = 0; i < 50; i++) {
            BlockPos check = impact.below(i);
            if (!level.getBlockState(check).isAir()) {
                ground = check.above();
                break;
            }
        }
        AreaEffectCloud cloud = new AreaEffectCloud(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        cloud.setOwner(owner(ball));
        cloud.setDuration(600);
        cloud.setWaitTime(0);
        cloud.setRadius(0.5F);
        cloud.setParticle(ParticleTypes.ELECTRIC_SPARK);
        cloud.addTag(GameplayRules.LIGHTNING_CLOUD);
        level.addFreshEntity(cloud);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
            bolt.setVisualOnly(false);
            level.addFreshEntity(bolt);
        }
    }

    private static void blockArea(DragonFireball ball, ServerLevel level, BlockState state) {
        BlockPos center = BlockPos.containing(ball.getX(), ball.getY(), ball.getZ());
        BlockPos ground = center;
        for (int i = 0; i < 20; i++) {
            BlockPos check = center.below(i);
            if (!level.getBlockState(check).isAir()) {
                ground = check;
                break;
            }
        }
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                BlockPos pos = ground.offset(x, 0, z);
                BlockState current = level.getBlockState(pos);
                // The exit portal (bedrock, portal blocks) and block entities such as shulker boxes are never replaced.
                if (current.hasBlockEntity() || current.getDestroySpeed(level, pos) < 0.0F) {
                    continue;
                }
                level.setBlock(pos, state, 3);
            }
        }
    }
}
