package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.data.BeginningCurseData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Cursed players (/permadeath maldicion; GAME60 10 min, REAL30 6 h of their own play time, see BeginningEffects) die
 * if they try to drink milk.
 */
public final class MilkCurse {
    private MilkCurse() {
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getItemStack().is(Items.MILK_BUCKET)) {
            return;
        }
        if (BeginningCurseData.get(player.server).isCursed(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§c[PERMADEATH] §dLa maldición de The Beginning te fulminó por beber leche."));
            player.kill();
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
