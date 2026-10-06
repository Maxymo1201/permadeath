package com.serthekiller.permadeath;

import com.serthekiller.permadeath.core.ProgressionMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the calendar identity packed into the jar ({@code permadeath_profile.properties}). It is
 * intentionally not a config option: a GAME60 jar is always GAME60 and a REAL30 jar is always REAL30.
 */
public final class BuildProfile {
    public static final String RESOURCE = "/permadeath_profile.properties";
    private static ProgressionMode mode;

    private BuildProfile() {
    }

    public static synchronized ProgressionMode mode() {
        if (mode == null) {
            mode = load();
        }
        return mode;
    }

    private static ProgressionMode load() {
        try (InputStream in = BuildProfile.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Permadeath jar has no calendar profile (" + RESOURCE
                        + "). Use permadeath-GAME60-neoforge-1.21.1.jar or permadeath-REAL30-neoforge-1.21.1.jar.");
            }
            Properties properties = new Properties();
            properties.load(in);
            return ProgressionMode.parse(properties.getProperty("mode"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the Permadeath calendar profile", e);
        }
    }
}
