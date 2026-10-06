package com.serthekiller.permadeath.worldgen;

import com.mojang.serialization.MapCodec;
import com.serthekiller.permadeath.PermadeathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Worldgen types of The Beginning, same ids as the Fabric mod (they are referenced by the bundled datapack
 * JSON in data/permadeath/worldgen and by existing worlds).
 */
public final class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, PermadeathMod.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, PermadeathMod.MOD_ID);
    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, PermadeathMod.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, PermadeathMod.MOD_ID);
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, PermadeathMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<UniformIslandsDensityFunction>> UNIFORM_ISLANDS =
            DENSITY_FUNCTION_TYPES.register("uniform_islands", () -> UniformIslandsDensityFunction.CODEC);
    public static final DeferredHolder<Feature<?>, BeginningCoralFeature> BEGINNING_CORAL =
            FEATURES.register("beginning_coral", () -> new BeginningCoralFeature(BeginningCoralConfiguration.CODEC));
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<ProtectTerrainProcessor>> PROTECT_TERRAIN =
            STRUCTURE_PROCESSORS.register("protect_terrain", () -> () -> ProtectTerrainProcessor.CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<UniformIslandJigsawStructure>> UNIFORM_ISLAND_JIGSAW =
            STRUCTURE_TYPES.register("uniform_island_jigsaw", () -> () -> UniformIslandJigsawStructure.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<IslandDensityPlacementModifier>> ISLAND_DENSITY =
            PLACEMENT_MODIFIERS.register("island_density", () -> () -> IslandDensityPlacementModifier.CODEC);

    private ModWorldgen() {
    }

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTION_TYPES.register(modBus);
        FEATURES.register(modBus);
        STRUCTURE_PROCESSORS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        PLACEMENT_MODIFIERS.register(modBus);
    }
}
