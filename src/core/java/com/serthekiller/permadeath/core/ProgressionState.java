package com.serthekiller.permadeath.core;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Persisted global state of a Permadeath world. This class is a plain data holder; the NeoForge
 * {@code SavedData} wrapper serialises it to {@code data/permadeath_progression.dat} and marks itself
 * dirty whenever {@link #markChanged()} is called.
 */
public final class ProgressionState {
    public static final int CURRENT_FORMAT_VERSION = 1;

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

    // ---- global timers (wall clock, UTC epoch millis) -----------------------------------------
    /** End of the current Death Train storm, or 0 when none is running. */
    public long deathTrainEndEpochMillis;
    /** Natural regeneration was disabled by a D50+ Death Train ("modo UHC") and must be restored. */
    public boolean deathTrainUhcActive;

    /** D60 Life Orb countdown deadline, or -1 when the countdown has not started. */
    public long lifeOrbDeadlineEpochMillis = -1L;
    /** The Life Orb requirement is permanently active. */
    public boolean lifeOrbActive;

    /** D60 periodic Wither: remaining real presence time (ms) per player. */
    public final Map<UUID, Long> witherRemainingMillis = new HashMap<>();

    /** "Cambio de Mikecrack" toggle (creepers ignore light rules), D60. */
    public boolean mikecrackEnabled;

    /** Hyper Golden Apple + consumed per player. */
    public final Map<UUID, Integer> hyperApplesConsumed = new HashMap<>();

    /** Legacy Fabric .txt/.json state already imported. */
    public boolean legacyMigrated;

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
