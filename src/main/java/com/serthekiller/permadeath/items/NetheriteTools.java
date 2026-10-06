package com.serthekiller.permadeath.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.List;

/**
 * Netherite tools are unbreakable and have a gold name (Fabric NetheriteUpgradeMixin on ItemStack#getName and
 * #isDamageable), done with default data components instead of a mixin. The unbreakable component hides its
 * tooltip line, like the Fabric version which simply made the tools non damageable.
 */
public final class NetheriteTools {
    public static final List<Item> TOOLS = List.of(Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL,
            Items.NETHERITE_HOE, Items.NETHERITE_SWORD);

    private NetheriteTools() {
    }

    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (Item tool : TOOLS) {
            event.modify(tool, builder -> builder
                    .set(DataComponents.UNBREAKABLE, new Unbreakable(false))
                    .set(DataComponents.ITEM_NAME, Component.translatable(tool.getDescriptionId()).withStyle(ChatFormatting.GOLD)));
        }
    }

    public static boolean isNetheriteTool(ItemStack stack) {
        for (Item tool : TOOLS) {
            if (stack.is(tool)) {
                return true;
            }
        }
        return false;
    }
}
