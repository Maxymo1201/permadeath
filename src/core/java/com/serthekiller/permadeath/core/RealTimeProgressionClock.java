package com.serthekiller.permadeath.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * REAL30 calendar: the 60 Permadeath days are compressed into 720 real hours (30 real days).
 *
 * <ul>
 *     <li>virtualDay = floor(effectiveElapsed / 12h), clamped to 0..60.</li>
 *     <li>effectiveElapsed = max(previousMaxElapsed, now - startEpochMillis): a system clock that goes
 *     back never moves Permadeath back.</li>
 *     <li>Based on UTC epoch millis only: time zones, DST or the host's local time cannot change it.</li>
 *     <li>Time keeps running while the server is stopped; TPS/lag have no influence.</li>
 *     <li>The time source is injectable ({@link Clock}) so the 30 days can be tested instantly.</li>
 * </ul>
 */
public final class RealTimeProgressionClock implements ProgressionClock {
    /** Regressions smaller than this are ignored silently (NTP jitter). */
    public static final long REGRESSION_WARN_THRESHOLD_MILLIS = 60_000L;
    /** The persisted maximum is refreshed (SavedData marked dirty) at most once per this amount of progress. */
    private static final long PERSIST_GRANULARITY_MILLIS = 60_000L;

    private final ProgressionState state;
    private final Clock clock;
    private final Consumer<String> warnLog;
    private long lastPersistedElapsed = Long.MIN_VALUE;
    private boolean regressionReported;

    public RealTimeProgressionClock(ProgressionState state, Clock clock, Consumer<String> warnLog) {
        this.state = state;
        this.clock = clock;
        this.warnLog = warnLog == null ? s -> {
        } : warnLog;
    }

    public void initialize() {
        long now = clock.millis();
        if (!state.initialized) {
            state.initialized = true;
            state.mode = ProgressionMode.REAL30;
            state.startEpochMillis = now;
            state.maxElapsedMillis = 0L;
            state.maxEffectiveDay = 0;
            state.markChanged();
        } else if (state.mode != ProgressionMode.REAL30) {
            int day = PermadeathCalendar.clampDay(state.maxEffectiveDay);
            warnLog.accept("World state was written by the " + state.mode + " calendar; re-anchoring REAL30 so that PD day "
                    + day + " is preserved.");
            state.mode = ProgressionMode.REAL30;
            state.startEpochMillis = now - day * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
            state.maxElapsedMillis = day * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
            state.maxEffectiveDay = day;
            state.markChanged();
        }
        lastPersistedElapsed = state.maxElapsedMillis;
        update();
    }

    @Override
    public ProgressionMode mode() {
        return ProgressionMode.REAL30;
    }

    public Instant startInstant() {
        return Instant.ofEpochMilli(state.startEpochMillis);
    }

    public Instant finalDayInstant() {
        return Instant.ofEpochMilli(state.startEpochMillis + PermadeathCalendar.REAL30_FINAL_ELAPSED_MILLIS);
    }

    public Instant dayInstant(int day) {
        return Instant.ofEpochMilli(state.startEpochMillis + PermadeathCalendar.clampDay(day) * PermadeathCalendar.REAL30_MILLIS_PER_DAY);
    }

    /** Effective elapsed wall-clock time (monotonic). */
    public long effectiveElapsedMillis() {
        long raw = clock.millis() - state.startEpochMillis;
        return Math.max(raw, state.maxElapsedMillis);
    }

    public static int dayForElapsed(long elapsedMillis) {
        if (elapsedMillis <= 0L) {
            return 0;
        }
        return PermadeathCalendar.clampDay(elapsedMillis / PermadeathCalendar.REAL30_MILLIS_PER_DAY);
    }

    @Override
    public int getDay() {
        return PermadeathCalendar.clampDay(state.maxEffectiveDay);
    }

    @Override
    public Duration getElapsed() {
        return Duration.ofMillis(Math.max(0L, effectiveElapsedMillis()));
    }

    @Override
    public Duration timeUntilDay(int day) {
        if (getDay() >= day) {
            return Duration.ZERO;
        }
        long target = PermadeathCalendar.clampDay(day) * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
        return Duration.ofMillis(Math.max(0L, target - effectiveElapsedMillis()));
    }

    @Override
    public DayChange update() {
        long now = clock.millis();
        long raw = now - state.startEpochMillis;
        if (raw + REGRESSION_WARN_THRESHOLD_MILLIS < state.maxElapsedMillis) {
            if (!regressionReported) {
                regressionReported = true;
                warnLog.accept("System clock went back by " + TimeFormat.realDuration(Duration.ofMillis(state.maxElapsedMillis - raw))
                        + " (elapsed " + raw + " ms < persisted maximum " + state.maxElapsedMillis
                        + " ms). Permadeath keeps the maximum and will NOT go back.");
            }
        } else if (raw >= state.maxElapsedMillis) {
            regressionReported = false;
        }
        long effective = Math.max(raw, state.maxElapsedMillis);
        boolean dirty = false;
        if (effective > state.maxElapsedMillis) {
            state.maxElapsedMillis = effective;
            if (effective - lastPersistedElapsed >= PERSIST_GRANULARITY_MILLIS) {
                dirty = true;
            }
        }
        int previous = getDay();
        int day = Math.max(dayForElapsed(effective), previous);
        if (day != state.maxEffectiveDay) {
            state.maxEffectiveDay = day;
            dirty = true;
        }
        if (dirty) {
            lastPersistedElapsed = state.maxElapsedMillis;
            state.markChanged();
        }
        return day == previous ? DayChange.NONE : new DayChange(previous, day);
    }

    @Override
    public void setDay(int day) {
        int target = PermadeathCalendar.clampDay(day);
        long now = clock.millis();
        state.startEpochMillis = now - target * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
        state.maxElapsedMillis = target * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
        state.maxEffectiveDay = target;
        state.executedMilestones.removeIf(m -> m > target);
        lastPersistedElapsed = state.maxElapsedMillis;
        regressionReported = false;
        state.markChanged();
    }

    @Override
    public String describe() {
        int day = getDay();
        StringBuilder sb = new StringBuilder();
        sb.append("Calendario: REAL30 (1 día Permadeath = 12 h reales; D60 = 720 h)\n");
        sb.append("Inicio (UTC): ").append(TimeFormat.utc(startInstant())).append('\n');
        sb.append("Tiempo real transcurrido: ").append(TimeFormat.realDuration(getElapsed())).append('\n');
        sb.append("Día Permadeath: ").append(day).append('/').append(PermadeathCalendar.FINAL_DAY).append('\n');
        sb.append("Fase: ").append(PermadeathCalendar.phaseName(getPhase())).append('\n');
        OptionalInt next = getNextMilestone();
        if (next.isPresent()) {
            sb.append("Siguiente hito: D").append(next.getAsInt()).append(" el ")
                    .append(TimeFormat.utc(dayInstant(next.getAsInt()))).append(" (faltan ")
                    .append(TimeFormat.realDuration(timeUntilDay(next.getAsInt()))).append(")\n");
        } else {
            sb.append("Siguiente hito: ninguno (D60 es la fase final)\n");
        }
        sb.append("D60 (UTC): ").append(TimeFormat.utc(finalDayInstant()));
        return sb.toString();
    }
}
