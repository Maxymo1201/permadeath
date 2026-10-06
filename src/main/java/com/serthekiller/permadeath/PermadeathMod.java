package com.serthekiller.permadeath;

import com.mojang.logging.LogUtils;
import com.serthekiller.permadeath.event.PermadeathEvents;
import com.serthekiller.permadeath.items.NetheriteTools;
import com.serthekiller.permadeath.loot.D60LootModifier;
import com.serthekiller.permadeath.recipes.PermadeathRecipes;
import com.serthekiller.permadeath.registry.DayConditions;
import com.serthekiller.permadeath.registry.ModArmorMaterials;
import com.serthekiller.permadeath.registry.ModBlocks;
import com.serthekiller.permadeath.registry.ModCreativeTabs;
import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.registry.ModSounds;
import com.serthekiller.permadeath.worldgen.ModWorldgen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Permadeath for NeoForge 1.21.1. The calendar identity (GAME60 / REAL30) is fixed by the jar
 * ({@link BuildProfile}); everything else is shared by both builds.
 */
@Mod(PermadeathMod.MOD_ID)
public final class PermadeathMod {
    public static final String MOD_ID = "permadeath";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PermadeathMod(IEventBus modBus) {
        LOGGER.info("[Permadeath] Loading Permadeath {} (calendar {})", java.util.Objects.requireNonNullElse(PermadeathMod.class.getPackage().getImplementationVersion(), "dev"),
                BuildProfile.mode());
        ModArmorMaterials.ARMOR_MATERIALS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        PermadeathRecipes.SERIALIZERS.register(modBus);
        DayConditions.CONDITIONS.register(modBus);
        D60LootModifier.SERIALIZERS.register(modBus);
        ModWorldgen.register(modBus);
        modBus.addListener(ModCreativeTabs::onBuildContents);
        modBus.addListener(NetheriteTools::onModifyDefaultComponents);
        PermadeathEvents.register(NeoForge.EVENT_BUS);
    }
}
