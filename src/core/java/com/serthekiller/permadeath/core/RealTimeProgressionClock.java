package com.serthekiller.permadeath.core;

import com.serthekiller.permadeath.core.time.PermadeathTimings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
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
 *     <li>Optional strict campaign ({@code strictCampaignDuration}): D60 starts 6 h early (hour 714) so that the 6 h
 *     final challenge ends exactly at hour 720. The earlier milestones do not move.</li>
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
    private final BooleanSupplier strictCampaign;
    private long lastPersistedElapsed = Long.MIN_VALUE;
    private boolean regressionReported;

    public RealTimeProgressionClock(ProgressionState state, Clock clock, Consumer<String> warnLog) {
        this(state, clock, warnLog, () -> false);
    }

    public RealTimeProgressionClock(ProgressionState state, Clock clock, Consumer<String> warnLog, BooleanSupplier strictCampaign) {
        this.state = state;
        this.clock = clock;
        this.warnLog = warnLog == null ? s -> {
        } : warnLog;
        this.strictCampaign = strictCampaign == null ? () -> false : strictCampaign;
    }

    public boolean strictCampaign() {
        return strictCampaign.getAsBoolean();
    }

    /** Calendar time at which {@code day} begins (D60 at hour 714 in the strict campaign). */
    public long dayStartElapsedMillis(int day) {
        int d = PermadeathCalendar.clampDay(day);
        if (d == PermadeathCalendar.FINAL_DAY && strictCampaign()) {
            return PermadeathTimings.REAL30.strictFinalDayElapsedMillis();
        }
        return d * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
    }

    private int dayFor(long elapsedMillis) {
        int day = dayForElapsed(elapsedMillis);
        if (day < PermadeathCalendar.FINAL_DAY && strictCampaign()
                && elapsedMillis >= PermadeathTimings.REAL30.strictFinalDayElapsedMillis()) {
            return PermadeathCalendar.FINAL_DAY;
        }
        return day;
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
        return Instant.ofEpochMilli(state.startEpochMillis + dayStartElapsedMillis(PermadeathCalendar.FINAL_DAY));
    }

    public Instant dayInstant(int day) {
        return Instant.ofEpochMilli(state.startEpochMillis + dayStartElapsedMillis(day));
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
        long target = dayStartElapsedMillis(day);
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
        int day = Math.max(dayFor(effective), previous);
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
        state.startEpochMillis = now - dayStartElapsedMillis(target);
        state.maxElapsedMillis = dayStartElapsedMillis(target);
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
        sb.append(strictCampaign()
                ? "Calendario: REAL30 estricto (1 día Permadeath = 12 h reales; D60 = 714 h y la final termina a las 720 h)\n"
                : "Calendario: REAL30 (1 día Permadeath = 12 h reales; D60 = 720 h, más 6 h efectivas de desafío final)\n");
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
