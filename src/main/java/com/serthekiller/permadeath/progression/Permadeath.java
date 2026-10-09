package com.serthekiller.permadeath.progression;

import com.serthekiller.permadeath.BuildProfile;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.GameDayProgressionClock;
import com.serthekiller.permadeath.core.ProgressionClock;
import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.RealTimeProgressionClock;
import com.serthekiller.permadeath.core.time.EventClock;
import com.serthekiller.permadeath.core.time.PermadeathTimings;
import com.serthekiller.permadeath.core.time.TimerMigration;
import com.serthekiller.permadeath.data.LegacyMigration;
import com.serthekiller.permadeath.data.PermadeathData;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.List;

/**
 * Runtime facade: the single place where mechanics ask "which Permadeath day is it?". It owns the
 * {@link ProgressionClock} of the running server (GAME60 or REAL30, fixed by the jar) and the persisted
 * {@link PermadeathData}. Everything is accessed from the server thread only.
 */
public final class Permadeath {
    @Nullable
    private static MinecraftServer server;
    @Nullable
    private static PermadeathData data;
    @Nullable
    private static volatile ProgressionClock clock;
    private static final EventClock EVENT_CLOCK = EventClock.system();
    /** Time source of REAL30 (replaceable by GameTests / debug only). */
    private static Clock wallClock = Clock.systemUTC();

    private Permadeath() {
    }

    public static void start(MinecraftServer s) {
        server = s;
        ProgressionMode mode = BuildProfile.mode();
        data = PermadeathData.get(s);
        ProgressionState state = data.state();
        LegacyMigration.migrateIfNeeded(s, state, mode);
        if (state.formatVersion < ProgressionState.CURRENT_FORMAT_VERSION) {
            int from = state.formatVersion;
            List<String> converted = TimerMigration.migrate(state, timings(), wallClock.millis());
            PermadeathMod.LOGGER.info("[Permadeath] Progression data migrated from format {} to {} ({} profile): {}", from,
                    ProgressionState.CURRENT_FORMAT_VERSION, mode, converted.isEmpty() ? "no running timer" : String.join("; ", converted));
        }
        if (mode == ProgressionMode.GAME60) {
            GameDayProgressionClock c = new GameDayProgressionClock(state, () -> s.overworld().getDayTime(),
                    msg -> PermadeathMod.LOGGER.warn("[Permadeath] {}", msg));
            c.initialize();
            clock = c;
        } else {
            RealTimeProgressionClock c = new RealTimeProgressionClock(state, wallClock,
                    msg -> PermadeathMod.LOGGER.warn("[Permadeath] {}", msg), PermadeathConfig::strictCampaign);
            c.initialize();
            clock = c;
        }
        PermadeathMod.LOGGER.info("[Permadeath] Calendar {} started: PD day {} (phase {})", mode, clock.getDay(), clock.getPhase());
    }

    public static void stop() {
        server = null;
        data = null;
        clock = null;
        EVENT_CLOCK.reset();
    }

    /** Progression timers of this build (GAME60 or REAL30). */
    public static PermadeathTimings timings() {
        return PermadeathTimings.forMode(BuildProfile.mode());
    }

    /** Real time since the previous server tick (monotonic, capped), shared by every active-time timer. */
    public static EventClock eventClock() {
        return EVENT_CLOCK;
    }

    public static boolean isRunning() {
        return server != null && clock != null;
    }

    /** Effective Permadeath day 0..60; 0 when no server is running (e.g. client side). */
    public static int day() {
        ProgressionClock c = clock;
        return c == null ? 0 : c.getDay();
    }

    public static ProgressionMode mode() {
        return BuildProfile.mode();
    }

    public static ProgressionClock clock() {
        if (clock == null) {
            throw new IllegalStateException("Permadeath calendar not running");
        }
        return clock;
    }

    public static PermadeathData data() {
        if (data == null) {
            throw new IllegalStateException("Permadeath data not loaded");
        }
        return data;
    }

    public static ProgressionState state() {
        return data().state();
    }

    public static MinecraftServer server() {
        if (server == null) {
            throw new IllegalStateException("No Permadeath server running");
        }
        return server;
    }

    /** Wall clock used for global real-time timers (Death Train, Life Orb, Wither). */
    public static long nowMillis() {
        return wallClock.millis();
    }
}
