package com.serthekiller.permadeath.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

import java.util.List;
import java.util.Optional;

/**
 * Jigsaw structure that only starts where The Beginning terrain is a large enough island: at least 65% of a
 * 15x15 grid (step 10, radius 70) of {@link UniformIslandsDensityFunction} samples must be solid, plus the four
 * points 60 blocks away. Same codec fields as vanilla {@code minecraft:jigsaw} ({@code permadeath:uniform_island_jigsaw}).
 */
public final class UniformIslandJigsawStructure extends Structure {
    private static final double DENSITY_THRESHOLD = 0.65;
    private static final int SAMPLE_RADIUS = 70;
    private static final int SAMPLE_STEP = 10;
    private static final int EDGE_RADIUS = 60;

    public static final MapCodec<UniformIslandJigsawStructure> CODEC = RecordCodecBuilder.<UniformIslandJigsawStructure>mapCodec(instance -> instance.group(
            settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
            Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.maxDepth),
            HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
            Codec.BOOL.fieldOf("use_expansion_hack").forGetter(s -> s.useExpansionHack),
            Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
            Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(s -> s.poolAliases),
            DimensionPadding.CODEC.optionalFieldOf("dimension_padding", JigsawStructure.DEFAULT_DIMENSION_PADDING).forGetter(s -> s.dimensionPadding),
            LiquidSettings.CODEC.optionalFieldOf("liquid_settings", JigsawStructure.DEFAULT_LIQUID_SETTINGS).forGetter(s -> s.liquidSettings)
    ).apply(instance, UniformIslandJigsawStructure::new)).validate(UniformIslandJigsawStructure::verifyRange);

    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final List<PoolAliasBinding> poolAliases;
    private final DimensionPadding dimensionPadding;
    private final LiquidSettings liquidSettings;

    public UniformIslandJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName, int maxDepth, HeightProvider startHeight, boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap, int maxDistanceFromCenter, List<PoolAliasBinding> poolAliases,
            DimensionPadding dimensionPadding, LiquidSettings liquidSettings) {
        super(settings);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.poolAliases = poolAliases;
        this.dimensionPadding = dimensionPadding;
        this.liquidSettings = liquidSettings;
    }

    private static DataResult<UniformIslandJigsawStructure> verifyRange(UniformIslandJigsawStructure s) {
        int padding = switch (s.terrainAdaptation()) {
            case NONE -> 0;
            case BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE -> 12;
        };
        return s.maxDistanceFromCenter + padding > 128
                ? DataResult.error(() -> "Structure size including terrain adaptation must not exceed 128")
                : DataResult.success(s);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int y = this.startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        BlockPos pos = new BlockPos(chunkPos.getMinBlockX(), y, chunkPos.getMinBlockZ());
        if (!hasEnoughIslandDensity(pos)) {
            return Optional.empty();
        }
        return JigsawPlacement.addPieces(context, this.startPool, this.startJigsawName, this.maxDepth, pos, this.useExpansionHack,
                this.projectStartToHeightmap, this.maxDistanceFromCenter, PoolAliasLookup.create(this.poolAliases, pos, context.seed()),
                this.dimensionPadding, this.liquidSettings);
    }

    private static boolean hasEnoughIslandDensity(BlockPos center) {
        int solid = 0;
        int total = 0;
        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_STEP) {
                total++;
                if (isSolid(center.getX() + dx, center.getY(), center.getZ() + dz)) {
                    solid++;
                }
            }
        }
        if (total == 0 || (double) solid / total < DENSITY_THRESHOLD) {
            return false;
        }
        return isSolid(center.getX(), center.getY(), center.getZ() - EDGE_RADIUS)
                && isSolid(center.getX(), center.getY(), center.getZ() + EDGE_RADIUS)
                && isSolid(center.getX() + EDGE_RADIUS, center.getY(), center.getZ())
                && isSolid(center.getX() - EDGE_RADIUS, center.getY(), center.getZ());
    }

    private static boolean isSolid(int x, int y, int z) {
        return UniformIslandsDensityFunction.INSTANCE.sample(x, y, z) > 0.0;
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.UNIFORM_ISLAND_JIGSAW.get();
    }
}
