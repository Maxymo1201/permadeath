package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionClock;
import com.serthekiller.permadeath.core.RealTimeProgressionClock;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.core.rules.DayRules;
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
 * Life Orb (D60): players have 8 real hours from the start of D60 to obtain it; afterwards every survival
 * player without a Life Orb in the inventory loses 16 max HP (3 s grace when it is lost). The countdown can be
 * restarted with {@code /permadeath event lifeorb} (plugin /pdc event lifeorb).
 *
 * <p>The deadline is an absolute timestamp. In REAL30 it is the instant D60 begins + 8 h, so the countdown also
 * runs while the server is stopped; in GAME60 D60 is a world-time event, so the 8 real hours start when the
 * server reaches D60 (as in Fabric).</p>
 */
public final class LifeOrb {
    private static final int GRACE_TICKS = 60;
    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(Component.literal("Cargando..."),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    private static final Map<UUID, Integer> GRACE = new HashMap<>();
    private static Boolean lastActive;

    private LifeOrb() {
    }

    public static void reset() {
        BOSS_BAR.removeAllPlayers();
        GRACE.clear();
        lastActive = null;
    }

    public static boolean isActive() {
        return Permadeath.isRunning() && Permadeath.state().lifeOrbActive;
    }

    public static void tick(MinecraftServer server) {
        if (!Permadeath.isRunning()) {
            return;
        }
        ProgressionState state = Permadeath.state();
        int day = Permadeath.day();
        long now = Permadeath.nowMillis();
        if (day < DayRules.LIFE_ORB_FROM_DAY) {
            if (state.lifeOrbActive || state.lifeOrbDeadlineEpochMillis != -1L) {
                state.lifeOrbActive = false;
                state.lifeOrbDeadlineEpochMillis = -1L;
                state.markChanged();
            }
            BOSS_BAR.removeAllPlayers();
        } else if (!state.lifeOrbActive) {
            if (state.lifeOrbDeadlineEpochMillis == -1L) {
                state.lifeOrbDeadlineEpochMillis = deadlineFor(now);
                state.markChanged();
                PermadeathMod.LOGGER.info("[Permadeath] D60 reached: Life Orb deadline {}", TimeFormat.utc(java.time.Instant.ofEpochMilli(state.lifeOrbDeadlineEpochMillis)));
            }
            long remaining = state.lifeOrbDeadlineEpochMillis - now;
            if (remaining <= 0L) {
                state.lifeOrbActive = true;
                state.lifeOrbDeadlineEpochMillis = -1L;
                state.markChanged();
                BOSS_BAR.removeAllPlayers();
                Texts.broadcast(server, Component.literal("§0[§4!§0] §c¡El tiempo ha terminado! El Orbe de Vida se ha activado globalmente.")
                        .withStyle(ChatFormatting.BOLD));
                PermadeathMod.LOGGER.info("[Permadeath] Life Orb countdown finished: penalty active");
            } else {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    BOSS_BAR.addPlayer(player);
                }
                updateBossBar(remaining, now);
            }
        } else {
            BOSS_BAR.removeAllPlayers();
        }
        applyPenalties(server, state.lifeOrbActive);
    }

    private static long deadlineFor(long now) {
        ProgressionClock clock = Permadeath.clock();
        if (clock instanceof RealTimeProgressionClock realTime) {
            long d60Start = realTime.dayInstant(DayRules.LIFE_ORB_FROM_DAY).toEpochMilli();
            return Math.min(d60Start, now) + DayRules.LIFE_ORB_COUNTDOWN_MILLIS;
        }
        return now + DayRules.LIFE_ORB_COUNTDOWN_MILLIS;
    }

    private static void updateBossBar(long remaining, long now) {
        BOSS_BAR.setName(Component.literal(TimeFormat.hms(remaining) + " para obtener Life Orb").withStyle(ChatFormatting.GOLD));
        BOSS_BAR.setProgress(1.0F);
        BossEvent.BossBarColor color = switch ((int) (now / 500L % 5L)) {
            case 0 -> BossEvent.BossBarColor.WHITE;
            case 1 -> BossEvent.BossBarColor.GREEN;
            case 2 -> BossEvent.BossBarColor.PINK;
            case 3 -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.RED;
        };
        BOSS_BAR.setColor(color);
    }

    private static void applyPenalties(MinecraftServer server, boolean active) {
        boolean becameActive = active && Boolean.FALSE.equals(lastActive);
        lastActive = active;
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
            int left = becameActive ? GRACE_TICKS : GRACE.getOrDefault(player.getUUID(), GRACE_TICKS);
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
     * Restarts the 8 h countdown on D60 (the penalty is lifted while it runs).
     * @return false when it is already running
     */
    public static boolean restartCountdown() {
        ProgressionState state = Permadeath.state();
        if (!state.lifeOrbActive && state.lifeOrbDeadlineEpochMillis > 0L) {
            return false;
        }
        state.lifeOrbActive = false;
        state.lifeOrbDeadlineEpochMillis = Permadeath.nowMillis() + DayRules.LIFE_ORB_COUNTDOWN_MILLIS;
        state.markChanged();
        lastActive = false;
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
}
