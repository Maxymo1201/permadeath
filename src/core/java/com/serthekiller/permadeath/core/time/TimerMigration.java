package com.serthekiller.permadeath.core.time;

import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Format 1 → 2: the old absolute wall-clock deadlines become remaining ACTIVE time.
 *
 * <ul>
 *     <li>Death Train and X2 Shulker Shells keep exactly the time that was observable at the migration
 *     ({@code end - now}); the profile factor is NOT applied again to a storm that already exists.</li>
 *     <li>Life Orb: a countdown that already ran out stays over (penalty active, applied only once because the
 *     modifier is idempotent); a running one keeps its observable time, capped at the new countdown of the profile.</li>
 *     <li>A world already on D60 with the countdown started gets an ACTIVE final challenge whose timeline matches the
 *     countdown (final = Life Orb left + (final - countdown)); with the countdown over only the post-deadline part is
 *     left.</li>
 *     <li>Milestones, Wither counters and every other value are kept as they are. Negative times become 0.</li>
 * </ul>
 */
public final class TimerMigration {
    private TimerMigration() {
    }

    /** @return the log lines of what was converted (empty when nothing had to be migrated) */
    public static List<String> migrate(ProgressionState s, PermadeathTimings timings, long nowEpochMillis) {
        List<String> log = new ArrayList<>();
        ProgressionState.LegacyTimers legacy = s.legacyTimers;
        if (s.formatVersion >= ProgressionState.CURRENT_FORMAT_VERSION || legacy == null) {
            s.legacyTimers = null;
            return log;
        }
        int from = s.formatVersion;
        if (legacy.deathTrainEndEpochMillis() > 0L) {
            long left = Math.max(0L, legacy.deathTrainEndEpochMillis() - nowEpochMillis);
            s.deathTrainRemainingMillis = left;
            log.add(left > 0L ? "Death Train: quedan " + TimeFormat.compact(left) + " (tiempo observable, sin volver a escalar)"
                    : "Death Train: había terminado con el servidor apagado");
        }
        if (legacy.shulkerEventEndEpochMillis() > 0L) {
            long left = Math.max(0L, legacy.shulkerEventEndEpochMillis() - nowEpochMillis);
            s.shulkerEventRemainingMillis = left;
            log.add(left > 0L ? "X2 Shulker Shells: quedan " + TimeFormat.compact(left) : "X2 Shulker Shells: había terminado");
        }
        if (!s.lifeOrbActive && legacy.lifeOrbDeadlineEpochMillis() > 0L) {
            long left = legacy.lifeOrbDeadlineEpochMillis() - nowEpochMillis;
            if (left <= 0L) {
                s.lifeOrbActive = true;
                s.lifeOrbRemainingMillis = -1L;
                log.add("Life Orb: el plazo ya había vencido; la penalización sigue activa");
            } else {
                s.lifeOrbRemainingMillis = Math.min(left, timings.lifeOrbCountdownMillis());
                log.add("Life Orb: quedan " + TimeFormat.compact(s.lifeOrbRemainingMillis)
                        + (left > timings.lifeOrbCountdownMillis() ? " (limitado al plazo del perfil)" : ""));
            }
        } else if (!s.lifeOrbActive) {
            s.lifeOrbRemainingMillis = -1L;
        }
        if (s.maxEffectiveDay >= PermadeathCalendar.FINAL_DAY && s.finalPhaseState == FinalPhaseState.NOT_STARTED
                && (s.lifeOrbActive || s.lifeOrbRemainingMillis >= 0L)) {
            long afterDeadline = Math.max(0L, timings.finalPhaseMillis() - timings.lifeOrbCountdownMillis());
            s.finalPhaseState = FinalPhaseState.ACTIVE;
            s.finalPhaseRemainingMillis = (s.lifeOrbActive ? 0L : s.lifeOrbRemainingMillis) + afterDeadline;
            s.finalPhaseStartedEpochMillis = nowEpochMillis;
            if (s.finalPhaseRemainingMillis <= 0L) {
                s.finalPhaseRemainingMillis = 1L;
            }
            log.add("Fase final: el mundo ya estaba en el D60; desafío final activo con " + TimeFormat.compact(s.finalPhaseRemainingMillis));
        }
        s.formatVersion = ProgressionState.CURRENT_FORMAT_VERSION;
        s.migratedFromVersion = from;
        s.migrationEpochMillis = nowEpochMillis;
        s.migrationSummary = log.isEmpty() ? "sin temporizadores activos" : String.join("; ", log);
        s.legacyTimers = null;
        s.markChanged();
        return log;
    }
}
