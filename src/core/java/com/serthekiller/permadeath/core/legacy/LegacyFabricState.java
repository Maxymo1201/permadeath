package com.serthekiller.permadeath.core.legacy;

import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.time.PermadeathTimings;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser/importer for the loose files written by the original Fabric mod in the world folder:
 * <ul>
 *     <li>{@code permadeath_date.txt} – start date "dd/MM/yyyy" (day = daysBetween(start, today) + 1, max 70)</li>
 *     <li>{@code permadeath_storm.txt} – "active\nticksRemaining"</li>
 *     <li>{@code permadeath_boost.txt} – "lastCycle\nlevel" (now derived from the day, informational)</li>
 *     <li>{@code permadeath_wither.txt} – "uuid:ticks" per line</li>
 *     <li>{@code permadeath_lifeorb.txt} – "expirationEpochMillis\nactive"</li>
 *     <li>{@code permadeath_mikecrack.txt} – "true|false"</li>
 *     <li>{@code data/hyper_apple_consumed.json} – {"uuid": count}</li>
 * </ul>
 * The original files are never deleted.
 */
public final class LegacyFabricState {
    public static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern JSON_ENTRY = Pattern.compile("\"([0-9a-fA-F-]{36})\"\\s*:\\s*(\\d+)");

    public Integer legacyDay;
    public Boolean stormActive;
    public Long stormTicksRemaining;
    public final Map<UUID, Long> witherTicks = new LinkedHashMap<>();
    public Long lifeOrbExpiration;
    public Boolean lifeOrbActive;
    public Boolean mikecrack;
    public final Map<UUID, Integer> hyperApples = new LinkedHashMap<>();
    public final List<String> notes = new ArrayList<>();

    public boolean isEmpty() {
        return legacyDay == null && stormActive == null && witherTicks.isEmpty() && lifeOrbExpiration == null
                && lifeOrbActive == null && mikecrack == null && hyperApples.isEmpty();
    }

    /** Legacy Fabric day number for a start date (identical formula to the Fabric DateManager, capped to D60). */
    public static int legacyDayFor(LocalDate start, LocalDate today) {
        long current = ChronoUnit.DAYS.between(start, today) + 1L;
        return PermadeathCalendar.clampDay(Math.max(1L, current));
    }

    public void parseDateFile(String content, LocalDate today) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            notes.add("permadeath_date.txt vacío: ignorado");
            return;
        }
        LocalDate start = LocalDate.parse(trimmed, LEGACY_DATE);
        legacyDay = legacyDayFor(start, today);
        notes.add("permadeath_date.txt: inicio " + trimmed + " -> día " + legacyDay);
    }

    public void parseStormFile(String content) {
        String[] lines = content.trim().split("\\R");
        if (lines.length >= 2) {
            stormActive = Boolean.parseBoolean(lines[0].trim());
            stormTicksRemaining = Long.parseLong(lines[1].trim());
            notes.add("permadeath_storm.txt: activa=" + stormActive + ", ticks=" + stormTicksRemaining);
        }
    }

    public void parseWitherFile(String content) {
        for (String line : content.split("\\R")) {
            if (!line.contains(":")) {
                continue;
            }
            String[] parts = line.trim().split(":");
            witherTicks.put(UUID.fromString(parts[0].trim()), Long.parseLong(parts[1].trim()));
        }
        notes.add("permadeath_wither.txt: " + witherTicks.size() + " contadores");
    }

    public void parseLifeOrbFile(String content) {
        String[] lines = content.trim().split("\\R");
        if (lines.length >= 2) {
            lifeOrbExpiration = Long.parseLong(lines[0].trim());
            lifeOrbActive = Boolean.parseBoolean(lines[1].trim());
            notes.add("permadeath_lifeorb.txt: expiración=" + lifeOrbExpiration + ", activo=" + lifeOrbActive);
        }
    }

    public void parseMikecrackFile(String content) {
        mikecrack = Boolean.parseBoolean(content.trim());
        notes.add("permadeath_mikecrack.txt: " + mikecrack);
    }

    public void parseHyperAppleJson(String content) {
        Matcher m = JSON_ENTRY.matcher(content);
        while (m.find()) {
            hyperApples.put(UUID.fromString(m.group(1)), Integer.parseInt(m.group(2)));
        }
        notes.add("hyper_apple_consumed.json: " + hyperApples.size() + " jugadores");
    }

    /**
     * Applies the parsed legacy data to a fresh state. The PD day number is preserved: GAME60 anchors
     * {@code baseWorldDay = currentWorldDay - day}; REAL30 anchors {@code start = now - day * 12h}.
     */
    public void applyTo(ProgressionState state, ProgressionMode mode, long currentWorldDay, long nowEpochMillis) {
        if (legacyDay != null) {
            int day = PermadeathCalendar.clampDay(legacyDay);
            state.initialized = true;
            state.mode = mode;
            state.maxEffectiveDay = day;
            if (mode == ProgressionMode.GAME60) {
                state.baseWorldDay = currentWorldDay - day;
            } else {
                state.startEpochMillis = nowEpochMillis - day * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
                state.maxElapsedMillis = day * PermadeathCalendar.REAL30_MILLIS_PER_DAY;
            }
        }
        if (Boolean.TRUE.equals(stormActive) && stormTicksRemaining != null && stormTicksRemaining > 0) {
            // Fabric counted the storm in server ticks (paused while offline), like the active time of format 2:
            // the remaining time is kept as it was (nominal 20 tps), without applying the profile factor again.
            state.deathTrainRemainingMillis = stormTicksRemaining * 50L;
        }
        witherTicks.forEach((uuid, ticks) -> state.witherRemainingMillis.put(uuid, Math.max(0L, ticks) * 50L));
        if (lifeOrbActive != null) {
            state.lifeOrbActive = lifeOrbActive;
        }
        if (lifeOrbExpiration != null && lifeOrbExpiration > 0L && !state.lifeOrbActive) {
            // Fabric stored an absolute deadline: an expired one stays expired, a running one keeps its observable
            // time (at most the countdown of the profile).
            long left = lifeOrbExpiration - nowEpochMillis;
            if (left <= 0L) {
                state.lifeOrbActive = true;
                state.lifeOrbRemainingMillis = -1L;
            } else {
                state.lifeOrbRemainingMillis = Math.min(left, PermadeathTimings.forMode(mode).lifeOrbCountdownMillis());
            }
        }
        if (mikecrack != null) {
            state.mikecrackEnabled = mikecrack;
        }
        hyperApples.forEach((uuid, count) -> state.hyperApplesConsumed.merge(uuid, count, Math::max));
        state.legacyMigrated = true;
        state.markChanged();
    }

    public static LocalDate today(ZoneId zone, long nowEpochMillis) {
        return java.time.Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate();
    }
}
