package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.RandomEffects;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fase 2 (D10-19): the night is skipped only when 4 players sleep; spiders spawn with 1-3 random effects.
 * The doubled hostile mob cap from D10 is applied by {@link com.serthekiller.permadeath.mechanics.MobCapController}.
 */
public final class Day10to19Handler implements PhaseHandler {
    static final int MINIMUM_PLAYERS_TO_SLEEP = 4;
    private final Set<UUID> sleeping = new HashSet<>();
    private int sleepCheckCooldown;

    @Override
    public String name() {
        return "Fase 2: Días 10-19";
    }

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        Texts.broadcast(overworld.getServer(), Component.literal("§e=== Fase 2: Días 10-19 ===\n")
                .append(Component.literal("§7- Sueño: Mínimo 4 jugadores\n")));
    }

    @Override
    public void onPhaseEnd(ServerLevel overworld) {
        resetState();
    }

    @Override
    public void resetState() {
        sleeping.clear();
        sleepCheckCooldown = 0;
    }

    @Override
    public void onEntityJoin(Entity entity, ServerLevel level, boolean loadedFromDisk) {
        applySpiderEffects(entity, level);
    }

    /**
     * Fabric re-applied effects every tick while a spider lacked Speed, Strength and Jump Boost, so effects
     * accumulated over time. The plugin rolled once at spawn; the port does that (persistent marker).
     */
    static void applySpiderEffects(Entity entity, ServerLevel level) {
        if (entity instanceof Spider spider && MobTracking.tryClaim(spider, "spider_effects")) {
            int day = Permadeath.day();
            RandomEffects.apply(spider, day, RandomEffects.spiderEffectCount(day, level.random), level.random);
        }
    }

    @Override
    public void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        handleSleepSystem(level);
        if (sleepCheckCooldown > 0) {
            sleepCheckCooldown--;
        }
    }

    private void handleSleepSystem(ServerLevel level) {
        if (!PhaseCommon.isNight(level)) {
            // Plugin: only at night. Vanilla lets players sleep in a daytime thunderstorm, which skipped the day
            // without the four players.
            for (ServerPlayer player : level.players()) {
                if (player.isSleeping()) {
                    player.displayClientMessage(Component.literal("Solo puedes dormir de noche.").withStyle(ChatFormatting.RED), false);
                    player.stopSleeping();
                }
            }
            sleeping.clear();
            return;
        }
        if (level.isThundering()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSleeping()) {
                    player.displayClientMessage(Component.literal("⚠ No puedes dormir durante una tormenta").withStyle(ChatFormatting.RED), false);
                    player.stopSleeping();
                    sleeping.remove(player.getUUID());
                }
            }
            return;
        }
        int totalPlayers = level.getServer().getPlayerCount();
        int sleepingCount = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping()) {
                sleeping.add(player.getUUID());
                sleepingCount++;
            } else {
                sleeping.remove(player.getUUID());
            }
        }
        if (sleepingCount > 0 && sleepingCount < MINIMUM_PLAYERS_TO_SLEEP) {
            Component missing = Component.literal("Se necesitan " + (MINIMUM_PLAYERS_TO_SLEEP - sleepingCount) + " jugadores más durmiendo")
                    .withStyle(ChatFormatting.GRAY);
            for (ServerPlayer player : level.players()) {
                if (player.isSleeping()) {
                    player.displayClientMessage(missing, true);
                }
            }
        }
        if (sleepingCount > 0 && sleepCheckCooldown == 0) {
            if (totalPlayers < MINIMUM_PLAYERS_TO_SLEEP) {
                for (ServerPlayer player : level.players()) {
                    if (player.isSleeping()) {
                        player.displayClientMessage(Component.literal("⚠ No hay suficientes jugadores para dormir").withStyle(ChatFormatting.RED), false);
                        player.stopSleeping();
                        sleeping.remove(player.getUUID());
                    }
                }
                sleepCheckCooldown = 40;
            } else if (sleepingCount >= MINIMUM_PLAYERS_TO_SLEEP) {
                PhaseCommon.skipToNextMorning(level);
                for (ServerPlayer player : level.players()) {
                    if (player.isSleeping()) {
                        player.stopSleeping();
                    }
                }
                sleeping.clear();
                sleepCheckCooldown = 60;
            }
        }
    }
}
