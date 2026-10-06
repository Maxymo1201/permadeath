package com.serthekiller.permadeath.registry;

import com.serthekiller.permadeath.PermadeathMod;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * Armor materials of the Fabric mod, same ids ({@code permadeath:<material>}) and values:
 * helmet 3, chestplate 8, leggings 6, boots 3, enchantability 15, toughness 3, knockback resistance 0.1,
 * repaired with netherite ingots. Layer textures: {@code permadeath:textures/models/armor/<material>_layer_N.png}.
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, PermadeathMod.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> NETHERITE = material("netherite");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> INFERNAL_NETHERITE = material("infernal_netherite");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DEMONIC_NETHERITE = material("demonic_netherite");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SCIENTIFIC_NETHERITE = material("scientific_netherite");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BOMB_NETHERITE = material("bomb_netherite");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> PINK_NETHERITE = material("pink_netherite");

    private ModArmorMaterials() {
    }

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(String name) {
        return ARMOR_MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                map.put(ArmorItem.Type.BOOTS, 3);
                map.put(ArmorItem.Type.LEGGINGS, 6);
                map.put(ArmorItem.Type.CHESTPLATE, 8);
                map.put(ArmorItem.Type.HELMET, 3);
                map.put(ArmorItem.Type.BODY, 8);
            });
            return new ArmorMaterial(defense, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Items.NETHERITE_INGOT),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, name))), 3.0F, 0.1F);
        });
    }

    public static Holder<ArmorMaterial> holder(DeferredHolder<ArmorMaterial, ArmorMaterial> holder) {
        return holder;
    }
}
