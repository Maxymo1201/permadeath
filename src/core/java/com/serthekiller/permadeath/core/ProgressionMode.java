package com.serthekiller.permadeath.core;

import java.util.Locale;

/**
 * Calendar source of a build. The mode is fixed per jar (see {@code permadeath_profile.properties});
 * it is never read from a user-editable config.
 */
public enum ProgressionMode {
    /** 1 Permadeath day = 1 Minecraft day (24 000 Overworld day-time ticks). */
    GAME60,
    /** 1 Permadeath day = 12 real hours measured on the UTC wall clock. D60 = 720 h. */
    REAL30;

    public static ProgressionMode parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Permadeath calendar profile is missing");
        }
        return ProgressionMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
