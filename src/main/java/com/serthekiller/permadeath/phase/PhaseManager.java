package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Activates the handler of the current phase. The phase is derived from the calendar (state based): a jump
 * from D9 to D31 activates the D30-39 handler directly. One-shot effects of skipped phases are executed by
 * the milestone system, so the Fabric "sequential phase loading" is not needed any more.
 *
 * <p>D70 is never activated: phase 6 (D60) is final. The Fabric Day70Handler is intentionally not ported.</p>
 */
public final class PhaseManager {
    private static final PhaseHandler[] HANDLERS = {
            new Day0to9Handler(),
            new Day10to19Handler(),
            new Day20to29Handler(),
            new Day30to39Handler(),
            new Day40to49Handler(),
            new Day50to59Handler(),
            new Day60Handler()
    };
    private static int currentPhase = -1;

    private PhaseManager() {
    }

    @Nullable
    public static PhaseHandler current() {
        return currentPhase < 0 ? null : HANDLERS[currentPhase];
    }

    public static int currentPhase() {
        return currentPhase;
    }

    public static String currentName() {
        PhaseHandler h = current();
        return h == null ? "-" : h.name();
    }

    /** Called every server tick after the calendar update. */
    public static void ensurePhase(MinecraftServer server) {
        int target = PermadeathCalendar.phaseForDay(Permadeath.day());
        if (target == currentPhase) {
            return;
        }
        ServerLevel overworld = server.overworld();
        PhaseHandler old = current();
        if (old != null) {
            try {
                old.onPhaseEnd(overworld);
                PermadeathMod.LOGGER.info("[Permadeath] Phase {} ended: {}", currentPhase, old.name());
            } catch (RuntimeException e) {
                PermadeathMod.LOGGER.error("[Permadeath] onPhaseEnd failed for phase {}", currentPhase, e);
            }
        }
        boolean firstActivation = currentPhase < 0;
        currentPhase = target;
        PhaseHandler handler = HANDLERS[target];
        try {
            handler.onPhaseStart(overworld);
            PermadeathMod.LOGGER.info("[Permadeath] Phase {} started: {}", target, handler.name());
        } catch (RuntimeException e) {
            PermadeathMod.LOGGER.error("[Permadeath] onPhaseStart failed for phase {}", target, e);
        }
        if (!firstActivation || server.getPlayerCount() > 0) {
            Texts.broadcast(server, "§6⚠ Nueva fase iniciada: §f" + handler.name());
        }
    }

    /** /permadeath reload: ends the active phase and starts it again on the next tick. */
    public static void reload(MinecraftServer server) {
        PhaseHandler old = current();
        if (old != null) {
            try {
                old.onPhaseEnd(server.overworld());
            } catch (RuntimeException e) {
                PermadeathMod.LOGGER.error("[Permadeath] onPhaseEnd failed for phase {}", currentPhase, e);
            }
        }
        currentPhase = -1;
    }

    public static void reset() {
        currentPhase = -1;
    }
}
