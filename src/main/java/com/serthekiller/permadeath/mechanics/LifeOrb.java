package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.core.time.CampaignTimers;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Life Orb (D60). When the D60 final challenge starts, players get {@code PermadeathTimings#lifeOrbCountdownMillis}
 * (GAME60 20 min, REAL30 4 h of active time) to obtain it; afterwards every survival player without a Life Orb in the
 * inventory loses 16 max HP. The countdown is remaining active time ({@link CampaignTimers}): it does not run while
 * the server is stopped or no eligible survivor is online, so a REAL30 server that reaches D60 at night while stopped
 * still gets the whole countdown when it starts again. {@code /permadeath event lifeorb} restarts it (plugin
 * /pdc event lifeorb).
 *
 * <p>Penalty: one independent, persistent modifier ({@link PlayerHealth#LIFE_ORB_PENALTY}) applied idempotently (it
 * cannot stack with the D40/D60 penalties or with itself). Players that connect after the deadline keep the original
 * consequence, but only after a short sync grace ({@value #GRACE_TICKS} ticks) so the penalty is never applied before
 * their inventory is loaded; the grace does not give them a new countdown.</p>
 */
public final class LifeOrb {
    public static final int GRACE_TICKS = 60;
    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(Component.literal("Cargando..."),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    private static final Map<UUID, Integer> GRACE = new HashMap<>();

    private LifeOrb() {
    }

    public static void reset() {
        BOSS_BAR.removeAllPlayers();
        GRACE.clear();
    }

    public static boolean isActive() {
        return Permadeath.isRunning() && Permadeath.state().lifeOrbActive;
    }

    public static boolean countdownRunning() {
        return Permadeath.isRunning() && CampaignTimers.lifeOrbCountdownRunning(Permadeath.state());
    }

    public static long remainingMillis() {
        return countdownRunning() ? Math.max(0L, Permadeath.state().lifeOrbRemainingMillis) : 0L;
    }

    /**
     * Boss bar, deadline announcement and penalties of one server tick.
     *
     * @param expired the countdown ran out during this tick ({@link CampaignTimers.Step#lifeOrbExpired()})
     */
    public static void tick(MinecraftServer server, boolean expired) {
        if (!Permadeath.isRunning()) {
            return;
        }
        ProgressionState state = Permadeath.state();
        if (Permadeath.day() < DayRules.LIFE_ORB_FROM_DAY) {
            CampaignTimers.clearLifeOrb(state);
            BOSS_BAR.removeAllPlayers();
        } else if (expired) {
            BOSS_BAR.removeAllPlayers();
            GRACE.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                GRACE.put(player.getUUID(), GRACE_TICKS);
            }
            Texts.broadcast(server, Component.literal("§0[§4!§0] §c¡El tiempo ha terminado! El Orbe de Vida se ha activado globalmente.")
                    .withStyle(ChatFormatting.BOLD));
            PermadeathMod.LOGGER.info("[Permadeath] Life Orb countdown finished: penalty active");
        } else if (CampaignTimers.lifeOrbCountdownRunning(state)) {
            if (server.getTickCount() % 10 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    BOSS_BAR.addPlayer(player);
                }
                updateBossBar(state.lifeOrbRemainingMillis, server.getTickCount());
            }
        } else {
            BOSS_BAR.removeAllPlayers();
        }
        applyPenalties(server, state.lifeOrbActive);
    }

    private static void updateBossBar(long remaining, int tick) {
        BOSS_BAR.setName(Component.literal(TimeFormat.compact(remaining) + " para obtener Life Orb").withStyle(ChatFormatting.GOLD));
        long countdown = Math.max(remaining, Permadeath.timings().lifeOrbCountdownMillis());
        BOSS_BAR.setProgress(Math.min(1.0F, (float) remaining / countdown));
        BossEvent.BossBarColor color = switch (tick / 10 % 5) {
            case 0 -> BossEvent.BossBarColor.WHITE;
            case 1 -> BossEvent.BossBarColor.GREEN;
            case 2 -> BossEvent.BossBarColor.PINK;
            case 3 -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.RED;
        };
        BOSS_BAR.setColor(color);
    }

    private static void applyPenalties(MinecraftServer server, boolean active) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
            if (health == null) {
                continue;
            }
            if (!active || player.isCreative() || player.isSpectator()) {
                GRACE.remove(player.getUUID());
                if (PlayerHealth.sync(health, PlayerHealth.LIFE_ORB_PENALTY, 0.0, false) && player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
                continue;
            }
            if (hasLifeOrb(player)) {
                GRACE.remove(player.getUUID());
                PlayerHealth.sync(health, PlayerHealth.LIFE_ORB_PENALTY, 0.0, false);
                continue;
            }
            if (health.getModifier(PlayerHealth.LIFE_ORB_PENALTY) != null) {
                GRACE.remove(player.getUUID());
                continue;
            }
            int left = GRACE.getOrDefault(player.getUUID(), GRACE_TICKS);
            if (left > 0) {
                GRACE.put(player.getUUID(), left - 1);
                continue;
            }
            GRACE.remove(player.getUUID());
            // Only the maximum drops by 16 (plugin setupHealth). The modifier is saved with the player, so a relog
            // or a restart does not apply it again; Fabric used a transient modifier and also took 16 current HP,
            // which killed the player on the next login.
            PlayerHealth.sync(health, PlayerHealth.LIFE_ORB_PENALTY, -DayRules.LIFE_ORB_PENALTY_HP, true);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    /**
     * Restarts the countdown on D60 (the penalty is lifted while it runs).
     * @return false when it is already running
     */
    public static boolean restartCountdown() {
        ProgressionState state = Permadeath.state();
        if (CampaignTimers.lifeOrbCountdownRunning(state)) {
            return false;
        }
        CampaignTimers.startLifeOrbCountdown(state, Permadeath.timings());
        return true;
    }

    public static boolean hasLifeOrb(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.LIFE_ORB.get())) {
                return true;
            }
        }
        return false;
    }

    public static void onLogout(ServerPlayer player) {
        GRACE.remove(player.getUUID());
        BOSS_BAR.removePlayer(player);
    }

    /** The old object of a respawned player leaves the boss bar (its connection closes when a dead player is banned). */
    public static void onRespawn(ServerPlayer original) {
        BOSS_BAR.removePlayer(original);
    }
}
