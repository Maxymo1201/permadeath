package com.serthekiller.permadeath.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

/** Configuration of {@code permadeath:beginning_coral}: the structure templates to choose from. */
public record BeginningCoralConfiguration(List<ResourceLocation> structures) implements FeatureConfiguration {
    public static final Codec<BeginningCoralConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("structures").forGetter(BeginningCoralConfiguration::structures)
    ).apply(instance, BeginningCoralConfiguration::new));
}
