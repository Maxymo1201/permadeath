package com.serthekiller.permadeath.core;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimeFormat {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private TimeFormat() {
    }

    public static String utc(Instant instant) {
        return UTC.format(instant);
    }

    /** "2d 03h 05m 07s" style. */
    public static String realDuration(Duration d) {
        long total = Math.max(0L, d.getSeconds());
        long days = total / 86_400L;
        long hours = (total % 86_400L) / 3_600L;
        long minutes = (total % 3_600L) / 60L;
        long seconds = total % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        sb.append(String.format("%02dh %02dm %02ds", hours, minutes, seconds));
        return sb.toString();
    }

    /** Minecraft time: "3 días MC + 12000 ticks (~10m 00s a 20 TPS)". */
    public static String minecraftTicks(long ticks) {
        long days = ticks / PermadeathCalendar.TICKS_PER_MINECRAFT_DAY;
        long rest = ticks % PermadeathCalendar.TICKS_PER_MINECRAFT_DAY;
        return days + " días MC + " + rest + " ticks (~" + realDuration(Duration.ofMillis(ticks * 50L)) + " a 20 TPS)";
    }

    /** "HH:MM:SS" countdown. */
    public static String hms(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        return String.format("%02d:%02d:%02d", totalSeconds / 3600L, (totalSeconds % 3600L) / 60L, totalSeconds % 60L);
    }
}
