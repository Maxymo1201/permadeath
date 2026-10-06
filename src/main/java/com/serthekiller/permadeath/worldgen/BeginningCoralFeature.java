package com.serthekiller.permadeath.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * Places one random "coral" template on a purpur surface with at least 3 solid purpur blocks below and free
 * air above the whole footprint (Fabric BeginningCoralFeature, same checks and flags).
 */
public class BeginningCoralFeature extends Feature<BeginningCoralConfiguration> {
    public BeginningCoralFeature(Codec<BeginningCoralConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BeginningCoralConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos start = context.origin();
        RandomSource random = context.random();
        List<ResourceLocation> pool = context.config().structures();
        if (pool.isEmpty()) {
            return false;
        }
        ResourceLocation chosen = pool.get(random.nextInt(pool.size()));
        StructureTemplate template = level.getLevel().getStructureManager().getOrCreate(chosen);
        Rotation rotation = Rotation.getRandom(random);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(true)
                .addProcessor(ProtectTerrainProcessor.INSTANCE);
        Vec3i size = template.getSize(rotation);

        BlockPos surface = null;
        for (int dy = 0; dy >= -20; dy--) {
            BlockPos check = start.above(dy);
            if (level.getBlockState(check).is(Blocks.PURPUR_BLOCK) && level.isEmptyBlock(check.above())) {
                int solid = 0;
                for (int down = 0; down <= 3; down++) {
                    if (level.getBlockState(check.below(down)).is(Blocks.PURPUR_BLOCK)) {
                        solid++;
                    }
                }
                if (solid >= 3) {
                    surface = check;
                    break;
                }
            }
        }
        if (surface == null) {
            return false;
        }
        BlockPos placePos = surface.above().offset(-size.getX() / 2, 0, -size.getZ() / 2);
        int groundY = surface.getY();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                BlockPos ground = new BlockPos(placePos.getX() + dx, groundY, placePos.getZ() + dz);
                if (!level.getBlockState(ground).is(Blocks.PURPUR_BLOCK)) {
                    return false;
                }
                int solidBelow = 0;
                for (int down = 1; down <= 3; down++) {
                    if (level.getBlockState(ground.below(down)).is(Blocks.PURPUR_BLOCK)) {
                        solidBelow++;
                    }
                }
                if (solidBelow < 2) {
                    return false;
                }
                for (int dy = 0; dy < size.getY(); dy++) {
                    if (!level.isEmptyBlock(ground.above(1 + dy))) {
                        return false;
                    }
                }
            }
        }
        template.placeInWorld(level, placePos, placePos, settings, random, 2);
        return true;
    }
}
