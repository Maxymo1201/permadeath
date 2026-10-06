package com.serthekiller.permadeath.registry;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.items.CustomArmorItem;
import com.serthekiller.permadeath.items.EnchantedItem;
import com.serthekiller.permadeath.items.HyperGoldenApplePlus;
import com.serthekiller.permadeath.items.SuperGoldenApplePlus;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Item registry. Every id is identical to the Fabric mod so existing inventories/chests keep their items.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PermadeathMod.MOD_ID);

    private static final FoodProperties APPLE_FOOD = new FoodProperties.Builder().nutrition(4).saturationModifier(1.2F).alwaysEdible().build();

    public static final DeferredItem<Item> SUPER_GOLDEN_APPLE_PLUS = ITEMS.registerItem("super_golden_apple_plus",
            SuperGoldenApplePlus::new, new Item.Properties().food(APPLE_FOOD).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> HYPER_GOLDEN_APPLE_PLUS = ITEMS.registerItem("hyper_golden_apple_plus",
            HyperGoldenApplePlus::new, new Item.Properties().food(APPLE_FOOD).rarity(Rarity.RARE));
    public static final DeferredItem<Item> END_RELIC = ITEMS.registerItem("end_relic", EnchantedItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> BEGINNING_RELIC = ITEMS.registerItem("beginning_relic", EnchantedItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> LIFE_ORB = ITEMS.registerItem("life_orb", EnchantedItem::new, new Item.Properties().rarity(Rarity.COMMON));

    public static final DeferredItem<BlockItem> INFERNAL_NETHERITE_BLOCK = ITEMS.registerSimpleBlockItem("infernal_netherite_block",
            ModBlocks.INFERNAL_NETHERITE_BLOCK, new Item.Properties().stacksTo(1));

    // ---- armors (bonus health in HP when the full set of the same bonus is worn) --------------------
    public static final DeferredItem<Item> NETHERITE_HELMET = armor("netherite_helmet", ModArmorMaterials.NETHERITE, ArmorItem.Type.HELMET, 8.0);
    public static final DeferredItem<Item> NETHERITE_CHESTPLATE = armor("netherite_chestplate", ModArmorMaterials.NETHERITE, ArmorItem.Type.CHESTPLATE, 8.0);
    public static final DeferredItem<Item> NETHERITE_LEGGINGS = armor("netherite_leggings", ModArmorMaterials.NETHERITE, ArmorItem.Type.LEGGINGS, 8.0);
    public static final DeferredItem<Item> NETHERITE_BOOTS = armor("netherite_boots", ModArmorMaterials.NETHERITE, ArmorItem.Type.BOOTS, 8.0);

    public static final DeferredItem<Item> INFERNAL_NETHERITE_HELMET = armor("infernal_netherite_helmet", ModArmorMaterials.INFERNAL_NETHERITE, ArmorItem.Type.HELMET, 10.0);
    public static final DeferredItem<Item> INFERNAL_NETHERITE_CHESTPLATE = armor("infernal_netherite_chestplate", ModArmorMaterials.INFERNAL_NETHERITE, ArmorItem.Type.CHESTPLATE, 10.0);
    public static final DeferredItem<Item> INFERNAL_NETHERITE_LEGGINGS = armor("infernal_netherite_leggings", ModArmorMaterials.INFERNAL_NETHERITE, ArmorItem.Type.LEGGINGS, 10.0);
    public static final DeferredItem<Item> INFERNAL_NETHERITE_BOOTS = armor("infernal_netherite_boots", ModArmorMaterials.INFERNAL_NETHERITE, ArmorItem.Type.BOOTS, 10.0);

    public static final DeferredItem<Item> DEMONIC_NETHERITE_HELMET = armor("demonic_netherite_helmet", ModArmorMaterials.DEMONIC_NETHERITE, ArmorItem.Type.HELMET, 0.0);
    public static final DeferredItem<Item> DEMONIC_NETHERITE_CHESTPLATE = armor("demonic_netherite_chestplate", ModArmorMaterials.DEMONIC_NETHERITE, ArmorItem.Type.CHESTPLATE, 0.0);
    public static final DeferredItem<Item> DEMONIC_NETHERITE_LEGGINGS = armor("demonic_netherite_leggings", ModArmorMaterials.DEMONIC_NETHERITE, ArmorItem.Type.LEGGINGS, 0.0);
    public static final DeferredItem<Item> DEMONIC_NETHERITE_BOOTS = armor("demonic_netherite_boots", ModArmorMaterials.DEMONIC_NETHERITE, ArmorItem.Type.BOOTS, 0.0);

    public static final DeferredItem<Item> SCIENTIFIC_NETHERITE_HELMET = armor("scientific_netherite_helmet", ModArmorMaterials.SCIENTIFIC_NETHERITE, ArmorItem.Type.HELMET, 0.0);
    public static final DeferredItem<Item> SCIENTIFIC_NETHERITE_CHESTPLATE = armor("scientific_netherite_chestplate", ModArmorMaterials.SCIENTIFIC_NETHERITE, ArmorItem.Type.CHESTPLATE, 0.0);
    public static final DeferredItem<Item> SCIENTIFIC_NETHERITE_LEGGINGS = armor("scientific_netherite_leggings", ModArmorMaterials.SCIENTIFIC_NETHERITE, ArmorItem.Type.LEGGINGS, 0.0);
    public static final DeferredItem<Item> SCIENTIFIC_NETHERITE_BOOTS = armor("scientific_netherite_boots", ModArmorMaterials.SCIENTIFIC_NETHERITE, ArmorItem.Type.BOOTS, 0.0);

    public static final DeferredItem<Item> BOMB_NETHERITE_HELMET = armor("bomb_netherite_helmet", ModArmorMaterials.BOMB_NETHERITE, ArmorItem.Type.HELMET, 0.0);
    public static final DeferredItem<Item> BOMB_NETHERITE_CHESTPLATE = armor("bomb_netherite_chestplate", ModArmorMaterials.BOMB_NETHERITE, ArmorItem.Type.CHESTPLATE, 0.0);
    public static final DeferredItem<Item> BOMB_NETHERITE_LEGGINGS = armor("bomb_netherite_leggings", ModArmorMaterials.BOMB_NETHERITE, ArmorItem.Type.LEGGINGS, 0.0);
    public static final DeferredItem<Item> BOMB_NETHERITE_BOOTS = armor("bomb_netherite_boots", ModArmorMaterials.BOMB_NETHERITE, ArmorItem.Type.BOOTS, 0.0);

    public static final DeferredItem<Item> PINK_NETHERITE_HELMET = armor("pink_netherite_helmet", ModArmorMaterials.PINK_NETHERITE, ArmorItem.Type.HELMET, 0.0);
    public static final DeferredItem<Item> PINK_NETHERITE_CHESTPLATE = armor("pink_netherite_chestplate", ModArmorMaterials.PINK_NETHERITE, ArmorItem.Type.CHESTPLATE, 0.0);
    public static final DeferredItem<Item> PINK_NETHERITE_LEGGINGS = armor("pink_netherite_leggings", ModArmorMaterials.PINK_NETHERITE, ArmorItem.Type.LEGGINGS, 0.0);
    public static final DeferredItem<Item> PINK_NETHERITE_BOOTS = armor("pink_netherite_boots", ModArmorMaterials.PINK_NETHERITE, ArmorItem.Type.BOOTS, 0.0);

    public static final ArmorSet NETHERITE = new ArmorSet(NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS);
    public static final ArmorSet INFERNAL_NETHERITE = new ArmorSet(INFERNAL_NETHERITE_HELMET, INFERNAL_NETHERITE_CHESTPLATE, INFERNAL_NETHERITE_LEGGINGS, INFERNAL_NETHERITE_BOOTS);
    public static final ArmorSet DEMONIC_NETHERITE = new ArmorSet(DEMONIC_NETHERITE_HELMET, DEMONIC_NETHERITE_CHESTPLATE, DEMONIC_NETHERITE_LEGGINGS, DEMONIC_NETHERITE_BOOTS);
    public static final ArmorSet SCIENTIFIC_NETHERITE = new ArmorSet(SCIENTIFIC_NETHERITE_HELMET, SCIENTIFIC_NETHERITE_CHESTPLATE, SCIENTIFIC_NETHERITE_LEGGINGS, SCIENTIFIC_NETHERITE_BOOTS);
    public static final ArmorSet BOMB_NETHERITE = new ArmorSet(BOMB_NETHERITE_HELMET, BOMB_NETHERITE_CHESTPLATE, BOMB_NETHERITE_LEGGINGS, BOMB_NETHERITE_BOOTS);
    public static final ArmorSet PINK_NETHERITE = new ArmorSet(PINK_NETHERITE_HELMET, PINK_NETHERITE_CHESTPLATE, PINK_NETHERITE_LEGGINGS, PINK_NETHERITE_BOOTS);

    private ModItems() {
    }

    /** The four pieces of one custom armour set. */
    public record ArmorSet(DeferredItem<Item> helmet, DeferredItem<Item> chestplate, DeferredItem<Item> leggings, DeferredItem<Item> boots) {
        public List<DeferredItem<Item>> pieces() {
            return List.of(helmet, chestplate, leggings, boots);
        }

        public boolean contains(net.minecraft.world.item.ItemStack stack) {
            return stack.is(helmet.get()) || stack.is(chestplate.get()) || stack.is(leggings.get()) || stack.is(boots.get());
        }
    }

    private static DeferredItem<Item> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type, double bonusHealth) {
        return ITEMS.registerItem(name, props -> new CustomArmorItem(material, type, bonusHealth, props),
                new Item.Properties().component(DataComponents.UNBREAKABLE, new Unbreakable(true)).fireResistant().stacksTo(1));
    }

    public static List<DeferredItem<Item>> netheriteSet() {
        return NETHERITE.pieces();
    }
}
