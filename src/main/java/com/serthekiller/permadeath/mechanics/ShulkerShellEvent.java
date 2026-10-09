package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.core.time.CampaignTimers;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * "X2 Shulker Shells" admin event (plugin /pdc event shulkershell): every shulker shell drop is a stack of two while it
 * lasts ({@code PermadeathTimings#shulkerEventMillis}: GAME60 10 min, REAL30 2 h of active time). The remaining time
 * is stored in the world and shown in a red boss bar; it only runs while an eligible survivor is online.
 */
public final class ShulkerShellEvent {
    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(Component.literal("X2 Shulker Shells"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private ShulkerShellEvent() {
    }

    public static void reset() {
        BOSS_BAR.removeAllPlayers();
    }

    public static boolean isActive() {
        return Permadeath.isRunning() && Permadeath.state().shulkerEventRemainingMillis > 0L;
    }

    public static long remainingMillis() {
        return Permadeath.isRunning() ? Math.max(0L, Permadeath.state().shulkerEventRemainingMillis) : 0L;
    }

    /** Shulker shells per drop: 2 while the event runs, 1 otherwise. */
    public static int shellsPerDrop() {
        return isActive() ? 2 : 1;
    }

    /** @return false when the event is already running */
    public static boolean start() {
        return CampaignTimers.startShulkerEvent(Permadeath.state(), Permadeath.timings());
    }

    /**
     * Logged out players, and the old object of a respawned player, leave the boss bar: it kept them, and a closed
     * connection queues every packet sent to it.
     */
    public static void removePlayer(ServerPlayer player) {
        BOSS_BAR.removePlayer(player);
    }

    /** Boss bar and end announcement; the time itself is advanced by {@link CampaignTimers#advance}. */
    public static void tick(MinecraftServer server, boolean ended) {
        ProgressionState state = Permadeath.state();
        if (ended) {
            BOSS_BAR.removeAllPlayers();
            Texts.broadcast(server, "§cPermadeath §7➤ §eEl evento de §c§lX2 Shulker Shells §eha acabado.");
            return;
        }
        long remaining = state.shulkerEventRemainingMillis;
        if (remaining <= 0L) {
            BOSS_BAR.removeAllPlayers();
            return;
        }
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long duration = Math.max(remaining, Permadeath.timings().shulkerEventMillis());
        BOSS_BAR.setName(Component.literal("§e§lX2 Shulker Shells: §b§n" + TimeFormat.compact(remaining)));
        BOSS_BAR.setProgress(Math.min(1.0F, (float) remaining / duration));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BOSS_BAR.addPlayer(player);
        }
    }
}
