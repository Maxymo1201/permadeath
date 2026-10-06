package com.serthekiller.permadeath.mixin;

import com.serthekiller.permadeath.mechanics.StructureRules;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * From D40 villages, desert pyramids, jungle temples, ocean monuments and shipwrecks no longer start in new
 * chunks (Fabric ChunkGeneratorStructureBlockMixin). Structure sets are not reloadable at runtime and there is
 * no structure-start event, hence the mixin. Runs on worldgen threads: it only reads the current day.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void permadeath$blockStructures(StructureSet.StructureSelectionEntry entry, StructureManager structureManager,
            RegistryAccess registryAccess, RandomState randomState, StructureTemplateManager templateManager, long seed,
            ChunkAccess chunk, ChunkPos chunkPos, SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        if (StructureRules.isBlocked(entry.structure())) {
            cir.setReturnValue(false);
        }
    }
}
