package com.serthekiller.permadeath.data;

import com.serthekiller.permadeath.core.FinalParticipant;
import com.serthekiller.permadeath.core.FinalPhaseState;
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
    /** Original format 1 timer values of a migrated world, kept in the file for reference. */
    private CompoundTag formatV1;

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
        s.deathTrainUhcActive = tag.getBoolean("DeathTrainUhc");
        s.lifeOrbActive = tag.getBoolean("LifeOrbActive");
        if (s.formatVersion < 2) {
            // Format 1 absolute timestamps: converted to remaining active time by TimerMigration on server start.
            s.legacyTimers = new ProgressionState.LegacyTimers(tag.getLong("DeathTrainEnd"),
                    tag.contains("LifeOrbDeadline") ? tag.getLong("LifeOrbDeadline") : -1L, tag.getLong("ShulkerEventEnd"));
        } else {
            s.deathTrainRemainingMillis = tag.getLong("DeathTrainRemaining");
            s.lifeOrbRemainingMillis = tag.contains("LifeOrbRemaining") ? tag.getLong("LifeOrbRemaining") : -1L;
            s.shulkerEventRemainingMillis = tag.getLong("ShulkerEventRemaining");
            s.finalPhaseState = parseFinalState(tag.getString("FinalPhase"));
            s.finalPhaseRemainingMillis = tag.getLong("FinalPhaseRemaining");
            s.finalPhaseStartedEpochMillis = tag.getLong("FinalPhaseStarted");
            s.finalPhaseEndedEpochMillis = tag.getLong("FinalPhaseEnded");
            ListTag participants = tag.getList("FinalParticipants", Tag.TAG_COMPOUND);
            for (int i = 0; i < participants.size(); i++) {
                CompoundTag e = participants.getCompound(i);
                FinalParticipant p = new FinalParticipant(e.getString("Name"));
                p.eliminated = e.getBoolean("Eliminated");
                p.lifeOrbBeforeDeadline = e.getBoolean("LifeOrbInTime");
                p.holdingLifeOrb = e.getBoolean("HoldingLifeOrb");
                p.survivedLastSeen = e.getBoolean("Survived");
                p.result = parseResult(e.getString("Result"));
                s.finalParticipants.put(e.getUUID("UUID"), p);
            }
            s.migratedFromVersion = tag.getInt("MigratedFrom");
            s.migrationEpochMillis = tag.getLong("MigrationTime");
            s.migrationSummary = tag.getString("MigrationSummary");
        }
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
        if (tag.contains("FormatV1", Tag.TAG_COMPOUND)) {
            data.formatV1 = tag.getCompound("FormatV1");
        } else if (s.formatVersion < 2) {
            // Keep the original format 1 timer values in the file (not needed any more, but never thrown away).
            CompoundTag v1 = new CompoundTag();
            for (String key : new String[]{"DeathTrainEnd", "LifeOrbDeadline", "ShulkerEventEnd"}) {
                if (tag.contains(key)) {
                    v1.putLong(key, tag.getLong(key));
                }
            }
            data.formatV1 = v1;
        }
        return data;
    }

    private static FinalPhaseState parseFinalState(String raw) {
        try {
            return raw.isEmpty() ? FinalPhaseState.NOT_STARTED : FinalPhaseState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return FinalPhaseState.NOT_STARTED;
        }
    }

    private static FinalParticipant.Result parseResult(String raw) {
        try {
            return raw.isEmpty() ? FinalParticipant.Result.PENDING : FinalParticipant.Result.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return FinalParticipant.Result.PENDING;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressionState s = state;
        tag.putInt("FormatVersion", s.formatVersion);
        if (s.legacyTimers != null) {
            // Loaded from format 1 but not migrated yet (the server did not start): keep the old values as they were.
            tag.putLong("DeathTrainEnd", s.legacyTimers.deathTrainEndEpochMillis());
            tag.putLong("LifeOrbDeadline", s.legacyTimers.lifeOrbDeadlineEpochMillis());
            tag.putLong("ShulkerEventEnd", s.legacyTimers.shulkerEventEndEpochMillis());
        }
        if (s.mode != null) {
            tag.putString("Mode", s.mode.name());
        }
        tag.putBoolean("Initialized", s.initialized);
        tag.putLong("BaseWorldDay", s.baseWorldDay);
        tag.putLong("StartEpochMillis", s.startEpochMillis);
        tag.putLong("MaxElapsedMillis", s.maxElapsedMillis);
        tag.putInt("MaxEffectiveDay", s.maxEffectiveDay);
        tag.putIntArray("ExecutedMilestones", s.executedMilestones.stream().mapToInt(Integer::intValue).toArray());
        tag.putLong("DeathTrainRemaining", s.deathTrainRemainingMillis);
        tag.putBoolean("DeathTrainUhc", s.deathTrainUhcActive);
        tag.putLong("LifeOrbRemaining", s.lifeOrbRemainingMillis);
        tag.putBoolean("LifeOrbActive", s.lifeOrbActive);
        tag.putLong("ShulkerEventRemaining", s.shulkerEventRemainingMillis);
        tag.putString("FinalPhase", s.finalPhaseState.name());
        tag.putLong("FinalPhaseRemaining", s.finalPhaseRemainingMillis);
        tag.putLong("FinalPhaseStarted", s.finalPhaseStartedEpochMillis);
        tag.putLong("FinalPhaseEnded", s.finalPhaseEndedEpochMillis);
        ListTag participants = new ListTag();
        for (Map.Entry<UUID, FinalParticipant> e : s.finalParticipants.entrySet()) {
            FinalParticipant p = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putUUID("UUID", e.getKey());
            c.putString("Name", p.name == null ? "" : p.name);
            c.putBoolean("Eliminated", p.eliminated);
            c.putBoolean("LifeOrbInTime", p.lifeOrbBeforeDeadline);
            c.putBoolean("HoldingLifeOrb", p.holdingLifeOrb);
            c.putBoolean("Survived", p.survivedLastSeen);
            c.putString("Result", p.result.name());
            participants.add(c);
        }
        tag.put("FinalParticipants", participants);
        tag.putInt("MigratedFrom", s.migratedFromVersion);
        tag.putLong("MigrationTime", s.migrationEpochMillis);
        tag.putString("MigrationSummary", s.migrationSummary == null ? "" : s.migrationSummary);
        if (formatV1 != null) {
            tag.put("FormatV1", formatV1);
        }
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
