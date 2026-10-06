package com.serthekiller.permadeath.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/** Air blocks of a template never replace existing terrain ({@code permadeath:protect_terrain}). */
public final class ProtectTerrainProcessor extends StructureProcessor {
    public static final ProtectTerrainProcessor INSTANCE = new ProtectTerrainProcessor();
    public static final MapCodec<ProtectTerrainProcessor> CODEC = MapCodec.unit(INSTANCE);

    private ProtectTerrainProcessor() {
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
            StructureTemplate.StructureBlockInfo blockInfo, StructureTemplate.StructureBlockInfo relativeBlockInfo,
            StructurePlaceSettings settings) {
        return relativeBlockInfo.state().isAir() && !level.getBlockState(relativeBlockInfo.pos()).isAir() ? null : relativeBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModWorldgen.PROTECT_TERRAIN.get();
    }
}
