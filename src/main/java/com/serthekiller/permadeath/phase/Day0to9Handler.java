package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fase 1 (D0-9): one sleeping player skips the night ("sueño instantáneo"); sleeping is not allowed during a
 * Death Train in daytime.
 */
public final class Day0to9Handler implements PhaseHandler {
    private final Set<UUID> sleeping = new HashSet<>();
    private int sleepMessageCooldown;

    @Override
    public String name() {
        return "Fase 1: Días 0-9";
    }

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        Texts.broadcast(overworld.getServer(), Component.literal("§a=== Fase 1: Días 0-9 ===\n")
                .append(Component.literal("§7- Sueño instantáneo \n"))
                .append(Component.literal("§7- Deathtrain y mensaje de muerte")));
    }

    @Override
    public void onPhaseEnd(ServerLevel overworld) {
        sleeping.clear();
        sleepMessageCooldown = 0;
    }

    @Override
    public void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        handleInstantSleep(level);
        if (sleepMessageCooldown > 0) {
            sleepMessageCooldown--;
        }
    }

    private void handleInstantSleep(ServerLevel level) {
        boolean night = PhaseCommon.isNight(level);
        boolean storm = DeathTrain.isActive();
        boolean skipped = false;
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (!player.isSleeping()) {
                sleeping.remove(id);
                continue;
            }
            if (!sleeping.add(id)) {
                continue;
            }
            if (!night && storm) {
                player.displayClientMessage(Component.literal("Solo puedes dormir de noche, no en tormenta").withStyle(ChatFormatting.RED), false);
                player.stopSleeping();
                sleeping.remove(id);
            } else if (night) {
                // Several players lying down in the same tick skip one night, not one day each.
                if (!skipped) {
                    PhaseCommon.skipToNextMorning(level);
                    skipped = true;
                }
                if (sleepMessageCooldown == 0) {
                    Texts.broadcast(level.getServer(), Component.empty()
                            .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" ha dormido").withStyle(ChatFormatting.YELLOW)));
                    sleepMessageCooldown = 60;
                }
                level.getServer().execute(() -> {
                    player.stopSleeping();
                    sleeping.remove(id);
                });
            }
        }
    }
}
