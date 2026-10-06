package com.serthekiller.permadeath.registry;

import com.serthekiller.permadeath.PermadeathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PermadeathMod.MOD_ID);

    /** {@code permadeath:infernal_netherite_block} (strength 5/5, netherite sounds, needs the correct tool). */
    public static final DeferredBlock<Block> INFERNAL_NETHERITE_BLOCK = BLOCKS.registerSimpleBlock("infernal_netherite_block",
            BlockBehaviour.Properties.of().strength(5.0F, 5.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());

    /** Data-driven banner pattern {@code data/permadeath/banner_pattern/wither_emperor.json}. */
    public static final ResourceKey<BannerPattern> WITHER_EMPEROR_PATTERN =
            ResourceKey.create(Registries.BANNER_PATTERN, ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, "wither_emperor"));

    private ModBlocks() {
    }
}
