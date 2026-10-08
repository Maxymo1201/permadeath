package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.event.EventHooks;

/**
 * "Cambio de Mikecrack" (D60): Ender Quantum Creepers appear around the players at any light level. As in the
 * plugin ("Mike-Creeper-Spawn", enabled by default) it is active on D60 unless an operator disables it with
 * /permadeath mikecrack disable; every second each player has a 1/30 chance of getting one creeper 15-29 blocks
 * away at their height, if fewer than 10 creepers are within 30 blocks. Fabric kept it off until an operator
 * enabled it and then spawned 5 creepers per player every 5 s and let natural creepers ignore light.
 */
public final class Mikecrack {
    private static final int PERIOD_TICKS = 20;
    private static final int ONE_IN = 30;
    private static final int MAX_NEARBY_CREEPERS = 10;
    private static int tickCounter;

    private Mikecrack() {
    }

    public static boolean isEnabled() {
        return Permadeath.isRunning() && Permadeath.day() >= 60 && !Permadeath.state().mikecrackDisabled;
    }

    public static void setEnabled(boolean enabled) {
        Permadeath.state().mikecrackEnabled = enabled;
        Permadeath.state().mikecrackDisabled = !enabled;
        Permadeath.state().markChanged();
        PermadeathMod.LOGGER.info("[Permadeath] Mikecrack mode {}", enabled ? "enabled" : "disabled");
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter < PERIOD_TICKS) {
            return;
        }
        tickCounter = 0;
        if (!isEnabled()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && level.random.nextInt(ONE_IN) == 0) {
                    spawnNear(level, player);
                }
            }
        }
    }

    private static void spawnNear(ServerLevel level, ServerPlayer player) {
        if (level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(30.0)).size() >= MAX_NEARBY_CREEPERS) {
            return;
        }
        RandomSource random = level.random;
        int dx = (random.nextBoolean() ? -1 : 1) * (15 + random.nextInt(15));
        int dz = (random.nextBoolean() ? -1 : 1) * (15 + random.nextInt(15));
        BlockPos ground = new BlockPos(player.getBlockX() + dx, player.getBlockY(), player.getBlockZ() + dz);
        BlockPos spawn = ground.above();
        if (!level.isLoaded(ground) || level.getBlockState(ground).isAir() || !level.getBlockState(spawn).isAir()) {
            return;
        }
        Creeper creeper = new Creeper(EntityType.CREEPER, level);
        creeper.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(creeper, level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);
        creeper.setCustomName(Component.literal(EnderMobs.ENDER_QUANTUM_CREEPER_NAME));
        level.addFreshEntity(creeper);
    }
}
