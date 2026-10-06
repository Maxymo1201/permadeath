package com.serthekiller.permadeath.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Overworld portal to The Beginning (spawned on D40). Same file name and NBT keys as the Fabric mod
 * ({@code permadeath_portal_state_day40}: PortalSpawned / PortalX / PortalY / PortalZ).
 */
public final class PortalState extends SavedData {
    public static final String NAME = "permadeath_portal_state_day40";

    public boolean hasSpawned;
    public int portalX = Integer.MIN_VALUE;
    public int portalY = Integer.MIN_VALUE;
    public int portalZ = Integer.MIN_VALUE;

    public static PortalState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PortalState::new, PortalState::load, null), NAME);
    }

    private static PortalState load(CompoundTag tag, HolderLookup.Provider registries) {
        PortalState state = new PortalState();
        state.hasSpawned = tag.getBoolean("PortalSpawned");
        if (tag.contains("PortalX")) {
            state.portalX = tag.getInt("PortalX");
            state.portalY = tag.getInt("PortalY");
            state.portalZ = tag.getInt("PortalZ");
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("PortalSpawned", hasSpawned);
        if (portalX != Integer.MIN_VALUE) {
            tag.putInt("PortalX", portalX);
            tag.putInt("PortalY", portalY);
            tag.putInt("PortalZ", portalZ);
        }
        return tag;
    }

    @Nullable
    public BlockPos getPortalPos() {
        return hasSpawned && portalX != Integer.MIN_VALUE ? new BlockPos(portalX, portalY, portalZ) : null;
    }
}
