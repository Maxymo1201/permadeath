package com.serthekiller.permadeath.recipes;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Vanilla recipes disabled by day (Fabric RecipeManagerMixin): torches from D40, iron/gold ingot smelting and
 * blasting from D50 (replaced by the {@code permadeath:*} nugget recipes). Applied to the server recipe manager
 * after every datapack (re)load - {@code DayController} reloads the datapacks when the day crosses D40/D50/D60 -
 * and before the recipes are sent to the clients.
 */
public final class RecipeFilter {
    private static final Set<ResourceLocation> FROM_D40 = Set.of(
            vanilla("torch"), vanilla("redstone_torch"), vanilla("soul_torch"));
    private static final Set<ResourceLocation> FROM_D50 = Set.of(
            vanilla("iron_ingot_from_smelting_iron_ore"),
            vanilla("iron_ingot_from_smelting_deepslate_iron_ore"),
            vanilla("iron_ingot_from_smelting_raw_iron"),
            vanilla("iron_ingot_from_blasting_iron_ore"),
            vanilla("iron_ingot_from_blasting_deepslate_iron_ore"),
            vanilla("iron_ingot_from_blasting_raw_iron"),
            vanilla("gold_ingot_from_smelting_gold_ore"),
            vanilla("gold_ingot_from_smelting_deepslate_gold_ore"),
            vanilla("gold_ingot_from_smelting_nether_gold_ore"),
            vanilla("gold_ingot_from_smelting_raw_gold"),
            vanilla("gold_ingot_from_blasting_gold_ore"),
            vanilla("gold_ingot_from_blasting_deepslate_gold_ore"),
            vanilla("gold_ingot_from_blasting_nether_gold_ore"),
            vanilla("gold_ingot_from_blasting_raw_gold"));

    private RecipeFilter() {
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    /** Recipe ids removed on {@code day}. */
    public static Set<ResourceLocation> removedOn(int day) {
        if (day >= 50) {
            Set<ResourceLocation> all = new java.util.HashSet<>(FROM_D40);
            all.addAll(FROM_D50);
            return all;
        }
        return day >= 40 ? FROM_D40 : Set.of();
    }

    /** Only the reload broadcast (player == null): on login the already filtered manager is synced as is. */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null || !Permadeath.isRunning()) {
            return;
        }
        apply(event.getPlayerList().getServer().getRecipeManager(), Permadeath.day());
    }

    public static void apply(RecipeManager manager, int day) {
        Set<ResourceLocation> remove = removedOn(day);
        if (remove.isEmpty()) {
            return;
        }
        List<RecipeHolder<?>> kept = new ArrayList<>();
        int removed = 0;
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (remove.contains(holder.id())) {
                removed++;
            } else {
                kept.add(holder);
            }
        }
        if (removed > 0) {
            manager.replaceRecipes(kept);
            PermadeathMod.LOGGER.info("[Permadeath] {} vanilla recipes disabled for day {}", removed, day);
        }
    }
}
