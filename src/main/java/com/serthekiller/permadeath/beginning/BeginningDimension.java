package com.serthekiller.permadeath.beginning;

import com.serthekiller.permadeath.PermadeathMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

/**
 * The Beginning: data-driven dimension ({@code data/permadeath/dimension/the_beginning.json}) with its own
 * dimension type, noise settings and structures. Same keys as the Fabric mod, so existing worlds keep it.
 */
public final class BeginningDimension {
    public static final ResourceKey<Level> LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, "the_beginning"));
    public static final ResourceKey<DimensionType> TYPE_KEY = ResourceKey.create(Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, "the_beginning_type"));

    private BeginningDimension() {
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL_KEY);
    }

    public static boolean is(Level level) {
        return level.dimension() == LEVEL_KEY;
    }
}
