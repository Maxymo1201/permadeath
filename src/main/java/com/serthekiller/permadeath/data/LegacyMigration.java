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
import java.util.function.Consumer;

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
        // Each file on its own: one damaged file (e.g. a line truncated by a crash) used to skip every later one.
        parse(date, text -> legacy.parseDateFile(text, LegacyFabricState.today(ZoneId.systemDefault(), now)));
        parse(storm, legacy::parseStormFile);
        if (Files.exists(boost)) {
            legacy.notes.add("permadeath_boost.txt: ignorado (el nivel de buffs del Death Train ahora se deriva del día)");
        }
        parse(wither, legacy::parseWitherFile);
        parse(lifeOrb, legacy::parseLifeOrbFile);
        parse(mikecrack, legacy::parseMikecrackFile);
        parse(apples, legacy::parseHyperAppleJson);
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

    private static void parse(Path path, Consumer<String> parser) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            parser.accept(read(path));
        } catch (RuntimeException | IOException e) {
            PermadeathMod.LOGGER.error("[Permadeath] Legacy Fabric file {} could not be parsed and is skipped (the file is kept untouched)", path, e);
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
