package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.core.time.CampaignTimers;
import com.serthekiller.permadeath.core.time.PermadeathTimings;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;

/**
 * Death Train (Fabric PermadeathUtils storm). Every death adds the historical duration of {@link DayRules} scaled to
 * the build profile ({@link PermadeathTimings#deathTrainMillis}: GAME60 / 72 with at least 60 s, REAL30 / 2) to the
 * time that is left; several deaths add up and never restart the storm.
 *
 * <p>The storm is stored as remaining ACTIVE time: it only runs while the server runs and at least one eligible
 * survivor is online ({@link Participants}), measured with a monotonic clock, so lag/TPS do not stretch it, a stopped
 * or empty server does not consume it and a restart keeps exactly what was left.</p>
 *
 * <p>While it lasts: permanent thunderstorm in the Overworld, a timer in the action bar, D50+ no natural
 * regeneration ("modo UHC", restored only if this storm turned it off). From D25 every death gives every mob alive
 * Strength, Resistance and Speed (I, II from D50) and, on D50-59, Fire Resistance, for the rest of its life, and mobs
 * spawning during the storm get them too (plugin deathTrainEffects).</p>
 */
public final class DeathTrain {
    private static boolean pausedReported;

    private DeathTrain() {
    }

    public static boolean isActive() {
        return Permadeath.isRunning() && Permadeath.state().deathTrainRemainingMillis > 0L;
    }

    public static long remainingMillis() {
        return Permadeath.isRunning() ? Math.max(0L, Permadeath.state().deathTrainRemainingMillis) : 0L;
    }

    /** Adds the storm of one death on {@code day} (scaled once, to the profile); returns the added duration. */
    public static long trigger(MinecraftServer server, int day) {
        ProgressionState state = Permadeath.state();
        long added = CampaignTimers.addDeathTrain(state, Permadeath.timings(), day);
        applyWeather(server.overworld(), state.deathTrainRemainingMillis);
        if (DayRules.deathTrainBuffAmplifier(day) >= 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Mob mob && mob.isAlive()) {
                        applyBuffs(mob, day);
                    }
                }
            }
        }
        PermadeathMod.LOGGER.info("[Permadeath] Death Train +{} (day {}, {}), {} left", TimeFormat.compact(added), day, Permadeath.mode(),
                TimeFormat.compact(state.deathTrainRemainingMillis));
        return added;
    }

    private static void applyWeather(ServerLevel overworld, long remainingMillis) {
        int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(20L, remainingMillis / 50L));
        overworld.setWeatherParameters(0, ticks, true, true);
    }

    /**
     * Storm state of one server tick (weather, UHC rule, action bar). The time itself is advanced by
     * {@link CampaignTimers#advance} in {@link CampaignTicker}; {@code ended} is its "storm finished" transition.
     */
    public static void tick(MinecraftServer server, boolean ended, boolean anyEligible) {
        if (!Permadeath.isRunning()) {
            return;
        }
        ProgressionState state = Permadeath.state();
        int day = Permadeath.day();
        long remaining = state.deathTrainRemainingMillis;
        if (remaining > 0L) {
            ServerLevel overworld = server.overworld();
            if (!overworld.isRaining() || !overworld.isThundering()) {
                applyWeather(overworld, remaining);
            }
            if (DayRules.deathTrainDisablesRegeneration(day) && !state.deathTrainUhcActive
                    && server.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
                // Only when the storm really changes the rule: a server that keeps it off must not get it back on later.
                setNaturalRegeneration(server, false);
                state.deathTrainUhcActive = true;
                state.markChanged();
            } else if (!DayRules.deathTrainDisablesRegeneration(day) && state.deathTrainUhcActive) {
                // Day set back below D50 during the storm: the UHC rule only exists from D50.
                setNaturalRegeneration(server, true);
                state.deathTrainUhcActive = false;
                state.markChanged();
            }
            if (server.getTickCount() % 20 == 0) {
                Component timer = Component.literal("Quedan: " + TimeFormat.compact(remaining) + " de tormenta"
                        + (anyEligible ? "" : " (en pausa: ningún superviviente conectado)")).withStyle(ChatFormatting.GRAY);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.displayClientMessage(timer, true);
                }
            }
            if (!anyEligible && !pausedReported) {
                pausedReported = true;
                PermadeathMod.LOGGER.info("[Permadeath] Death Train paused ({} left): no eligible survivor online", TimeFormat.compact(remaining));
            } else if (anyEligible) {
                pausedReported = false;
            }
            return;
        }
        if (ended) {
            finish(server, true);
        } else if (state.deathTrainUhcActive) {
            // A storm that ended earlier (e.g. while migrating an old world): only restore what it changed.
            finish(server, false);
        }
    }

    private static void finish(MinecraftServer server, boolean announce) {
        ProgressionState state = Permadeath.state();
        state.deathTrainRemainingMillis = 0L;
        if (announce) {
            server.overworld().setWeatherParameters(6000, 0, false, false);
        }
        if (state.deathTrainUhcActive) {
            setNaturalRegeneration(server, true);
            state.deathTrainUhcActive = false;
        }
        state.markChanged();
        if (announce) {
            Texts.broadcast(server, Component.literal("La Tormenta ha finalizado...").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
            PermadeathMod.LOGGER.info("[Permadeath] Death Train finished");
        }
    }

    /** Permanent Death Train buffs of one mob (plugin deathTrainEffects): nothing before D25. */
    public static void applyBuffs(Mob mob, int day) {
        int amplifier = DayRules.deathTrainBuffAmplifier(day);
        if (amplifier < 0) {
            return;
        }
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, amplifier, false, true));
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, amplifier, false, true));
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, amplifier, false, true));
        if (DayRules.deathTrainFireResistance(day)) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, true));
        }
    }

    /** /permadeath storm add...: effective real time chosen by an administrator (never scaled to the profile). */
    public static void addMillis(MinecraftServer server, long millis) {
        ProgressionState state = Permadeath.state();
        CampaignTimers.addDeathTrainRaw(state, millis);
        applyWeather(server.overworld(), state.deathTrainRemainingMillis);
    }

    /** /permadeath storm remove...: shortens the running storm (it lasts at least 1 more second). */
    public static boolean removeMillis(MinecraftServer server, long millis) {
        ProgressionState state = Permadeath.state();
        if (!CampaignTimers.removeDeathTrainRaw(state, millis)) {
            return false;
        }
        applyWeather(server.overworld(), state.deathTrainRemainingMillis);
        return true;
    }

    /** /permadeath resetstorm and /permadeath reset. */
    public static void reset(MinecraftServer server) {
        ProgressionState state = Permadeath.state();
        boolean uhc = state.deathTrainUhcActive;
        state.deathTrainRemainingMillis = 0L;
        state.deathTrainUhcActive = false;
        state.markChanged();
        if (uhc) {
            // Only undo what the storm did: a server that keeps naturalRegeneration off on its own stays that way.
            setNaturalRegeneration(server, true);
        }
        server.overworld().setWeatherParameters(6000, 0, false, false);
        PermadeathMod.LOGGER.info("[Permadeath] Death Train reset manually");
    }

    public static void resetRuntime() {
        pausedReported = false;
    }

    private static void setNaturalRegeneration(MinecraftServer server, boolean enabled) {
        server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(enabled, server);
    }

    /** Duration of the death message: "8m 20s", "4h 00m", "2h 45m". */
    public static String durationText(long millis) {
        return TimeFormat.compact(millis);
    }
}
