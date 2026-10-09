package com.serthekiller.permadeath.beginning;

import com.serthekiller.permadeath.data.BeginningCurseData;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.UUID;

/**
 * Keeps the potion effects of The Beginning curse (Slowness I + Weakness I) and blessing (Resistance II) on the same
 * clock as their timers in {@link BeginningCurseData}: every second the effect duration is set to the remaining real
 * time, so low TPS, relogs or restarts cannot make the effect end before or after the milk ban.
 *
 * <ul>
 *     <li>The time of a player only runs while that player is online, alive and not a spectator.</li>
 *     <li>Curse: the effects are put back while it lasts (they are part of the curse; milk kills a cursed player).</li>
 *     <li>Blessing: it is a normal effect, so if the player loses it (milk, death) the blessing ends.</li>
 * </ul>
 */
public final class BeginningEffects {
    /** Largest drift (ticks) tolerated between an effect and its timer before it is corrected. */
    private static final int MAX_DRIFT_TICKS = 40;

    private BeginningEffects() {
    }

    /**
     * @param stepMillis real time of this tick
     * @param sync       correct the effect durations this tick (once per second)
     */
    public static void tick(MinecraftServer server, long stepMillis, boolean sync) {
        BeginningCurseData data = BeginningCurseData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            boolean playing = player.isAlive() && !player.isSpectator();
            long curse = data.curseRemaining(uuid);
            if (curse > 0L) {
                long left = playing ? curse - Math.max(0L, stepMillis) : curse;
                data.setCurseRemaining(uuid, left);
                if (left <= 0L) {
                    endEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
                    endEffect(player, MobEffects.WEAKNESS, 0);
                } else if (playing && sync) {
                    keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0, left);
                    keepEffect(player, MobEffects.WEAKNESS, 0, left);
                }
            }
            long blessing = data.blessingRemaining(uuid);
            if (blessing > 0L) {
                long left = playing ? blessing - Math.max(0L, stepMillis) : blessing;
                if (playing && !hasEffect(player, MobEffects.DAMAGE_RESISTANCE, 1)) {
                    left = 0L; // removed by milk or lost on death: the blessing is over
                }
                data.setBlessingRemaining(uuid, left);
                if (left <= 0L) {
                    endEffect(player, MobEffects.DAMAGE_RESISTANCE, 1);
                } else if (playing && sync) {
                    keepEffect(player, MobEffects.DAMAGE_RESISTANCE, 1, left);
                }
            }
        }
    }

    /** Applies the curse effects right away for its whole remaining time. */
    public static void applyCurse(ServerPlayer player, long millis) {
        keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0, millis);
        keepEffect(player, MobEffects.WEAKNESS, 0, millis);
    }

    public static void applyBlessing(ServerPlayer player, long millis) {
        keepEffect(player, MobEffects.DAMAGE_RESISTANCE, 1, millis);
    }

    private static int ticksFor(long millis) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (millis + 49L) / 50L));
    }

    private static boolean hasEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        return current != null && current.getAmplifier() == amplifier;
    }

    private static void keepEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, long millis) {
        int ticks = ticksFor(millis);
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier && Math.abs(current.getDuration() - ticks) <= MAX_DRIFT_TICKS) {
            return;
        }
        if (current != null && current.getAmplifier() <= amplifier) {
            player.removeEffect(effect);
        }
        player.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true, true));
    }

    private static void endEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        if (hasEffect(player, effect, amplifier)) {
            player.removeEffect(effect);
        }
    }
}
