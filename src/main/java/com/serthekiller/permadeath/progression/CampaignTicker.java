package com.serthekiller.permadeath.progression;

import com.serthekiller.permadeath.beginning.BeginningEffects;
import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.time.CampaignTimers;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.mechanics.FinalChallengeManager;
import com.serthekiller.permadeath.mechanics.LifeOrb;
import com.serthekiller.permadeath.mechanics.Participants;
import com.serthekiller.permadeath.mechanics.ShulkerShellEvent;
import com.serthekiller.permadeath.mechanics.WitherSpawner;
import net.minecraft.server.MinecraftServer;

/**
 * One server tick of every progression timer, in a fixed order, with ONE real-time step from the shared
 * {@code EventClock}:
 * <ol>
 *     <li>final challenge start / reset / participants;</li>
 *     <li>global active timers (Death Train, X2 Shulker Shells, Life Orb countdown, final timer), paused when no eligible
 *     survivor is online;</li>
 *     <li>reactions to their transitions (storm end, event end, Life Orb deadline, final evaluation);</li>
 *     <li>individual timers: periodic Wither per player in the Overworld, The Beginning curse/blessing per player.</li>
 * </ol>
 */
public final class CampaignTicker {
    private CampaignTicker() {
    }

    public static void tick(MinecraftServer server) {
        run(server, Permadeath.eventClock().step());
    }

    /** Runs one tick with an explicit real-time step (also used by the GameTests to simulate long periods). */
    public static CampaignTimers.Step run(MinecraftServer server, long stepMillis) {
        if (!Permadeath.isRunning()) {
            return CampaignTimers.Step.NONE;
        }
        ProgressionState state = Permadeath.state();
        boolean eligible = Participants.anyEligible(server);
        FinalChallengeManager.preTick(server, eligible);
        boolean finalOnCalendar = PermadeathConfig.strictCampaign() && state.finalPhaseState == FinalPhaseState.ACTIVE;
        CampaignTimers.Step step = CampaignTimers.advance(state, stepMillis, eligible, finalOnCalendar);
        DeathTrain.tick(server, step.deathTrainEnded(), eligible);
        ShulkerShellEvent.tick(server, step.shulkerEventEnded());
        LifeOrb.tick(server, step.lifeOrbExpired());
        FinalChallengeManager.postTick(server, step, eligible);
        WitherSpawner.tick(server, stepMillis);
        BeginningEffects.tick(server, stepMillis, server.getTickCount() % 20 == 0);
        return step;
    }
}
