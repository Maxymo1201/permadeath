package com.serthekiller.permadeath.items;

import com.serthekiller.permadeath.PermadeathMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** "Elytra de Netherite Infernal" (Fabric ModArmorMaterials#createArmoredElytraStack; the D60 recipe builds the same stack). */
public final class ArmoredElytra {
    private ArmoredElytra() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.ELYTRA);
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(id("armored_elytra_armor"), 8.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id("armored_elytra_toughness"), 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id("armored_elytra_kb"), 0.1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        CompoundTag inner = new CompoundTag();
        inner.putBoolean("armored_elytra", true);
        CompoundTag tag = new CompoundTag();
        tag.put("permadeath", inner);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME, Component.literal("Elytra de Netherite Infernal").withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, path);
    }
}
