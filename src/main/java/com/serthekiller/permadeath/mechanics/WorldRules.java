package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

/**
 * Global rules that depend only on the day.
 * <ul>
 *     <li>Drowning: from D50 the air lost under water is the vanilla amount x5 (D50-59) or x10 (D60). Vanilla
 *     decides the base amount, so Respiration (which skips ticks) and Water Breathing / Conduit Power (no
 *     consumption at all) keep working. Fabric subtracted a fixed extra 5/19 air per tick.</li>
 *     <li>Death Train rain: players in the Overworld under rain with open sky get Blindness for 60 s with
 *     probability 1/10000 per tick on D40-49 and 1/5000 from D50 (Fabric used 1/7500 on D50-59, nothing on
 *     D60 and 1/5000 only after D60).</li>
 *     <li>PvP is allowed only from D40 (Fabric PvpChanges).</li>
 *     <li>Command block output is disabled once per server start (Fabric ServerSetup).</li>
 * </ul>
 */
public final class WorldRules {
    private static boolean rulesConfigured;

    private WorldRules() {
    }

    public static void reset() {
        rulesConfigured = false;
    }

    public static void onBreathe(LivingBreatheEvent event) {
        if (event.canBreathe() || event.getEntity().level().isClientSide() || !Permadeath.isRunning()) {
            return;
        }
        int scaled = DayRules.scaledAirConsumption(event.getConsumeAirAmount(), Permadeath.day());
        if (scaled != event.getConsumeAirAmount()) {
            event.setConsumeAirAmount(scaled);
        }
    }

    /** End of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        if (!rulesConfigured) {
            server.getGameRules().getRule(GameRules.RULE_COMMANDBLOCKOUTPUT).set(false, server);
            rulesConfigured = true;
        }
        int day = Permadeath.day();
        boolean pvp = DayRules.pvpEnabled(day);
        if (server.isPvpAllowed() != pvp) {
            server.setPvpAllowed(pvp);
        }
        rainBlindness(server.overworld(), day);
    }

    private static void rainBlindness(ServerLevel overworld, int day) {
        int oneIn = DayRules.rainBlindnessOneIn(day);
        if (oneIn <= 0 || !DeathTrain.isActive()) {
            return;
        }
        for (ServerPlayer player : overworld.players()) {
            BlockPos pos = player.blockPosition();
            if (overworld.isRainingAt(pos) && overworld.canSeeSky(pos) && overworld.getRandom().nextInt(oneIn) == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 1200, 0, false, true));
            }
        }
    }
}
