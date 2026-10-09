package com.serthekiller.permadeath.beginning;

import com.serthekiller.permadeath.data.BeginningCurseData;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Fabric TheBeginningEventHandler + StormExpulsionHandler.
 * <ul>
 *     <li>Entering The Beginning (no storm): moved to the arrival platform at (1.5, 238, 1.5); the first
 *     player ever to enter gets the "bendición del comienzo" (Resistance II, 12 h of game time).</li>
 *     <li>Leaving The Beginning for the Overworld: moved to the player's bed/respawn point or world spawn.</li>
 *     <li>While a Death Train storm lasts, every player in The Beginning is expelled to the Overworld (checked
 *     every second, so logging in or being teleported there does not bypass it, as in the plugin) and, on D50+,
 *     the closure is announced when the storm starts (plugin BeginningManager#closeBeginning).</li>
 * </ul>
 */
public final class BeginningEvents {
    private static boolean wasStormActive;

    private BeginningEvents() {
    }

    public static void reset() {
        wasStormActive = false;
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (event.getTo() == BeginningDimension.LEVEL_KEY) {
            ServerLevel beginning = BeginningDimension.level(server);
            if (beginning == null) {
                return;
            }
            if (!DeathTrain.isActive()) {
                BeginningPortal.teleport(player, beginning, BeginningPortal.ensurePortalAndGetSpawn(beginning));
                player.sendSystemMessage(Component.literal("§eBienvenido a The Beginning."));
            }
            if (!player.isCreative() && !player.isSpectator()) {
                grantFirstEntryBlessing(player);
            }
        } else if (event.getTo() == Level.OVERWORLD && event.getFrom() == BeginningDimension.LEVEL_KEY) {
            ServerLevel overworld = server.overworld();
            BeginningPortal.teleport(player, overworld, BeginningPortal.getPlayerSpawnInOverworld(player, overworld));
        }
    }

    private static void grantFirstEntryBlessing(ServerPlayer player) {
        BeginningCurseData data = BeginningCurseData.get(player.server);
        if (data.isFirstEntryClaimed()) {
            return;
        }
        data.markFirstEntryClaimed();
        grantBlessing(player);
    }

    /**
     * Resistance II for {@code PermadeathTimings#beginningBlessingMillis} of the player's own play time (GAME60 10 min,
     * REAL30 6 h; kept on time by {@link BeginningEffects}) and the announcement (also /permadeath bendicion, plugin
     * /pdc beginning bendicion).
     */
    public static void grantBlessing(ServerPlayer player) {
        long millis = Permadeath.timings().beginningBlessingMillis();
        BeginningCurseData.get(player.server).bless(player.getUUID(), millis);
        BeginningEffects.applyBlessing(player, millis);
        Texts.broadcast(player.server, "§c[PERMADEATH] §d" + player.getGameProfile().getName()
                + ". Enhorabuena, has recibido la bendición del comienzo por entrar primero a The Beginning. Suerte.");
    }

    /** End of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        boolean active = DeathTrain.isActive();
        if (active && !wasStormActive && Permadeath.day() >= 50) {
            Texts.broadcast(server, "§cPermadeath §7➤ §eThe Beginning ha cerrado temporalmente (DeathTrain).");
        }
        if (active && (!wasStormActive || server.getTickCount() % 20 == 0)) {
            expelPlayers(server);
        }
        wasStormActive = active;
    }

    private static void expelPlayers(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.level().dimension() == BeginningDimension.LEVEL_KEY && !player.isSpectator()) {
                BeginningPortal.teleport(player, overworld, BeginningPortal.getPlayerSpawnInOverworld(player, overworld));
                player.displayClientMessage(Component.literal("§c§lLa tormenta te expulsó de The Beginning."), true);
                player.playNotifySound(SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }
}
