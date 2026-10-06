package com.serthekiller.permadeath.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Relics and Life Orb (the Fabric class name is kept; they are rendered without glint). */
public class EnchantedItem extends Item {
    public EnchantedItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
