package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.progression.PermadeathConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * D60 periodic Wither: every eligible survivor gets a vanilla Wither summoned on them after
 * {@code PermadeathTimings#witherIntervalMillis} (GAME60 8 min, REAL30 30 min) of real presence in the Overworld.
 *
 * <ul>
 *     <li>One independent counter per UUID, stored in the world: it survives relogs and restarts.</li>
 *     <li>It only runs while that player is online, alive, in survival/adventure mode and in the Overworld; spectators,
 *     creative players and eliminated players do not count, and a stopped server consumes nothing.</li>
 *     <li>Real time from the monotonic {@code EventClock} (lag does not stretch it); a frozen server cannot summon
 *     several Withers at once: at most one per player per tick, and the counter restarts from a full interval.</li>
 *     <li>The counter is only reset once the Wither really joined the world; otherwise it retries.</li>
 *     <li>After a finished final challenge the timers stop unless {@code freezeAfterCampaign = false}.</li>
 *     <li>Optional {@code witherAccumulationLimit} (off by default) postpones a Wither while too many are nearby.</li>
 * </ul>
 */
public final class WitherSpawner {
    private static final double ACCUMULATION_RADIUS = 128.0;
    private static final Map<UUID, Long> LIMIT_LOGGED = new HashMap<>();

    private WitherSpawner() {
    }

    public static void reset() {
        LIMIT_LOGGED.clear();
    }

    public static boolean running() {
        return Permadeath.isRunning() && Permadeath.day() >= DayRules.WITHER_FROM_DAY
                && !(Permadeath.state().finalPhaseState.finished() && PermadeathConfig.freezeAfterCampaign());
    }

    /** Remaining presence time of {@code uuid} before its next Wither (a full interval if it has no counter yet). */
    public static long remainingFor(UUID uuid) {
        return Permadeath.state().witherRemainingMillis.getOrDefault(uuid, Permadeath.timings().witherIntervalMillis());
    }

    /** One server tick: {@code stepMillis} of real time for every eligible player in the Overworld. */
    public static void tick(MinecraftServer server, long stepMillis) {
        if (!running()) {
            return;
        }
        ProgressionState state = Permadeath.state();
        long interval = Permadeath.timings().witherIntervalMillis();
        ServerLevel overworld = server.overworld();
        boolean changed = false;
        for (ServerPlayer player : overworld.players()) {
            if (!Participants.isEligible(player)) {
                continue;
            }
            UUID uuid = player.getUUID();
            long remaining = Math.min(state.witherRemainingMillis.getOrDefault(uuid, interval), interval);
            remaining = Math.max(0L, remaining - Math.max(0L, stepMillis));
            if (remaining == 0L && trySummon(overworld, player)) {
                remaining = interval;
            }
            Long previous = state.witherRemainingMillis.put(uuid, remaining);
            changed |= previous == null || previous != remaining;
        }
        if (changed) {
            state.markChanged();
        }
    }

    private static boolean trySummon(ServerLevel overworld, ServerPlayer player) {
        int limit = PermadeathConfig.witherAccumulationLimit();
        if (limit > 0) {
            int nearby = overworld.getEntitiesOfClass(WitherBoss.class, player.getBoundingBox().inflate(ACCUMULATION_RADIUS), WitherBoss::isAlive).size();
            if (nearby >= limit) {
                long now = overworld.getGameTime();
                Long logged = LIMIT_LOGGED.get(player.getUUID());
                if (logged == null || now - logged >= 1200L) {
                    LIMIT_LOGGED.put(player.getUUID(), now);
                    PermadeathMod.LOGGER.info("[Permadeath] Wither of {} postponed: {} Withers nearby (witherAccumulationLimit={})",
                            player.getScoreboardName(), nearby, limit);
                }
                return false;
            }
        }
        WitherBoss wither = new WitherBoss(EntityType.WITHER, overworld);
        wither.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        if (!overworld.addFreshEntity(wither)) {
            PermadeathMod.LOGGER.warn("[Permadeath] Wither for {} could not be added; retrying", player.getScoreboardName());
            return false;
        }
        LIMIT_LOGGED.remove(player.getUUID());
        PermadeathMod.LOGGER.info("[Permadeath] Wither summoned for {}", player.getScoreboardName());
        return true;
    }
}
