package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * D60: every player gets a Wither spawned on him after 60 real minutes of presence in the Overworld (Fabric:
 * 72000 server ticks, which is longer than one hour whenever the TPS drops). The remaining time of every
 * player is stored in the world and survives restarts; time only counts while the player is online in the
 * Overworld.
 */
public final class WitherSpawner {
    /** Longest real gap counted between two ticks (protects against lag spikes and clock jumps). */
    private static final long MAX_STEP_MILLIS = 5_000L;
    private static long lastTickMillis = -1L;
    private static long saveCounter;

    private WitherSpawner() {
    }

    public static void reset() {
        lastTickMillis = -1L;
        saveCounter = 0;
    }

    public static void tick(MinecraftServer server) {
        if (!Permadeath.isRunning()) {
            return;
        }
        long now = Permadeath.nowMillis();
        long step = lastTickMillis < 0L ? 0L : Math.max(0L, Math.min(MAX_STEP_MILLIS, now - lastTickMillis));
        lastTickMillis = now;
        if (Permadeath.day() < DayRules.WITHER_FROM_DAY) {
            return;
        }
        ProgressionState state = Permadeath.state();
        ServerLevel overworld = server.overworld();
        boolean dirty = false;
        for (ServerPlayer player : overworld.players()) {
            if (player.isSpectator()) {
                continue;
            }
            long remaining = state.witherRemainingMillis.getOrDefault(player.getUUID(), DayRules.WITHER_INTERVAL_MILLIS) - step;
            if (remaining <= 0L) {
                WitherBoss wither = new WitherBoss(EntityType.WITHER, overworld);
                wither.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                overworld.addFreshEntity(wither);
                PermadeathMod.LOGGER.info("[Permadeath] Wither summoned for {}", player.getScoreboardName());
                remaining = DayRules.WITHER_INTERVAL_MILLIS;
                dirty = true;
            }
            state.witherRemainingMillis.put(player.getUUID(), remaining);
        }
        if (dirty || ++saveCounter % 400 == 0) {
            state.markChanged();
        }
    }
}
