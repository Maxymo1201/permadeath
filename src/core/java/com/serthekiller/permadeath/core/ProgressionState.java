package com.serthekiller.permadeath.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Persisted global state of a Permadeath world. This class is a plain data holder; the NeoForge
 * {@code SavedData} wrapper serialises it to {@code data/permadeath_progression.dat} and marks itself
 * dirty whenever {@link #markChanged()} is called.
 */
public final class ProgressionState {
    /** 1 = absolute wall-clock timers; 2 = remaining active time + final challenge. */
    public static final int CURRENT_FORMAT_VERSION = 2;

    public int formatVersion = CURRENT_FORMAT_VERSION;
    /** Calendar that last wrote this state (used to detect a world moved between GAME60/REAL30 jars). */
    public ProgressionMode mode;
    public boolean initialized;

    // ---- GAME60 -------------------------------------------------------------------------------
    /** Overworld day number (dayTime / 24000) that corresponds to PD day 0. */
    public long baseWorldDay;

    // ---- REAL30 -------------------------------------------------------------------------------
    /** UTC epoch millis of PD day 0. */
    public long startEpochMillis;
    /** Largest elapsed wall-clock time ever observed (protects against clock regression). */
    public long maxElapsedMillis;

    // ---- both calendars -----------------------------------------------------------------------
    /** Highest effective PD day ever reached (monotonic except for explicit /permadeath setday). */
    public int maxEffectiveDay;
    /** Milestones whose one-shot side effects have already been executed. */
    public final TreeSet<Integer> executedMilestones = new TreeSet<>();

    // ---- campaign timers (format 2: remaining ACTIVE time in ms, see PermadeathTimings) -----------
    // Active time only runs while the server runs and at least one eligible survivor is online; it is
    // measured with a monotonic clock (EventClock), so lag, TPS and wall-clock jumps cannot change it.
    /** Remaining Death Train storm, 0 when none is running. */
    public long deathTrainRemainingMillis;
    /** Natural regeneration was turned off by a D50+ Death Train ("modo UHC") and must be turned back on. */
    public boolean deathTrainUhcActive;

    /** D60 Life Orb countdown, -1 when it has not started (or is over: see {@link #lifeOrbActive}). */
    public long lifeOrbRemainingMillis = -1L;
    /** The Life Orb requirement is permanently active (the countdown ended). */
    public boolean lifeOrbActive;

    /** D60 periodic Wither: remaining presence time (ms) in the Overworld per player. */
    public final Map<UUID, Long> witherRemainingMillis = new HashMap<>();

    /** Remaining "X2 Shulker Shells" admin event, 0 when it is not running. */
    public long shulkerEventRemainingMillis;

    // ---- D60 final challenge -------------------------------------------------------------------
    public FinalPhaseState finalPhaseState = FinalPhaseState.NOT_STARTED;
    /** Remaining active time of the final challenge (meaningful while ACTIVE). */
    public long finalPhaseRemainingMillis;
    /** UTC epoch of the start / end of the final challenge (information only), 0 = not yet. */
    public long finalPhaseStartedEpochMillis;
    public long finalPhaseEndedEpochMillis;
    /** Every player that took part in the final challenge (was an eligible survivor while it ran). */
    public final Map<UUID, FinalParticipant> finalParticipants = new LinkedHashMap<>();

    // ---- format 1 values kept for the migration (see TimerMigration) ---------------------------
    /** Format 1 absolute timestamps read from an old world; null once migrated. */
    public transient LegacyTimers legacyTimers;
    /** Format of the file this state was loaded from, and when/what the last migration converted. */
    public int migratedFromVersion;
    public long migrationEpochMillis;
    public String migrationSummary = "";

    /** Absolute timestamps (UTC epoch ms) of the format 1 timers. */
    public record LegacyTimers(long deathTrainEndEpochMillis, long lifeOrbDeadlineEpochMillis, long shulkerEventEndEpochMillis) {
    }

    /** "Cambio de Mikecrack" explicitly enabled with /permadeath mikecrack enable (or migrated from Fabric). */
    public boolean mikecrackEnabled;
    /** "Cambio de Mikecrack" explicitly disabled with /permadeath mikecrack disable (it is on by default on D60). */
    public boolean mikecrackDisabled;

    /** Hyper Golden Apple + consumed per player. */
    public final Map<UUID, Integer> hyperApplesConsumed = new HashMap<>();

    /** Legacy Fabric .txt/.json state already imported. */
    public boolean legacyMigrated;

    // ---- End arena (one-shot world edits) ------------------------------------------------------
    /** 35 % of the End island top end stone already turned into end stone bricks. */
    public boolean endArenaPrepared;
    /** Packed positions (BlockPos#asLong) of the four End healing altars. */
    public long[] endAltarPositions = new long[0];

    private transient Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> {
        } : listener;
    }

    public void markChanged() {
        changeListener.run();
    }
}
