package com.serthekiller.permadeath.registry;

import com.serthekiller.permadeath.items.ArmoredElytra;
import com.serthekiller.permadeath.mechanics.TotemSystem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.List;
import java.util.function.Supplier;

/** Same placement as the Fabric ModItemsGroups: armour sets after netherite boots, relics after the totem, apples after the enchanted golden apple. */
public final class ModCreativeTabs {
    private static final CreativeModeTab.TabVisibility VISIBILITY = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;

    private ModCreativeTabs() {
    }

    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            List<Supplier<? extends ItemLike>> armor = List.of(
                    ModItems.NETHERITE_HELMET, ModItems.NETHERITE_CHESTPLATE, ModItems.NETHERITE_LEGGINGS, ModItems.NETHERITE_BOOTS,
                    ModItems.INFERNAL_NETHERITE_HELMET, ModItems.INFERNAL_NETHERITE_CHESTPLATE, ModItems.INFERNAL_NETHERITE_LEGGINGS, ModItems.INFERNAL_NETHERITE_BOOTS,
                    ModItems.DEMONIC_NETHERITE_HELMET, ModItems.DEMONIC_NETHERITE_CHESTPLATE, ModItems.DEMONIC_NETHERITE_LEGGINGS, ModItems.DEMONIC_NETHERITE_BOOTS,
                    ModItems.SCIENTIFIC_NETHERITE_HELMET, ModItems.SCIENTIFIC_NETHERITE_CHESTPLATE, ModItems.SCIENTIFIC_NETHERITE_LEGGINGS, ModItems.SCIENTIFIC_NETHERITE_BOOTS,
                    ModItems.BOMB_NETHERITE_HELMET, ModItems.BOMB_NETHERITE_CHESTPLATE, ModItems.BOMB_NETHERITE_LEGGINGS, ModItems.BOMB_NETHERITE_BOOTS,
                    ModItems.PINK_NETHERITE_HELMET, ModItems.PINK_NETHERITE_CHESTPLATE, ModItems.PINK_NETHERITE_LEGGINGS, ModItems.PINK_NETHERITE_BOOTS);
            ItemStack previous = new ItemStack(Items.NETHERITE_BOOTS);
            for (Supplier<? extends ItemLike> item : armor) {
                ItemStack stack = new ItemStack(item.get());
                event.insertAfter(previous, stack, VISIBILITY);
                previous = stack;
            }
            ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
            ItemStack medal = TotemSystem.createSurvivorMedal();
            ItemStack endRelic = new ItemStack(ModItems.END_RELIC.get());
            ItemStack beginningRelic = new ItemStack(ModItems.BEGINNING_RELIC.get());
            ItemStack elytra = ArmoredElytra.create();
            ItemStack lifeOrb = new ItemStack(ModItems.LIFE_ORB.get());
            event.insertAfter(totem, endRelic, VISIBILITY);
            event.insertAfter(endRelic, beginningRelic, VISIBILITY);
            event.insertAfter(beginningRelic, elytra, VISIBILITY);
            event.insertAfter(elytra, lifeOrb, VISIBILITY);
            event.insertAfter(lifeOrb, medal, VISIBILITY);
            event.insertAfter(new ItemStack(Items.TNT), new ItemStack(ModItems.INFERNAL_NETHERITE_BLOCK.get()), VISIBILITY);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            ItemStack superApple = new ItemStack(ModItems.SUPER_GOLDEN_APPLE_PLUS.get());
            event.insertAfter(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), superApple, VISIBILITY);
            event.insertAfter(superApple, new ItemStack(ModItems.HYPER_GOLDEN_APPLE_PLUS.get()), VISIBILITY);
        }
    }
}
