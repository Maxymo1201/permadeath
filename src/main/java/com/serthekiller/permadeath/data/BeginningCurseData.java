package com.serthekiller.permadeath.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blessing of the first player entering The Beginning and the "last player" curse (no milk for 12 real
 * hours). Same file and keys as the Fabric {@code BeginningCurseManager}.
 */
public final class BeginningCurseData extends SavedData {
    public static final String NAME = "permadeath_beginning_curse";

    private boolean firstEntryClaimed;
    private final Map<UUID, Long> cursedUntilMillis = new HashMap<>();

    public static BeginningCurseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(BeginningCurseData::new, BeginningCurseData::load, null), NAME);
    }

    public boolean isFirstEntryClaimed() {
        return firstEntryClaimed;
    }

    public void markFirstEntryClaimed() {
        firstEntryClaimed = true;
        setDirty();
    }

    public void curse(UUID uuid, long durationMillis) {
        cursedUntilMillis.put(uuid, System.currentTimeMillis() + durationMillis);
        setDirty();
    }

    public boolean isCursed(UUID uuid) {
        Long until = cursedUntilMillis.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    public void clearExpired() {
        if (cursedUntilMillis.values().removeIf(until -> until <= System.currentTimeMillis())) {
            setDirty();
        }
    }

    private static BeginningCurseData load(CompoundTag tag, HolderLookup.Provider registries) {
        BeginningCurseData data = new BeginningCurseData();
        data.firstEntryClaimed = tag.getBoolean("FirstEntryClaimed");
        ListTag list = tag.getList("Cursed", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.cursedUntilMillis.put(entry.getUUID("UUID"), entry.getLong("Until"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("FirstEntryClaimed", firstEntryClaimed);
        ListTag list = new ListTag();
        cursedUntilMillis.forEach((uuid, until) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", uuid);
            entry.putLong("Until", until);
            list.add(entry);
        });
        tag.put("Cursed", list);
        return tag;
    }
}
