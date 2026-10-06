package com.serthekiller.permadeath.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Personal death messages ({@code permadeath_custom_messages}, compound "messages" uuid -> text). */
public final class CustomMessagesData extends SavedData {
    public static final String NAME = "permadeath_custom_messages";
    public static final String DEFAULT_MESSAGE = "Ahora por fin descansa en paz...";
    private final Map<UUID, String> messages = new HashMap<>();

    public static CustomMessagesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CustomMessagesData::new, CustomMessagesData::load, null), NAME);
    }

    public String getMessage(UUID uuid) {
        return messages.getOrDefault(uuid, DEFAULT_MESSAGE);
    }

    public void setMessage(UUID uuid, String message) {
        messages.put(uuid, message);
        setDirty();
    }

    private static CustomMessagesData load(CompoundTag tag, HolderLookup.Provider registries) {
        CustomMessagesData data = new CustomMessagesData();
        CompoundTag entries = tag.getCompound("messages");
        for (String key : entries.getAllKeys()) {
            try {
                data.messages.put(UUID.fromString(key), entries.getString(key));
            } catch (IllegalArgumentException ignored) {
                // invalid uuid key, skip
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        messages.forEach((uuid, msg) -> entries.putString(uuid.toString(), msg));
        tag.put("messages", entries);
        return tag;
    }
}
