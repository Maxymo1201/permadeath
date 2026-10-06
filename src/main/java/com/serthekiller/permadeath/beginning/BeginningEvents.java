package com.serthekiller.permadeath.beginning;

import com.serthekiller.permadeath.data.BeginningCurseData;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Fabric TheBeginningEventHandler + StormExpulsionHandler.
 * <ul>
 *     <li>Entering The Beginning (no storm): moved to the arrival platform at (1.5, 238, 1.5); the first
 *     player ever to enter gets the "bendición del comienzo" (Resistance II, 12 h of game time).</li>
 *     <li>Leaving The Beginning for the Overworld: moved to the player's bed/respawn point or world spawn.</li>
 *     <li>When a Death Train storm starts, every player in The Beginning is expelled to the Overworld.</li>
 * </ul>
 */
public final class BeginningEvents {
    private static final int BLESSING_DURATION_TICKS = 864000;
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
            }
            grantFirstEntryBlessing(player);
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
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BLESSING_DURATION_TICKS, 1, false, true, true));
        Texts.broadcast(player.server, "§c[PERMADEATH] §d" + player.getGameProfile().getName()
                + ". Enhorabuena, has recibido la bendición del comienzo por entrar primero a The Beginning. Suerte.");
    }

    /** End of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        boolean active = DeathTrain.isActive();
        if (active && !wasStormActive) {
            expelPlayers(server);
        }
        wasStormActive = active;
    }

    private static void expelPlayers(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.level().dimension() == BeginningDimension.LEVEL_KEY) {
                BeginningPortal.teleport(player, overworld, BeginningPortal.getPlayerSpawnInOverworld(player, overworld));
                player.displayClientMessage(Component.literal("§c§lLa tormenta te expulsó de The Beginning."), true);
            }
        }
    }
}
