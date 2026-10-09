package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.rules.DayRules;

/**
 * Every progression timer of a build, in milliseconds of ACTIVE real time (see {@link EventClock}). This is the
 * only place where these durations are defined: every system asks {@link #forMode(ProgressionMode)}.
 *
 * <table>
 *     <tr><th>Timer</th><th>GAME60</th><th>REAL30</th></tr>
 *     <tr><td>D60 periodic Wither</td><td>8 min</td><td>30 min</td></tr>
 *     <tr><td>D60 Life Orb</td><td>20 min</td><td>4 h</td></tr>
 *     <tr><td>X2 Shulker Shells</td><td>10 min</td><td>2 h</td></tr>
 *     <tr><td>The Beginning curse / blessing</td><td>10 min</td><td>6 h</td></tr>
 *     <tr><td>Death Train</td><td>original / 72, at least 60 s</td><td>original / 2</td></tr>
 *     <tr><td>D60 final challenge</td><td>30 min</td><td>6 h</td></tr>
 * </table>
 *
 * Tactical durations (fuses, cooldowns, attacks, short potions, dragon timings) are not progression timers and
 * stay in ticks where they are used.
 */
public record PermadeathTimings(
        ProgressionMode mode,
        long witherIntervalMillis,
        long lifeOrbCountdownMillis,
        long shulkerEventMillis,
        long beginningCurseMillis,
        long beginningBlessingMillis,
        long finalPhaseMillis,
        long deathTrainDivisor,
        long deathTrainMinimumMillis) {

    public static final long SECOND = 1_000L;
    public static final long MINUTE = 60L * SECOND;
    public static final long HOUR = 60L * MINUTE;

    public static final PermadeathTimings GAME60 = new PermadeathTimings(ProgressionMode.GAME60,
            8L * MINUTE, 20L * MINUTE, 10L * MINUTE, 10L * MINUTE, 10L * MINUTE, 30L * MINUTE, 72L, 60L * SECOND);
    public static final PermadeathTimings REAL30 = new PermadeathTimings(ProgressionMode.REAL30,
            30L * MINUTE, 4L * HOUR, 2L * HOUR, 6L * HOUR, 6L * HOUR, 6L * HOUR, 2L, 0L);

    public static PermadeathTimings forMode(ProgressionMode mode) {
        return mode == ProgressionMode.REAL30 ? REAL30 : GAME60;
    }

    /**
     * Death Train storm added by one death on {@code day}: the historical duration of {@link DayRules} scaled to the
     * profile (GAME60 {@code max(60 s, original / 72)}, REAL30 {@code original / 2}). Every historical duration is a
     * multiple of 30 minutes, so both divisions are exact.
     */
    public long deathTrainMillis(int day) {
        long original = DayRules.deathTrainDurationMillis(PermadeathCalendar.clampDay(day));
        return Math.max(deathTrainMinimumMillis, original / deathTrainDivisor);
    }

    /**
     * REAL30 optional strict campaign ({@code strictCampaignDuration}): the final challenge has to end at hour 720, so
     * D60 starts {@link #finalPhaseMillis} earlier (hour 714). The earlier milestones do not move.
     */
    public long strictFinalDayElapsedMillis() {
        return PermadeathCalendar.REAL30_FINAL_ELAPSED_MILLIS - finalPhaseMillis;
    }
}
