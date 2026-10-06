package com.serthekiller.permadeath.data;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.legacy.LegacyFabricState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;

/**
 * Imports the loose state files written by the Fabric mod into {@link PermadeathData}. Runs once per world;
 * the original files are left untouched (never deleted).
 */
public final class LegacyMigration {
    private LegacyMigration() {
    }

    public static void migrateIfNeeded(MinecraftServer server, ProgressionState state, ProgressionMode mode) {
        if (state.legacyMigrated) {
            return;
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path date = root.resolve("permadeath_date.txt");
        Path storm = root.resolve("permadeath_storm.txt");
        Path boost = root.resolve("permadeath_boost.txt");
        Path wither = root.resolve("permadeath_wither.txt");
        Path lifeOrb = root.resolve("permadeath_lifeorb.txt");
        Path mikecrack = root.resolve("permadeath_mikecrack.txt");
        Path apples = root.resolve("data").resolve("hyper_apple_consumed.json");
        boolean any = Files.exists(date) || Files.exists(storm) || Files.exists(wither) || Files.exists(lifeOrb)
                || Files.exists(mikecrack) || Files.exists(apples) || Files.exists(boost);
        if (!any) {
            return;
        }
        long now = System.currentTimeMillis();
        LegacyFabricState legacy = new LegacyFabricState();
        try {
            if (Files.exists(date)) {
                legacy.parseDateFile(read(date), LegacyFabricState.today(ZoneId.systemDefault(), now));
            }
            if (Files.exists(storm)) {
                legacy.parseStormFile(read(storm));
            }
            if (Files.exists(boost)) {
                legacy.notes.add("permadeath_boost.txt: ignorado (el nivel de buffs del Death Train ahora se deriva del día)");
            }
            if (Files.exists(wither)) {
                legacy.parseWitherFile(read(wither));
            }
            if (Files.exists(lifeOrb)) {
                legacy.parseLifeOrbFile(read(lifeOrb));
            }
            if (Files.exists(mikecrack)) {
                legacy.parseMikecrackFile(read(mikecrack));
            }
            if (Files.exists(apples)) {
                legacy.parseHyperAppleJson(read(apples));
            }
        } catch (RuntimeException | IOException e) {
            PermadeathMod.LOGGER.error("[Permadeath] Legacy Fabric state could not be fully parsed; the original files are kept untouched", e);
        }
        if (state.initialized) {
            // The NeoForge calendar already exists: never overwrite it with older legacy data.
            legacy.legacyDay = null;
            legacy.notes.add("calendario NeoForge ya inicializado: el día legacy no se aplica");
        }
        long worldDay = Math.floorDiv(server.overworld().getDayTime(), 24_000L);
        legacy.applyTo(state, mode, worldDay, now);
        for (String note : legacy.notes) {
            PermadeathMod.LOGGER.info("[Permadeath] legacy: {}", note);
        }
        PermadeathMod.LOGGER.info("[Permadeath] Legacy state migrated successfully (original files kept in {})", root.toAbsolutePath());
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
