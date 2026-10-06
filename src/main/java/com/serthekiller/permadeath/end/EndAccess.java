package com.serthekiller.permadeath.end;

import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/** EndAccessMixin: players cannot enter The End before D30; the portal pushes them up instead. */
public final class EndAccess {
    private EndAccess() {
    }

    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDimension() == Level.END
                && !DayRules.endOpen(Permadeath.day())) {
            event.setCanceled(true);
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.3, 0.0));
            player.hurtMarked = true;
        }
    }
}
