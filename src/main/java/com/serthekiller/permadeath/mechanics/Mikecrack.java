package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * "Cambio de Mikecrack" (D60, toggled with /permadeath mikecrack): creepers ignore their spawn placement
 * rules (Fabric: Mixins on SpawnPlacements / Monster#checkMonsterSpawnRules, here
 * {@link MobSpawnEvent.SpawnPlacementCheck}) and 5 extra creepers spawn around every Overworld player each 100
 * ticks. The state is stored in the world (Fabric permadeath_mikecrack.txt, migrated).
 */
public final class Mikecrack {
    private static int tickCounter;

    private Mikecrack() {
    }

    public static boolean isEnabled() {
        return Permadeath.isRunning() && Permadeath.state().mikecrackEnabled;
    }

    public static void setEnabled(boolean enabled) {
        Permadeath.state().mikecrackEnabled = enabled;
        Permadeath.state().markChanged();
        PermadeathMod.LOGGER.info("[Permadeath] Mikecrack mode {}", enabled ? "enabled" : "disabled");
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() == EntityType.CREEPER && isEnabled()) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
        }
    }

    public static void tick(MinecraftServer server) {
        if (!isEnabled() || ++tickCounter < 100) {
            return;
        }
        tickCounter = 0;
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : overworld.players()) {
            spawnAround(overworld, player);
        }
    }

    private static void spawnAround(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        int spawned = 0;
        for (int attempt = 0; attempt < 30 && spawned < 5; attempt++) {
            int dx = random.nextInt(49) - 24;
            int dz = random.nextInt(49) - 24;
            if (Math.abs(dx) < 8 && Math.abs(dz) < 8) {
                continue;
            }
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;
            BlockPos candidate;
            if (random.nextDouble() < 0.4) {
                candidate = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
            } else {
                candidate = findSpotInColumn(level, random, x, z, origin.getY());
                if (candidate == null) {
                    continue;
                }
            }
            if (trySpawn(level, random, candidate)) {
                spawned++;
            }
        }
    }

    private static BlockPos findSpotInColumn(ServerLevel level, RandomSource random, int x, int z, int playerY) {
        int minY = level.getMinBuildHeight() + 1;
        int topY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Math.max(topY, playerY + 10));
        if (maxY <= minY) {
            return null;
        }
        for (int i = 0; i < 24; i++) {
            BlockPos pos = new BlockPos(x, minY + random.nextInt(maxY - minY), z);
            if (isValidSpot(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isValidSpot(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above())) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        boolean ground = !below.isAir() || !below.getFluidState().isEmpty();
        return isPassable(level, pos) && isPassable(level, pos.above()) && ground;
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean trySpawn(ServerLevel level, RandomSource random, BlockPos pos) {
        if (!isValidSpot(level, pos)) {
            return false;
        }
        double y = pos.getY();
        BlockPos belowPos = pos.below();
        VoxelShape ground = level.getBlockState(belowPos).getCollisionShape(level, belowPos);
        if (!ground.isEmpty()) {
            y = belowPos.getY() + ground.max(Direction.Axis.Y);
        }
        Creeper creeper = new Creeper(EntityType.CREEPER, level);
        creeper.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        return level.addFreshEntity(creeper);
    }
}
