package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * "X2 Shulker Shells" admin event (plugin /pdc event shulkershell): for 4 real hours every shulker shell drop is a
 * stack of two. The end is an absolute timestamp stored in the world, shown in a red boss bar.
 */
public final class ShulkerShellEvent {
    public static final long DURATION_MILLIS = 4L * 60L * 60_000L;
    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(Component.literal("X2 Shulker Shells"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private ShulkerShellEvent() {
    }

    public static void reset() {
        BOSS_BAR.removeAllPlayers();
    }

    public static boolean isActive() {
        return Permadeath.isRunning() && Permadeath.state().shulkerEventEndEpochMillis > Permadeath.nowMillis();
    }

    /** @return false when the event is already running */
    public static boolean start() {
        if (isActive()) {
            return false;
        }
        ProgressionState state = Permadeath.state();
        state.shulkerEventEndEpochMillis = Permadeath.nowMillis() + DURATION_MILLIS;
        state.markChanged();
        return true;
    }

    /**
     * Logged out players, and the old object of a respawned player, leave the boss bar: it kept them, and a closed
     * connection queues every packet sent to it.
     */
    public static void removePlayer(ServerPlayer player) {
        BOSS_BAR.removePlayer(player);
    }

    public static void tick(MinecraftServer server) {
        ProgressionState state = Permadeath.state();
        if (state.shulkerEventEndEpochMillis <= 0L || server.getTickCount() % 20 != 0) {
            return;
        }
        long remaining = state.shulkerEventEndEpochMillis - Permadeath.nowMillis();
        if (remaining <= 0L) {
            state.shulkerEventEndEpochMillis = 0L;
            state.markChanged();
            BOSS_BAR.removeAllPlayers();
            Texts.broadcast(server, "§cPermadeath §7➤ §eEl evento de §c§lX2 Shulker Shells §eha acabado.");
            return;
        }
        BOSS_BAR.setName(Component.literal("§e§lX2 Shulker Shells: §b§n" + TimeFormat.hms(remaining)));
        BOSS_BAR.setProgress((float) remaining / DURATION_MILLIS);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BOSS_BAR.addPlayer(player);
        }
    }
}
