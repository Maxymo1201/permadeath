package com.serthekiller.permadeath.data;

import com.serthekiller.permadeath.PermadeathMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * "Superviviente de los 70 Días" bookkeeping of the Fabric mod ({@code permadeath_survival_achievement}).
 * The achievement itself requires D70 and is therefore NOT obtainable in these D60-final builds; deaths,
 * cheat flags and disqualifications are still recorded so the data of existing worlds is preserved.
 */
public final class SurvivalAchievementData extends SavedData {
    public static final String NAME = "permadeath_survival_achievement";
    public static final long REQUIRED_DAY = 70L;
    public static final long REQUIRED_PLAYTIME_TICKS = 3_240_000L;

    private boolean changeDayUsed;
    private boolean speedrunUsed;
    private boolean beginningPortalCommandUsed;
    private boolean stormResetUsed;
    private final Map<UUID, PlayerRecord> players = new HashMap<>();

    public static SurvivalAchievementData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(SurvivalAchievementData::new, SurvivalAchievementData::load, null), NAME);
    }

    private PlayerRecord record(UUID uuid) {
        return players.computeIfAbsent(uuid, u -> new PlayerRecord());
    }

    public void flagChangeDayUsed(MinecraftServer server) {
        if (!changeDayUsed) {
            changeDayUsed = true;
            setDirty();
            broadcastDisqualification(server, "§e/permadeath setday");
        }
    }

    public void flagBeginningPortalCommandUsed(MinecraftServer server) {
        if (!beginningPortalCommandUsed) {
            beginningPortalCommandUsed = true;
            setDirty();
            broadcastDisqualification(server, "§eel comando de portal manual");
        }
    }

    public void flagStormResetUsed(MinecraftServer server) {
        if (!stormResetUsed) {
            stormResetUsed = true;
            setDirty();
            broadcastDisqualification(server, "§e/permadeath resetstorm§7, §e/permadeath storm §7o §e/permadeath reset");
        }
    }

    private static void broadcastDisqualification(MinecraftServer server, String command) {
        PermadeathMod.LOGGER.warn("{} used: the 70-day survival achievement is disqualified for this world.", command);
        server.getPlayerList().broadcastSystemMessage(Component.literal("§c⚠ " + command
                + " §7fue usado — el logro §fSuperviviente de los 70 Días §7ya no se puede conseguir en este mundo."), false);
    }

    public void recordDeath(UUID uuid) {
        record(uuid).totalDeaths++;
        setDirty();
    }

    public void flagCheatDetected(UUID uuid, String detail) {
        PlayerRecord r = record(uuid);
        if (!r.cheatDetected) {
            r.cheatDetected = true;
            setDirty();
            PermadeathMod.LOGGER.warn("Player {} disqualified from the 70-day achievement ({})", uuid, detail);
        }
    }

    public boolean isWorldDisqualified() {
        return changeDayUsed || speedrunUsed || beginningPortalCommandUsed || stormResetUsed;
    }

    public String progressInfo(ServerPlayer player, int currentDay) {
        PlayerRecord r = record(player.getUUID());
        long playTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        double playedHours = playTicks / 20.0 / 3600.0;
        return String.format(Locale.ROOT,
                "§6=== Superviviente de los 70 Días ===\n§7Este build termina en el día §f60§7: el logro de D70 no está activo.\n"
                        + "§7Día actual: §f%d§7/§f%d\n§7Muertes registradas: %s§f%d\n§7Horas jugadas: §f%.1fh§7/§f45h\n"
                        + "§7Mundo sin trampas (setday/resetstorm/portal manual): %s\n§7Sin comandos prohibidos detectados: %s",
                currentDay, REQUIRED_DAY, r.totalDeaths == 0 ? "§a" : "§c", r.totalDeaths, playedHours,
                isWorldDisqualified() ? "§c✗" : "§a✓", r.cheatDetected ? "§c✗" : "§a✓");
    }

    private static SurvivalAchievementData load(CompoundTag tag, HolderLookup.Provider registries) {
        SurvivalAchievementData m = new SurvivalAchievementData();
        m.changeDayUsed = tag.getBoolean("changeDayUsed");
        m.speedrunUsed = tag.getBoolean("speedrunUsed");
        m.beginningPortalCommandUsed = tag.getBoolean("beginningPortalUsed");
        m.stormResetUsed = tag.getBoolean("stormResetUsed");
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            PlayerRecord r = new PlayerRecord();
            r.totalDeaths = entry.getInt("deaths");
            r.cheatDetected = entry.getBoolean("cheat");
            r.rewardClaimed = entry.getBoolean("claimed");
            m.players.put(entry.getUUID("uuid"), r);
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("changeDayUsed", changeDayUsed);
        tag.putBoolean("speedrunUsed", speedrunUsed);
        tag.putBoolean("beginningPortalUsed", beginningPortalCommandUsed);
        tag.putBoolean("stormResetUsed", stormResetUsed);
        ListTag list = new ListTag();
        players.forEach((uuid, r) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("deaths", r.totalDeaths);
            t.putBoolean("cheat", r.cheatDetected);
            t.putBoolean("claimed", r.rewardClaimed);
            t.putUUID("uuid", uuid);
            list.add(t);
        });
        tag.put("players", list);
        return tag;
    }

    private static final class PlayerRecord {
        int totalDeaths;
        boolean cheatDetected;
        boolean rewardClaimed;
    }
}
