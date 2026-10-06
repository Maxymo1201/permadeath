package com.serthekiller.permadeath.beginning;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.data.PortalState;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.DimensionTransition;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Portals between the Overworld and The Beginning (Fabric Day40to49Handler#spawnCustomPortal and
 * TheBeginningEventHandler). The structure template keeps the original (misspelled) id
 * {@code permadeath:begginningportal}.
 */
public final class BeginningPortal {
    public static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, "begginningportal");
    /** Portal platform in The Beginning (corner/anchor) and the arrival point of the players. */
    public static final BlockPos BEGINNING_PORTAL_ANCHOR = new BlockPos(0, 235, 0);
    public static final Vec3 BEGINNING_ARRIVAL = new Vec3(1.5, 238.0, 1.5);

    private BeginningPortal() {
    }

    /**
     * D40: places the portal structure once per world at a random position (±2500) 40 blocks above the
     * surface of the Overworld. Persisted in {@link PortalState} (same file as Fabric, so a Fabric world that
     * already has its portal does not get a second one).
     */
    @Nullable
    public static BlockPos spawnOverworldPortal(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        PortalState state = PortalState.get(server);
        if (state.hasSpawned) {
            return null;
        }
        int x = overworld.getRandom().nextInt(5001) - 2500;
        int z = overworld.getRandom().nextInt(5001) - 2500;
        overworld.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        int surfaceY = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos pos = new BlockPos(x, surfaceY + 40, z);
        Optional<StructureTemplate> template = server.getStructureManager().get(TEMPLATE);
        if (template.isEmpty()) {
            PermadeathMod.LOGGER.error("[Permadeath] Structure template {} not found: the portal to The Beginning was not generated", TEMPLATE);
            return null;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE).setIgnoreEntities(false);
        template.get().placeInWorld(overworld, pos, pos, settings, overworld.getRandom(), 3);
        state.hasSpawned = true;
        state.portalX = pos.getX();
        state.portalY = pos.getY();
        state.portalZ = pos.getZ();
        state.setDirty();
        PermadeathMod.LOGGER.info("[Permadeath] Portal to The Beginning generated at {}", pos.toShortString());
        return pos;
    }

    /** Makes sure the arrival platform exists in The Beginning and returns the arrival point. */
    public static Vec3 ensurePortalAndGetSpawn(ServerLevel beginning) {
        beginning.getChunk(0, 0, ChunkStatus.FULL, true);
        if (!isPortalPresent(beginning, BEGINNING_PORTAL_ANCHOR)) {
            placePortalStructure(beginning, BEGINNING_PORTAL_ANCHOR);
        }
        return BEGINNING_ARRIVAL;
    }

    private static boolean isPortalPresent(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.getBlockState(center.offset(dx, 1, dz)).isAir()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void placePortalStructure(ServerLevel level, BlockPos anchor) {
        Optional<StructureTemplate> template = level.getServer().getStructureManager().get(TEMPLATE);
        if (template.isPresent()) {
            Vec3i size = template.get().getSize();
            BlockPos pos = anchor.offset(-(size.getX() / 2), 0, -(size.getZ() / 2));
            StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE).setIgnoreEntities(false);
            template.get().placeInWorld(level, pos, pos, settings, level.getRandom(), 3);
        } else {
            placeFallbackPlatform(level, anchor);
        }
    }

    private static void placeFallbackPlatform(ServerLevel level, BlockPos center) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(center.offset(dx, 0, dz), Blocks.PURPUR_BLOCK.defaultBlockState(), 3);
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            level.setBlock(center.offset(dx, 1, -3), Blocks.PURPUR_PILLAR.defaultBlockState(), 3);
            level.setBlock(center.offset(dx, 1, 3), Blocks.PURPUR_PILLAR.defaultBlockState(), 3);
        }
        for (int dz = -2; dz <= 2; dz++) {
            level.setBlock(center.offset(-3, 1, dz), Blocks.PURPUR_PILLAR.defaultBlockState(), 3);
            level.setBlock(center.offset(3, 1, dz), Blocks.PURPUR_PILLAR.defaultBlockState(), 3);
        }
    }

    /** Overworld bed/respawn point of the player, or the world spawn. */
    public static Vec3 getPlayerSpawnInOverworld(ServerPlayer player, ServerLevel overworld) {
        ResourceKey<Level> respawnDimension = player.getRespawnDimension();
        BlockPos respawnPos = player.getRespawnPosition();
        if (respawnPos != null && respawnDimension.equals(Level.OVERWORLD)) {
            // keepInventory = true: never consumes a respawn anchor charge (they do not work in the Overworld anyway).
            DimensionTransition respawn = player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
            if (!respawn.pos().equals(Vec3.ZERO)) {
                return respawn.pos();
            }
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    // ------------------------------------------------------------------------------------------------ gateways

    /** Result of {@link #gatewayOverride}: {@code overridden == false} keeps the vanilla destination. */
    public record GatewayOverride(boolean overridden, @Nullable DimensionTransition transition) {
        public static final GatewayOverride VANILLA = new GatewayOverride(false, null);
        public static final GatewayOverride NO_TELEPORT = new GatewayOverride(true, null);
    }

    /**
     * Destination of an End gateway used by a player (Fabric EndGatewayMixin):
     * <ul>
     *     <li>D40-49, Overworld: the player is sent to y = -100 (the void) and the gateway does nothing else -
     *     The Beginning opens on D50 (PermaDeathCore killed the player with "entró a TheBeginning antes de tiempo").</li>
     *     <li>D50+, Overworld: to the arrival platform of The Beginning, or to the player's spawn while a Death
     *     Train storm is active.</li>
     *     <li>D50+, The Beginning: back to the player's spawn in the Overworld.</li>
     * </ul>
     */
    public static GatewayOverride gatewayOverride(ServerLevel level, Entity entity) {
        if (!(entity instanceof ServerPlayer player) || !Permadeath.isRunning()) {
            return GatewayOverride.VANILLA;
        }
        int day = Permadeath.day();
        MinecraftServer server = level.getServer();
        if (level.dimension() == Level.OVERWORLD && day >= 40 && day < 50) {
            if (!player.isSpectator()) {
                player.teleportTo(player.getX(), -100.0, player.getZ());
                return GatewayOverride.NO_TELEPORT;
            }
            return GatewayOverride.VANILLA;
        }
        if (level.dimension() == Level.OVERWORLD && day >= 50) {
            ServerLevel beginning = BeginningDimension.level(server);
            if (beginning == null) {
                return GatewayOverride.VANILLA;
            }
            if (!DeathTrain.isActive()) {
                Vec3 target = ensurePortalAndGetSpawn(beginning);
                return new GatewayOverride(true, new DimensionTransition(beginning, target, player.getDeltaMovement(),
                        player.getYRot(), player.getXRot() + 1.0F, DimensionTransition.PLACE_PORTAL_TICKET));
            }
            ServerLevel overworld = server.overworld();
            return new GatewayOverride(true, new DimensionTransition(overworld, getPlayerSpawnInOverworld(player, overworld), Vec3.ZERO,
                    player.getYRot(), player.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET));
        }
        if (level.dimension() == BeginningDimension.LEVEL_KEY && day >= 50) {
            ServerLevel overworld = server.overworld();
            return new GatewayOverride(true, new DimensionTransition(overworld, getPlayerSpawnInOverworld(player, overworld), Vec3.ZERO,
                    player.getYRot(), player.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET));
        }
        return GatewayOverride.VANILLA;
    }

    /** Teleport used by the portals and the storm expulsion (Fabric TeleportTarget + ADD_PORTAL_CHUNK_TICKET). */
    public static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
        player.changeDimension(new DimensionTransition(level, pos, Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET));
    }
}
