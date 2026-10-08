package com.serthekiller.permadeath.recipes;

import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Extra ingredient consumption of the special recipes (Fabric ResultSlotMixin). ItemCraftedEvent is fired
 * once per crafted item, before vanilla removes one item from every slot, so "shrink 7" leaves vanilla's own
 * removal of 1 to complete the 8.
 *
 * <p>Fix: when the grid no longer matches any recipe after the extra consumption, vanilla treats every
 * remaining stack as a "crafting remainder" and adds it to itself (the same ItemStack object), duplicating it
 * (reachable in Fabric with a Beginning Relic whose edge stacks differ, e.g. 32 and 64 diamond blocks). In
 * that case the surplus above one item per slot is moved to the player's inventory first.</p>
 */
public final class CraftingCost {
    private CraftingCost() {
    }

    /** The recipes whose extra ingredients are taken here (only by hand: the Crafter is refused them). */
    public static boolean hasExtraCost(Recipe<?> recipe) {
        return recipe instanceof PermadeathRecipes.HyperApple || recipe instanceof PermadeathRecipes.SuperApple
                || recipe instanceof PermadeathRecipes.BeginningRelic || recipe instanceof PermadeathRecipes.LifeOrb;
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack result = event.getCrafting();
        Container grid = event.getInventory();
        if (grid.getContainerSize() != 9) {
            return;
        }
        boolean handled = true;
        if (result.is(ModItems.HYPER_GOLDEN_APPLE_PLUS.get())) {
            for (int i = 0; i < 9; i++) {
                if (i != 4) {
                    grid.getItem(i).shrink(7);
                } else if (Permadeath.day() >= 60) {
                    grid.getItem(i).shrink(63);
                }
            }
        } else if (result.is(ModItems.SUPER_GOLDEN_APPLE_PLUS.get())) {
            for (int i = 0; i < 9; i++) {
                if (i != 4) {
                    grid.getItem(i).shrink(7);
                }
            }
        } else if (result.is(ModItems.BEGINNING_RELIC.get())) {
            for (int slot : new int[]{1, 3, 5, 7}) {
                grid.getItem(slot).shrink(31);
            }
        } else if (result.is(ModItems.LIFE_ORB.get())) {
            for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) {
                grid.getItem(slot).shrink(63);
            }
        } else {
            handled = false;
        }
        if (handled && grid instanceof CraftingContainer crafting) {
            preventRemainderDuplication(player, crafting);
        }
    }

    private static void preventRemainderDuplication(ServerPlayer player, CraftingContainer grid) {
        boolean stillMatches = player.serverLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), player.serverLevel()).isPresent();
        if (stillMatches) {
            return;
        }
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.getCount() > 1) {
                ItemStack surplus = stack.split(stack.getCount() - 1);
                if (!player.getInventory().add(surplus)) {
                    player.drop(surplus, false);
                }
            }
        }
    }
}
