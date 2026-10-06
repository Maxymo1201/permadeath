package com.serthekiller.permadeath.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** "Modo servidor restringido" toggle ({@code permadeath_server_mode}, key "restricted"). */
public final class ServerModeData extends SavedData {
    public static final String NAME = "permadeath_server_mode";
    private boolean restricted;

    public static ServerModeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ServerModeData::new, ServerModeData::load, null), NAME);
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
        setDirty();
    }

    private static ServerModeData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerModeData data = new ServerModeData();
        data.restricted = tag.getBoolean("restricted");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("restricted", restricted);
        return tag;
    }
}
