package com.serthekiller.permadeath.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Date, time and cause of every permadeath ({@code permadeath_death_records}, compound "records" uuid -&gt;
 * {Name, Date, Time, Cause}). Used to label the monument heads like the plugin (PlayerDataManager#craftHead).
 */
public final class DeathRecordsData extends SavedData {
    public static final String NAME = "permadeath_death_records";
    private final Map<UUID, Record> records = new HashMap<>();

    public record Record(String name, String date, String time, String cause) {
    }

    public static DeathRecordsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(DeathRecordsData::new, DeathRecordsData::load, null), NAME);
    }

    @Nullable
    public Record get(UUID uuid) {
        return records.get(uuid);
    }

    public void put(UUID uuid, Record record) {
        records.put(uuid, record);
        setDirty();
    }

    /** Spanish cause of death of the plugin (PlayerDataManager#setAutoDeathCause), with its colour codes. */
    public static String causeLabel(@Nullable DamageSource source) {
        if (source == null) {
            return "Causa desconocida.";
        }
        if (source.is(DamageTypes.WITHER)) {
            return "§0Efecto Wither";
        }
        if (source.is(DamageTypes.DRAGON_BREATH)) {
            return "§dEnder Dragon (Breath)";
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return "Explosión";
        }
        if (source.is(DamageTypes.DROWN)) {
            return "Ahogamiento";
        }
        if (source.is(DamageTypes.FALL)) {
            return "Caída";
        }
        if (source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)) {
            return "Lava";
        }
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)) {
            return "Fuego";
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return "Trueno";
        }
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return "Vacío";
        }
        if (source.is(DamageTypes.IN_WALL)) {
            return "Sofocado";
        }
        if (source.is(DamageTypes.THORNS)) {
            return "Espinas";
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return "Proyectil";
        }
        if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK)) {
            return "Mobs";
        }
        if (source.is(DamageTypes.MAGIC) && source.getEntity() == null) {
            // Poison is the only magic damage without a source entity.
            return "Veneno";
        }
        return "Causa desconocida.";
    }

    private static DeathRecordsData load(CompoundTag tag, HolderLookup.Provider registries) {
        DeathRecordsData data = new DeathRecordsData();
        CompoundTag entries = tag.getCompound("records");
        for (String key : entries.getAllKeys()) {
            try {
                CompoundTag e = entries.getCompound(key);
                data.records.put(UUID.fromString(key), new Record(e.getString("Name"), e.getString("Date"), e.getString("Time"), e.getString("Cause")));
            } catch (IllegalArgumentException ignored) {
                // invalid uuid key, skip
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        records.forEach((uuid, r) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Name", r.name());
            e.putString("Date", r.date());
            e.putString("Time", r.time());
            e.putString("Cause", r.cause());
            entries.put(uuid.toString(), e);
        });
        tag.put("records", entries);
        return tag;
    }
}
