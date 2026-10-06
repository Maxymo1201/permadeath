package com.serthekiller.permadeath.phase;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * One handler per phase (D0-9, D10-19, ..., D50-59, D60). Only the handler of the current phase receives
 * events, exactly like the Fabric mod (its listeners were registered once and gated by an "active" flag).
 * Hooks map the Fabric callbacks to NeoForge events:
 * <ul>
 *     <li>ServerEntityEvents.ENTITY_LOAD → {@link EntityJoinLevelEvent}</li>
 *     <li>ServerLivingEntityEvents.ALLOW_DAMAGE → {@link LivingIncomingDamageEvent}</li>
 *     <li>ServerLivingEntityEvents.AFTER_DEATH → LivingDeathEvent ({@link #onDeath}) + {@link LivingDropsEvent}</li>
 *     <li>EntitySleepEvents.ALLOW_SLEEPING → {@link CanPlayerSleepEvent}</li>
 *     <li>PlayerBlockBreakEvents.AFTER → BlockEvent.BreakEvent</li>
 *     <li>UseBlockCallback → {@link PlayerInteractEvent.RightClickBlock}</li>
 *     <li>ServerTickEvents.END_WORLD_TICK → LevelTickEvent.Post</li>
 * </ul>
 */
public interface PhaseHandler {
    String name();

    default void onPhaseStart(ServerLevel overworld) {
    }

    default void onPhaseEnd(ServerLevel overworld) {
    }

    default void onLevelTick(ServerLevel level) {
    }

    default void onEntityJoin(EntityJoinLevelEvent event, ServerLevel level) {
    }

    default void onIncomingDamage(LivingIncomingDamageEvent event) {
    }

    default void onDeath(LivingEntity entity, DamageSource source, ServerLevel level) {
    }

    default void onDrops(LivingDropsEvent event) {
    }

    default void onSleepAttempt(CanPlayerSleepEvent event) {
    }

    default void onBlockBroken(ServerPlayer player, BlockPos pos, BlockState state) {
    }

    default void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    }
}
