package com.serthekiller.permadeath.mechanics;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who keeps the campaign timers running: an online survivor (survival or adventure mode, alive, not eliminated by a
 * Permadeath). Spectators (dead players, observing admins) and creative players never keep a timer running.
 */
public final class Participants {
    private Participants() {
    }

    public static boolean isEligible(ServerPlayer player) {
        return !player.isSpectator() && !player.isCreative() && player.isAlive() && !player.isRemoved()
                && !player.getTags().contains(DeathHandler.DEATH_TAG);
    }

    public static boolean anyEligible(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isEligible(player)) {
                return true;
            }
        }
        return false;
    }
}
