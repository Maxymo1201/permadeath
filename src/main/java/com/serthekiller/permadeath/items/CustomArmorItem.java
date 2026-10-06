package com.serthekiller.permadeath.items;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/** Armor piece that grants extra max health when the whole set (same bonus) is worn. */
public class CustomArmorItem extends ArmorItem {
    private final double bonusHealth;

    public CustomArmorItem(Holder<ArmorMaterial> material, Type type, double bonusHealth, Properties properties) {
        super(material, type, properties);
        this.bonusHealth = bonusHealth;
    }

    public double getBonusHealth() {
        return bonusHealth;
    }
}
