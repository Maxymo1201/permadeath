package com.serthekiller.permadeath.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/** Keeps a placement only where at least 55% of a 17x21x17 sample around it is solid ({@code permadeath:island_density}). */
public final class IslandDensityPlacementModifier extends PlacementModifier {
    public static final IslandDensityPlacementModifier INSTANCE = new IslandDensityPlacementModifier();
    public static final MapCodec<IslandDensityPlacementModifier> CODEC = MapCodec.unit(INSTANCE);

    private IslandDensityPlacementModifier() {
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int solid = 0;
        int total = 0;
        int radius = 8;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = -10; dy <= 10; dy += 2) {
                    total++;
                    if (!context.getBlockState(pos.offset(dx, dy, dz)).isAir()) {
                        solid++;
                    }
                }
            }
        }
        return (double) solid / total >= 0.55 ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModWorldgen.ISLAND_DENSITY.get();
    }
}
