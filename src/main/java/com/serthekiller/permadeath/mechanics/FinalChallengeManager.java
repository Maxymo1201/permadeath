package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.FinalParticipant;
import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.core.time.CampaignTimers;
import com.serthekiller.permadeath.core.time.FinalChallenge;
import com.serthekiller.permadeath.core.time.PermadeathTimings;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.progression.PermadeathConfig;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game side of the D60 final challenge ({@link FinalChallenge}). Reaching D60 activates the D60 rules; the challenge
 * starts as soon as an eligible survivor is online on D60 and then lasts {@code PermadeathTimings#finalPhaseMillis}
 * of active time (GAME60 30 min, REAL30 6 h; REAL30 strict campaign: until hour 720 of the calendar).
 *
 * <p>T = 0: announcement, Life Orb countdown and Wither counters start. The Life Orb deadline and the periodic Withers
 * run on their own timers inside the challenge. At the end every participant is evaluated once (VICTORY: alive, Life
 * Orb obtained before the deadline and still held), the result is saved and announced, and the world is left as it
 * is. A setday below D60 (or /permadeath reset) makes the challenge playable again.</p>
 */
public final class FinalChallengeManager {
    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(Component.literal("Desafío final"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

    private FinalChallengeManager() {
    }

    public static void reset() {
        BOSS_BAR.removeAllPlayers();
    }

    public static FinalPhaseState state() {
        return Permadeath.isRunning() ? Permadeath.state().finalPhaseState : FinalPhaseState.NOT_STARTED;
    }

    public static long remainingMillis() {
        return state() == FinalPhaseState.ACTIVE ? Math.max(0L, Permadeath.state().finalPhaseRemainingMillis) : 0L;
    }

    /** Before the timers advance: start, reset after a rollback, calendar sync and participant bookkeeping. */
    public static void preTick(MinecraftServer server, boolean anyEligible) {
        ProgressionState s = Permadeath.state();
        int day = Permadeath.day();
        if (day < PermadeathCalendar.FINAL_DAY) {
            if (s.finalPhaseState != FinalPhaseState.NOT_STARTED || !s.finalParticipants.isEmpty()) {
                FinalChallenge.reset(s);
                PermadeathMod.LOGGER.info("[Permadeath] Day set back below D60: final challenge reset");
            }
            BOSS_BAR.removeAllPlayers();
            return;
        }
        if (FinalChallenge.canStart(s, day, anyEligible)) {
            start(server, s);
        }
        if (s.finalPhaseState == FinalPhaseState.ACTIVE) {
            if (PermadeathConfig.strictCampaign()) {
                FinalChallenge.syncToCalendar(s, Permadeath.timings(), Permadeath.nowMillis());
            }
            // Every tick, so a Life Orb obtained in the last second before the deadline counts.
            observeAll(server, s);
        }
    }

    /** After the timers advanced: end of the challenge and boss bar. */
    public static void postTick(MinecraftServer server, CampaignTimers.Step step, boolean anyEligible) {
        ProgressionState s = Permadeath.state();
        if (step.finalPhaseEnded() && s.finalPhaseState == FinalPhaseState.ACTIVE) {
            observeAll(server, s);
            FinalPhaseState result = FinalChallenge.finish(s, Permadeath.nowMillis());
            announceEnd(server, s, result);
        }
        if (s.finalPhaseState != FinalPhaseState.ACTIVE) {
            BOSS_BAR.removeAllPlayers();
            return;
        }
        if (server.getTickCount() % 10 == 0) {
            long remaining = s.finalPhaseRemainingMillis;
            BOSS_BAR.setName(Component.literal("Desafío final: " + TimeFormat.compact(remaining)
                    + (anyEligible ? "" : " (en pausa)")).withStyle(ChatFormatting.YELLOW));
            BOSS_BAR.setProgress(Math.min(1.0F, Math.max(0.0F, (float) remaining / Permadeath.timings().finalPhaseMillis())));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BOSS_BAR.addPlayer(player);
            }
        }
    }

    /** A player died permanently: eliminated from the challenge; the challenge fails when nobody is left. */
    public static void onPermadeath(ServerPlayer player) {
        if (!Permadeath.isRunning()) {
            return;
        }
        ProgressionState s = Permadeath.state();
        if (s.finalPhaseState != FinalPhaseState.ACTIVE) {
            return;
        }
        if (FinalChallenge.markEliminated(s, player.getUUID())) {
            FinalPhaseState result = FinalChallenge.fail(s, Permadeath.nowMillis());
            announceEnd(player.server, s, result);
        }
    }

    public static void removePlayer(ServerPlayer player) {
        BOSS_BAR.removePlayer(player);
    }

    private static void start(MinecraftServer server, ProgressionState s) {
        PermadeathTimings timings = Permadeath.timings();
        FinalChallenge.start(s, timings, Permadeath.nowMillis());
        if (PermadeathConfig.strictCampaign()) {
            FinalChallenge.syncToCalendar(s, timings, Permadeath.nowMillis());
        }
        observeAll(server, s);
        String duration = TimeFormat.compact(s.finalPhaseRemainingMillis);
        String orb = CampaignTimers.lifeOrbCountdownRunning(s) ? TimeFormat.compact(s.lifeOrbRemainingMillis) : "-";
        sendTitles(server, Component.literal("¡Desafío final!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                Component.literal("Sobrevive " + duration + " y consigue la Life Orb").withStyle(ChatFormatting.GOLD));
        Texts.broadcast(server, Component.literal("§4§l[PERMADEATH] §cHa comenzado el desafío final del día 60. §7Sobrevive §f" + duration
                + "§7, consigue la §6Life Orb §7antes de §f" + orb + " §7y consérvala hasta el final. Cada §f"
                + TimeFormat.compact(timings.witherIntervalMillis()) + " §7en el Overworld aparecerá un §8Wither§7."));
        PermadeathMod.LOGGER.info("[Permadeath] D60 final challenge started ({}): {} left, Life Orb countdown {}", Permadeath.mode(), duration, orb);
    }

    private static void observeAll(MinecraftServer server, ProgressionState s) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FinalChallenge.observe(s, player.getUUID(), player.getGameProfile().getName(), Participants.isEligible(player), LifeOrb.hasLifeOrb(player));
        }
    }

    private static void announceEnd(MinecraftServer server, ProgressionState s, FinalPhaseState result) {
        List<String> winners = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<UUID, FinalParticipant> e : s.finalParticipants.entrySet()) {
            FinalParticipant p = e.getValue();
            boolean win = p.result == FinalParticipant.Result.VICTORY;
            if (win) {
                winners.add(p.name);
            }
            summary.append("\n§7- §f").append(p.name).append(win ? " §a✔ VICTORIA" : " §c✘ DERROTA")
                    .append(" §8(").append(p.eliminated ? "eliminado" : p.survivedLastSeen ? "sobrevivió" : "no superviviente")
                    .append(", Life Orb a tiempo: ").append(p.lifeOrbBeforeDeadline ? "sí" : "no")
                    .append(", la conserva: ").append(p.holdingLifeOrb ? "sí" : "no").append(')');
            ServerPlayer online = server.getPlayerList().getPlayer(e.getKey());
            if (online != null) {
                sendTitle(online, win ? Component.literal("¡VICTORIA!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                : Component.literal("DERROTA").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.literal(win ? "Has completado Permadeath" : "El desafío final ha terminado").withStyle(ChatFormatting.GRAY));
            }
        }
        String header = result == FinalPhaseState.COMPLETED
                ? "§6§l[PERMADEATH] §a¡Desafío final completado! §7Vencedores: §f" + String.join(", ", winners)
                : "§4§l[PERMADEATH] §cEl desafío final ha terminado sin vencedores.";
        Texts.broadcast(server, header + summary + (PermadeathConfig.freezeAfterCampaign()
                ? "\n§7Los temporizadores de la campaña se han detenido; el mundo se conserva."
                : "\n§7Las mecánicas del día 60 siguen activas (supervivencia libre)."));
        PermadeathMod.LOGGER.info("[Permadeath] D60 final challenge ended: {} (winners: {})", result, winners);
    }

    private static void sendTitles(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTitle(player, title, subtitle);
        }
    }

    private static void sendTitle(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
}
