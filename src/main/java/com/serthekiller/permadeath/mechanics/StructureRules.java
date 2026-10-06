package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Set;

/** Vanilla structures that stop generating in new chunks from D40 (used by ChunkGeneratorMixin). */
public final class StructureRules {
    public static final int FROM_DAY = 40;
    public static final Set<ResourceLocation> BLOCKED = Set.of(
            ResourceLocation.withDefaultNamespace("village_plains"),
            ResourceLocation.withDefaultNamespace("village_desert"),
            ResourceLocation.withDefaultNamespace("village_savanna"),
            ResourceLocation.withDefaultNamespace("village_taiga"),
            ResourceLocation.withDefaultNamespace("village_snowy"),
            ResourceLocation.withDefaultNamespace("desert_pyramid"),
            ResourceLocation.withDefaultNamespace("jungle_temple"),
            ResourceLocation.withDefaultNamespace("monument"),
            ResourceLocation.withDefaultNamespace("shipwreck"),
            ResourceLocation.withDefaultNamespace("shipwreck_beached"));

    private StructureRules() {
    }

    public static boolean isBlocked(Holder<Structure> structure) {
        if (Permadeath.day() < FROM_DAY) {
            return false;
        }
        return structure.unwrapKey().map(key -> BLOCKED.contains(key.location())).orElse(false);
    }
}
