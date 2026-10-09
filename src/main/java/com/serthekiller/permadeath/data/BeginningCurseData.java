package com.serthekiller.permadeath.data;

import com.serthekiller.permadeath.BuildProfile;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.time.PermadeathTimings;
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
 * Blessing of the first player entering The Beginning and the "last player" curse (no milk, Slowness, Weakness), with
 * the remaining time of each player ({@code PermadeathTimings}: GAME60 10 min, REAL30 6 h). The time of a player only
 * runs while that player is online and playing, so the milk ban and the potion effects always end together.
 *
 * <p>Format 1 (Fabric {@code BeginningCurseManager} keys) stored an absolute "until" timestamp: it is converted once to
 * the observable remaining time, at most the curse duration of the profile.</p>
 */
public final class BeginningCurseData extends SavedData {
    public static final String NAME = "permadeath_beginning_curse";
    private static final int FORMAT_VERSION = 2;

    private boolean firstEntryClaimed;
    private final Map<UUID, Long> curseRemainingMillis = new HashMap<>();
    private final Map<UUID, Long> blessingRemainingMillis = new HashMap<>();

    public static Factory<BeginningCurseData> factory() {
        return new Factory<>(BeginningCurseData::new, BeginningCurseData::load, null);
    }

    public static BeginningCurseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public boolean isFirstEntryClaimed() {
        return firstEntryClaimed;
    }

    public void markFirstEntryClaimed() {
        firstEntryClaimed = true;
        setDirty();
    }

    public void curse(UUID uuid, long durationMillis) {
        curseRemainingMillis.put(uuid, durationMillis);
        setDirty();
    }

    public void bless(UUID uuid, long durationMillis) {
        blessingRemainingMillis.put(uuid, durationMillis);
        setDirty();
    }

    public boolean isCursed(UUID uuid) {
        return curseRemaining(uuid) > 0L;
    }

    public long curseRemaining(UUID uuid) {
        return curseRemainingMillis.getOrDefault(uuid, 0L);
    }

    public long blessingRemaining(UUID uuid) {
        return blessingRemainingMillis.getOrDefault(uuid, 0L);
    }

    public Map<UUID, Long> curses() {
        return Map.copyOf(curseRemainingMillis);
    }

    public Map<UUID, Long> blessings() {
        return Map.copyOf(blessingRemainingMillis);
    }

    /** Sets the remaining curse time; 0 or less ends it. */
    public void setCurseRemaining(UUID uuid, long millis) {
        if (millis <= 0L) {
            if (curseRemainingMillis.remove(uuid) != null) {
                setDirty();
            }
        } else if (!Long.valueOf(millis).equals(curseRemainingMillis.put(uuid, millis))) {
            setDirty();
        }
    }

    /** Sets the remaining blessing time; 0 or less ends it. */
    public void setBlessingRemaining(UUID uuid, long millis) {
        if (millis <= 0L) {
            if (blessingRemainingMillis.remove(uuid) != null) {
                setDirty();
            }
        } else if (!Long.valueOf(millis).equals(blessingRemainingMillis.put(uuid, millis))) {
            setDirty();
        }
    }

    private static BeginningCurseData load(CompoundTag tag, HolderLookup.Provider registries) {
        BeginningCurseData data = new BeginningCurseData();
        data.firstEntryClaimed = tag.getBoolean("FirstEntryClaimed");
        if (tag.getInt("FormatVersion") < FORMAT_VERSION) {
            long now = System.currentTimeMillis();
            long cap = PermadeathTimings.forMode(BuildProfile.mode()).beginningCurseMillis();
            ListTag list = tag.getList("Cursed", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                long left = Math.min(cap, entry.getLong("Until") - now);
                if (left > 0L) {
                    data.curseRemainingMillis.put(entry.getUUID("UUID"), left);
                }
            }
            if (!list.isEmpty()) {
                PermadeathMod.LOGGER.info("[Permadeath] Beginning curse data migrated to format 2: {} of {} curses still running",
                        data.curseRemainingMillis.size(), list.size());
                data.setDirty();
            }
        } else {
            readTimers(tag.getList("Curses", Tag.TAG_COMPOUND), data.curseRemainingMillis);
            readTimers(tag.getList("Blessings", Tag.TAG_COMPOUND), data.blessingRemainingMillis);
        }
        return data;
    }

    private static void readTimers(ListTag list, Map<UUID, Long> target) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long left = entry.getLong("RemainingMillis");
            if (left > 0L) {
                target.put(entry.getUUID("UUID"), left);
            }
        }
    }

    private static ListTag writeTimers(Map<UUID, Long> timers) {
        ListTag list = new ListTag();
        timers.forEach((uuid, left) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", uuid);
            entry.putLong("RemainingMillis", left);
            list.add(entry);
        });
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        tag.putBoolean("FirstEntryClaimed", firstEntryClaimed);
        tag.put("Curses", writeTimers(curseRemainingMillis));
        tag.put("Blessings", writeTimers(blessingRemainingMillis));
        return tag;
    }
}
