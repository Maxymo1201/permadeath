package com.serthekiller.permadeath.core;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * GAME60 calendar: 1 Permadeath day = 1 Minecraft day of the Overworld (24 000 day-time ticks).
 *
 * <ul>
 *     <li>PD day = floor(overworldDayTime / 24000) - baseWorldDay, clamped to 0..60.</li>
 *     <li>The world does not need to start at vanilla day 0: {@code baseWorldDay} is stored when the
 *     calendar is first initialised.</li>
 *     <li>Rolling {@code /time} back never moves Permadeath back: the effective day is the persistent
 *     maximum ever observed.</li>
 *     <li>Sleeping / skipping nights advances the day normally because it advances the day time.</li>
 *     <li>Never uses the system clock or LocalDate.</li>
 * </ul>
 */
public final class GameDayProgressionClock implements ProgressionClock {
    private final ProgressionState state;
    private final LongSupplier overworldDayTime;
    private final Consumer<String> warnLog;
    private boolean rollbackReported;

    public GameDayProgressionClock(ProgressionState state, LongSupplier overworldDayTime, Consumer<String> warnLog) {
        this.state = state;
        this.overworldDayTime = overworldDayTime;
        this.warnLog = warnLog == null ? s -> {
        } : warnLog;
    }

    /** Must be called once the Overworld (and the persisted state) is available. */
    public void initialize() {
        long worldDay = currentWorldDay();
        if (!state.initialized) {
            state.initialized = true;
            state.mode = ProgressionMode.GAME60;
            state.baseWorldDay = worldDay;
            state.maxEffectiveDay = 0;
            state.markChanged();
        } else if (state.mode != ProgressionMode.GAME60) {
            int day = PermadeathCalendar.clampDay(state.maxEffectiveDay);
            warnLog.accept("World state was written by the " + state.mode + " calendar; re-anchoring GAME60 so that PD day "
                    + day + " is preserved (baseWorldDay=" + (worldDay - day) + ").");
            state.mode = ProgressionMode.GAME60;
            state.baseWorldDay = worldDay - day;
            state.maxEffectiveDay = day;
            state.markChanged();
        }
        update();
    }

    @Override
    public ProgressionMode mode() {
        return ProgressionMode.GAME60;
    }

    public long currentWorldDay() {
        return Math.floorDiv(overworldDayTime.getAsLong(), PermadeathCalendar.TICKS_PER_MINECRAFT_DAY);
    }

    public long baseWorldDay() {
        return state.baseWorldDay;
    }

    /** PD day derived directly from the current world time, without the rollback protection. */
    public long rawDay() {
        return currentWorldDay() - state.baseWorldDay;
    }

    @Override
    public int getDay() {
        return PermadeathCalendar.clampDay(state.maxEffectiveDay);
    }

    @Override
    public Duration getElapsed() {
        long ticks = overworldDayTime.getAsLong() - state.baseWorldDay * PermadeathCalendar.TICKS_PER_MINECRAFT_DAY;
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }

    @Override
    public Duration timeUntilDay(int day) {
        if (getDay() >= day) {
            return Duration.ZERO;
        }
        long target = (state.baseWorldDay + day) * PermadeathCalendar.TICKS_PER_MINECRAFT_DAY;
        long remainingTicks = Math.max(0L, target - overworldDayTime.getAsLong());
        return Duration.ofMillis(remainingTicks * 50L);
    }

    /** Remaining Minecraft ticks until {@code day} (for display in game days). */
    public long ticksUntilDay(int day) {
        if (getDay() >= day) {
            return 0L;
        }
        long target = (state.baseWorldDay + day) * PermadeathCalendar.TICKS_PER_MINECRAFT_DAY;
        return Math.max(0L, target - overworldDayTime.getAsLong());
    }

    @Override
    public DayChange update() {
        long raw = rawDay();
        int previous = getDay();
        if (raw < state.maxEffectiveDay) {
            if (!rollbackReported) {
                rollbackReported = true;
                warnLog.accept("Overworld time is behind the Permadeath calendar (world PD day " + raw + " < effective day "
                        + state.maxEffectiveDay + "). /time rollback detected: Permadeath will NOT go back.");
            }
            // Re-anchor on the current world day: waiting for the world to catch up froze the calendar for as many
            // Minecraft days as were rolled back (all of them after a plain /time set day, which resets the counter).
            state.baseWorldDay = currentWorldDay() - state.maxEffectiveDay;
            state.markChanged();
            raw = state.maxEffectiveDay;
        } else {
            rollbackReported = false;
        }
        int effective = PermadeathCalendar.clampDay(Math.max(raw, state.maxEffectiveDay));
        if (effective != state.maxEffectiveDay) {
            state.maxEffectiveDay = effective;
            state.markChanged();
        }
        return effective == previous ? DayChange.NONE : new DayChange(previous, effective);
    }

    @Override
    public void setDay(int day) {
        int target = PermadeathCalendar.clampDay(day);
        state.baseWorldDay = currentWorldDay() - target;
        state.maxEffectiveDay = target;
        state.executedMilestones.removeIf(m -> m > target);
        rollbackReported = false;
        state.markChanged();
    }

    @Override
    public String describe() {
        int day = getDay();
        StringBuilder sb = new StringBuilder();
        sb.append("Calendario: GAME60 (1 día Permadeath = 1 día de Minecraft, 24000 ticks del Overworld)\n");
        sb.append("Día base del mundo: ").append(state.baseWorldDay).append('\n');
        sb.append("Día actual del mundo: ").append(currentWorldDay()).append('\n');
        sb.append("Día Permadeath: ").append(day).append('/').append(PermadeathCalendar.FINAL_DAY).append('\n');
        sb.append("Fase: ").append(PermadeathCalendar.phaseName(getPhase())).append('\n');
        OptionalInt next = getNextMilestone();
        if (next.isPresent()) {
            long ticks = ticksUntilDay(next.getAsInt());
            sb.append("Siguiente hito: D").append(next.getAsInt()).append(" (faltan ")
                    .append(TimeFormat.minecraftTicks(ticks)).append(')').append('\n');
            sb.append("D60 en: ").append(TimeFormat.minecraftTicks(ticksUntilDay(PermadeathCalendar.FINAL_DAY)));
        } else {
            sb.append("Siguiente hito: ninguno (D60 es la fase final)");
        }
        return sb.toString();
    }
}
