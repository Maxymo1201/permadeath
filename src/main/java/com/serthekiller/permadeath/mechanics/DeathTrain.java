package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.core.rules.DayRules;
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
 * Death Train (Fabric PermadeathUtils storm). Every death adds {@link DayRules#deathTrainDurationMillis} of
 * thunderstorm. The end of the storm is an absolute wall-clock timestamp stored in the world, so it does not
 * depend on TPS and keeps running while the server is stopped (Fabric counted server ticks and saved them in
 * permadeath_storm.txt).
 *
 * <p>While it lasts: permanent thunderstorm in the Overworld, a timer in the action bar, D50+ no natural
 * regeneration ("modo UHC"). From D25 every death gives every mob alive Strength, Resistance and Speed (I, II
 * from D50) and, on D50-59, Fire Resistance, for the rest of its life, and mobs spawning during the storm get
 * them too (plugin deathTrainEffects, infinite duration; Fabric refreshed them only while the storm lasted).</p>
 */
public final class DeathTrain {
    private static long tickCounter;

    private DeathTrain() {
    }

    public static boolean isActive() {
        if (!Permadeath.isRunning()) {
            return false;
        }
        return Permadeath.state().deathTrainEndEpochMillis > Permadeath.nowMillis();
    }

    public static long remainingMillis() {
        if (!Permadeath.isRunning()) {
            return 0L;
        }
        return Math.max(0L, Permadeath.state().deathTrainEndEpochMillis - Permadeath.nowMillis());
    }

    /** Adds the storm of one death on {@code day}; returns the added duration. */
    public static long trigger(MinecraftServer server, int day) {
        ProgressionState state = Permadeath.state();
        long now = Permadeath.nowMillis();
        long added = DayRules.deathTrainDurationMillis(day);
        long base = Math.max(now, state.deathTrainEndEpochMillis);
        state.deathTrainEndEpochMillis = base + added;
        state.markChanged();
        applyWeather(server.overworld(), state.deathTrainEndEpochMillis - now);
        if (DayRules.deathTrainBuffAmplifier(day) >= 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Mob mob && mob.isAlive()) {
                        applyBuffs(mob, day);
                    }
                }
            }
        }
        PermadeathMod.LOGGER.info("[Permadeath] Death Train +{} (day {}), ends at {}", TimeFormat.realDuration(java.time.Duration.ofMillis(added)), day,
                TimeFormat.utc(java.time.Instant.ofEpochMilli(state.deathTrainEndEpochMillis)));
        return added;
    }

    private static void applyWeather(ServerLevel overworld, long remainingMillis) {
        int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, remainingMillis / 50L));
        overworld.setWeatherParameters(0, ticks, true, true);
    }

    public static void tick(MinecraftServer server) {
        if (!Permadeath.isRunning()) {
            return;
        }
        tickCounter++;
        ProgressionState state = Permadeath.state();
        long end = state.deathTrainEndEpochMillis;
        if (end <= 0L) {
            return;
        }
        long remaining = end - Permadeath.nowMillis();
        int day = Permadeath.day();
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
            if (tickCounter % 20L == 0L) {
                Component timer = Component.literal("Quedan: " + TimeFormat.hms(remaining) + " de tormenta").withStyle(ChatFormatting.GRAY);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.displayClientMessage(timer, true);
                }
            }
            return;
        }
        // Storm finished (possibly while the server was stopped).
        state.deathTrainEndEpochMillis = 0L;
        server.overworld().setWeatherParameters(6000, 0, false, false);
        if (state.deathTrainUhcActive) {
            setNaturalRegeneration(server, true);
            state.deathTrainUhcActive = false;
        }
        state.markChanged();
        Texts.broadcast(server, Component.literal("La Tormenta ha finalizado...").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
        PermadeathMod.LOGGER.info("[Permadeath] Death Train finished");
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

    /** /permadeath storm addHours: extends the running storm or starts one. */
    public static void addMillis(MinecraftServer server, long millis) {
        ProgressionState state = Permadeath.state();
        long now = Permadeath.nowMillis();
        state.deathTrainEndEpochMillis = Math.max(now, state.deathTrainEndEpochMillis) + millis;
        state.markChanged();
        applyWeather(server.overworld(), state.deathTrainEndEpochMillis - now);
    }

    /** /permadeath storm removeHours: shortens the running storm (it lasts at least 1 more second). */
    public static boolean removeMillis(MinecraftServer server, long millis) {
        if (!isActive()) {
            return false;
        }
        ProgressionState state = Permadeath.state();
        long now = Permadeath.nowMillis();
        state.deathTrainEndEpochMillis = Math.max(now + 1000L, state.deathTrainEndEpochMillis - millis);
        state.markChanged();
        applyWeather(server.overworld(), state.deathTrainEndEpochMillis - now);
        return true;
    }

    /** /permadeath resetstorm and /permadeath reset. */
    public static void reset(MinecraftServer server) {
        ProgressionState state = Permadeath.state();
        boolean uhc = state.deathTrainUhcActive;
        state.deathTrainEndEpochMillis = 0L;
        state.deathTrainUhcActive = false;
        state.markChanged();
        if (uhc) {
            // Only undo what the storm did: a server that keeps naturalRegeneration off on its own stays that way.
            setNaturalRegeneration(server, true);
        }
        server.overworld().setWeatherParameters(6000, 0, false, false);
        PermadeathMod.LOGGER.info("[Permadeath] Death Train reset manually");
    }

    private static void setNaturalRegeneration(MinecraftServer server, boolean enabled) {
        server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(enabled, server);
    }

    /** Spanish duration text of the death message ("2 horas", "1h 30 minutos"). */
    public static String durationText(long millis) {
        long totalMinutes = millis / 60_000L;
        if (totalMinutes % 60L == 0L) {
            long hours = totalMinutes / 60L;
            return hours + " hora" + (hours == 1L ? "" : "s");
        }
        long h = totalMinutes / 60L;
        long m = totalMinutes % 60L;
        return (h > 0L ? h + "h " : "") + m + " minutos";
    }
}
