package com.serthekiller.permadeath.progression;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.beginning.BeginningPortal;
import com.serthekiller.permadeath.core.MilestoneTracker;
import com.serthekiller.permadeath.core.ProgressionClock;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.mechanics.MobCapController;
import com.serthekiller.permadeath.phase.PhaseManager;
import com.serthekiller.permadeath.registry.DayConditions;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * Server-tick orchestration of the calendar: updates the clock, runs milestone side effects idempotently,
 * keeps the phase handler, the hostile mob cap and the day-conditioned recipes in sync with the day.
 */
public final class DayController {
    private static boolean reloadInFlight;

    private DayController() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (!Permadeath.isRunning()) {
            return;
        }
        ProgressionClock clock = Permadeath.clock();
        ProgressionClock.DayChange change = clock.update();
        if (change.changed()) {
            PermadeathMod.LOGGER.info("[Permadeath] Day changed {} -> {} ({})", change.previousDay(), change.newDay(), clock.mode());
        }
        runPendingMilestones(server);
        MobCapController.apply(Permadeath.day());
        PhaseManager.ensurePhase(server);
        checkRecipeConditions(server);
    }

    /** Executes once every milestone reached so far (handles jumps such as D9 → D31). */
    public static void runPendingMilestones(MinecraftServer server) {
        int day = Permadeath.day();
        List<Integer> pending = MilestoneTracker.pending(Permadeath.state(), day);
        for (int milestone : pending) {
            try {
                executeMilestone(server, milestone);
            } catch (RuntimeException e) {
                PermadeathMod.LOGGER.error("[Permadeath] Milestone D{} failed; it will be retried", milestone, e);
                return;
            }
            MilestoneTracker.markExecuted(Permadeath.state(), milestone);
            PermadeathMod.LOGGER.info("[Permadeath] Milestone D{} executed", milestone);
        }
    }

    private static void executeMilestone(MinecraftServer server, int milestone) {
        if (milestone == 40) {
            // Fabric Day40to49Handler#onPhaseStart → spawnCustomPortal (only once per world, PortalState guard).
            BeginningPortal.spawnOverworldPortal(server);
        }
        // D10 (mob cap), D20/D25/D30 (hostility, drops), D50, D60 (Life Orb countdown, Wither) are state based:
        // their systems read the current day every tick, so no extra one-shot action is required here.
    }

    /** Reloads datapacks when a recipe threshold (D40/D50/D60) differs from the one used at the last load. */
    private static void checkRecipeConditions(MinecraftServer server) {
        if (reloadInFlight) {
            return;
        }
        int wanted = DayRules.recipeBucket(Permadeath.day());
        if (wanted == DayConditions.loadedBucket()) {
            return;
        }
        reloadInFlight = true;
        PermadeathMod.LOGGER.info("[Permadeath] Recipe threshold D{} reached (loaded: D{}), reloading datapacks...",
                wanted, DayConditions.loadedBucket());
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((v, error) -> server.execute(() -> {
            reloadInFlight = false;
            if (error != null) {
                PermadeathMod.LOGGER.error("[Permadeath] Datapack reload failed", error);
                DayConditions.forceLoadedBucket(wanted);
            } else {
                PermadeathMod.LOGGER.info("[Permadeath] Recipes reloaded for D{}", wanted);
                DayConditions.forceLoadedBucket(wanted);
            }
        }));
    }

    public static void reset() {
        reloadInFlight = false;
    }
}
