package com.serthekiller.permadeath.data;

import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.ProgressionState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;

/**
 * Central persisted state ({@code <world>/data/permadeath_progression.dat}), stored in the Overworld data
 * storage so it is saved with the world on autosave, /save-all and /stop and survives backups/restores.
 */
public final class PermadeathData extends SavedData {
    public static final String NAME = "permadeath_progression";

    private final ProgressionState state = new ProgressionState();

    public PermadeathData() {
        state.setChangeListener(this::setDirty);
    }

    public ProgressionState state() {
        return state;
    }

    public static Factory<PermadeathData> factory() {
        return new Factory<>(PermadeathData::new, PermadeathData::load, null);
    }

    public static PermadeathData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static PermadeathData load(CompoundTag tag, HolderLookup.Provider registries) {
        PermadeathData data = new PermadeathData();
        ProgressionState s = data.state;
        s.formatVersion = tag.getInt("FormatVersion");
        if (tag.contains("Mode", Tag.TAG_STRING)) {
            try {
                s.mode = ProgressionMode.parse(tag.getString("Mode"));
            } catch (IllegalArgumentException ignored) {
                s.mode = null;
            }
        }
        s.initialized = tag.getBoolean("Initialized");
        s.baseWorldDay = tag.getLong("BaseWorldDay");
        s.startEpochMillis = tag.getLong("StartEpochMillis");
        s.maxElapsedMillis = tag.getLong("MaxElapsedMillis");
        s.maxEffectiveDay = tag.getInt("MaxEffectiveDay");
        for (int m : tag.getIntArray("ExecutedMilestones")) {
            s.executedMilestones.add(m);
        }
        s.deathTrainEndEpochMillis = tag.getLong("DeathTrainEnd");
        s.deathTrainUhcActive = tag.getBoolean("DeathTrainUhc");
        s.lifeOrbDeadlineEpochMillis = tag.contains("LifeOrbDeadline") ? tag.getLong("LifeOrbDeadline") : -1L;
        s.lifeOrbActive = tag.getBoolean("LifeOrbActive");
        ListTag withers = tag.getList("WitherTimers", Tag.TAG_COMPOUND);
        for (int i = 0; i < withers.size(); i++) {
            CompoundTag e = withers.getCompound(i);
            s.witherRemainingMillis.put(e.getUUID("UUID"), e.getLong("RemainingMillis"));
        }
        s.mikecrackEnabled = tag.getBoolean("Mikecrack");
        s.mikecrackDisabled = tag.getBoolean("MikecrackDisabled");
        ListTag apples = tag.getList("HyperApples", Tag.TAG_COMPOUND);
        for (int i = 0; i < apples.size(); i++) {
            CompoundTag e = apples.getCompound(i);
            s.hyperApplesConsumed.put(e.getUUID("UUID"), e.getInt("Count"));
        }
        s.legacyMigrated = tag.getBoolean("LegacyMigrated");
        s.endArenaPrepared = tag.getBoolean("EndArenaPrepared");
        s.endAltarPositions = tag.getLongArray("EndAltars");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressionState s = state;
        tag.putInt("FormatVersion", ProgressionState.CURRENT_FORMAT_VERSION);
        if (s.mode != null) {
            tag.putString("Mode", s.mode.name());
        }
        tag.putBoolean("Initialized", s.initialized);
        tag.putLong("BaseWorldDay", s.baseWorldDay);
        tag.putLong("StartEpochMillis", s.startEpochMillis);
        tag.putLong("MaxElapsedMillis", s.maxElapsedMillis);
        tag.putInt("MaxEffectiveDay", s.maxEffectiveDay);
        tag.putIntArray("ExecutedMilestones", s.executedMilestones.stream().mapToInt(Integer::intValue).toArray());
        tag.putLong("DeathTrainEnd", s.deathTrainEndEpochMillis);
        tag.putBoolean("DeathTrainUhc", s.deathTrainUhcActive);
        tag.putLong("LifeOrbDeadline", s.lifeOrbDeadlineEpochMillis);
        tag.putBoolean("LifeOrbActive", s.lifeOrbActive);
        ListTag withers = new ListTag();
        for (Map.Entry<UUID, Long> e : s.witherRemainingMillis.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("UUID", e.getKey());
            c.putLong("RemainingMillis", e.getValue());
            withers.add(c);
        }
        tag.put("WitherTimers", withers);
        tag.putBoolean("Mikecrack", s.mikecrackEnabled);
        tag.putBoolean("MikecrackDisabled", s.mikecrackDisabled);
        ListTag apples = new ListTag();
        for (Map.Entry<UUID, Integer> e : s.hyperApplesConsumed.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("UUID", e.getKey());
            c.putInt("Count", e.getValue());
            apples.add(c);
        }
        tag.put("HyperApples", apples);
        tag.putBoolean("LegacyMigrated", s.legacyMigrated);
        tag.putBoolean("EndArenaPrepared", s.endArenaPrepared);
        tag.putLongArray("EndAltars", s.endAltarPositions);
        return tag;
    }
}
