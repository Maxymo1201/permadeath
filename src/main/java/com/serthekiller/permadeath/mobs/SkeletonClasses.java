package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/**
 * Skeleton classes (Fabric Day20..Day60 handlers). Every number (health, enchantment levels, armour, arrows,
 * names and class probabilities) is copied from the jar of the corresponding phase.
 */
public final class SkeletonClasses {
    /** Persistent marker so a skeleton is classed only once (Fabric re-rolled spider riders once more). */
    public static final String CLASSED_KEY = "skeleton_class";
    private static final int LEATHER_COLOR = 11546150;
    private static final int SCIENTIFIC_ARROW_COLOR = 8366264;
    /** Científico arrows: 3 min of each effect (plugin; Fabric 18 s). */
    private static final int SCIENTIFIC_EFFECT_TICKS = 3 * 60 * 20;
    private static final int DEMONIC_ARROW_COLOR = 8454016;
    /** Armour drop chance of the class skeletons (plugin 0.8; leather, custom netherite and D60 sets 0). */
    private static final float ARMOR_DROP = 0.8F;
    public static final String DEMONIC_SKELETON_TAG = "DemonicSkeleton";

    private SkeletonClasses() {
    }

    /** Tier of the class tables. */
    public enum Tier {
        D30("skeleton_class", "insert_effect_d30_39"),
        D40("skeleton_class_d40_49", "insert_effect_d40_49"),
        D50("skeleton_class_d50_59", "insert_effect_d50_59"),
        D60("skeleton_class_d60_69", "insert_effect_d60_69");

        private final String classKey;
        private final String insertKey;

        Tier(String classKey, String insertKey) {
            this.classKey = classKey;
            this.insertKey = insertKey;
        }

        /** Persistent marker of the Fabric handler ("pmd_proc_" + key), kept for world compatibility. */
        public String classKey() {
            return classKey;
        }

        public String insertKey() {
            return insertKey;
        }

        public static Tier forDay(int day) {
            if (day >= 60) {
                return D60;
            }
            if (day >= 50) {
                return D50;
            }
            if (day >= 40) {
                return D40;
            }
            return D30;
        }
    }

    // ------------------------------------------------------------------------------------------- D20-29

    /** D20-29 spider jockeys: a new skeleton with a D20 class rides the spider. */
    public static void addD20Rider(Entity spider, ServerLevel level) {
        Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
        skeleton.setPos(spider.getX(), spider.getY(), spider.getZ());
        applyD20Class(skeleton, level, spider);
    }

    /**
     * D20-29 classes (plugin spawnSkeletonClass, also applied to every natural skeleton - Fabric only classed the
     * spider riders): roll nextInt(6) - 0-1 diamond + bow (20 HP), 2 wither skeleton with chainmail and a Punch XX
     * bow (40 HP), 3 iron + Fire Aspect II iron axe (20 HP), 4 gold + Sharpness XX crossbow (40 HP), 5 wither
     * skeleton with red leather and a Power X bow (40 HP). Armour drops 80 % (leather 0 %), weapons never drop.
     */
    public static void applyD20Class(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle) {
        MobTracking.markProcessed(skeleton, CLASSED_KEY);
        boolean fresh = vehicle != null && !skeleton.isAddedToLevel();
        switch (level.random.nextInt(6)) {
            case 2 -> {
                WitherSkeleton wither = replaceWithWitherSkeleton(skeleton, level);
                wither.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.PUNCH, 20)));
                armor(wither, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
                MobUtil.setMaxHealth(wither, 40.0);
                MobUtil.dropChances(wither, ARMOR_DROP, 0.0F);
                spawnReplacement(wither, skeleton, level, vehicle, true);
            }
            case 3 -> {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.IRON_AXE), ench(Enchantments.FIRE_ASPECT, 2)));
                armor(skeleton, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                MobUtil.setMaxHealth(skeleton, 20.0);
                MobUtil.dropChances(skeleton, ARMOR_DROP, 0.0F);
                mount(skeleton, level, vehicle, fresh);
            }
            case 4 -> {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.CROSSBOW), ench(Enchantments.SHARPNESS, 20)));
                armor(skeleton, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
                MobUtil.setMaxHealth(skeleton, 40.0);
                MobUtil.dropChances(skeleton, ARMOR_DROP, 0.0F);
                mount(skeleton, level, vehicle, fresh);
            }
            case 5 -> {
                WitherSkeleton wither = replaceWithWitherSkeleton(skeleton, level);
                wither.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.POWER, 10)));
                leatherArmor(wither);
                MobUtil.setMaxHealth(wither, 40.0);
                MobUtil.dropChances(wither, 0.0F, 0.0F);
                spawnReplacement(wither, skeleton, level, vehicle, true);
            }
            default -> {
                armor(skeleton, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                MobUtil.setMaxHealth(skeleton, 20.0);
                MobUtil.dropChances(skeleton, ARMOR_DROP, 0.0F);
                mount(skeleton, level, vehicle, fresh);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- D30+

    /**
     * Applies a random class to {@code skeleton}. When {@code vehicle} is not null the skeleton is a new rider
     * (not yet in the world); otherwise it is an existing skeleton converted in place. Wither skeleton classes
     * replace the skeleton entity.
     */
    public static void applyRandomClass(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, Tier tier) {
        MobTracking.markProcessed(skeleton, CLASSED_KEY);
        MobTracking.markProcessed(skeleton, tier.classKey());
        Entity actualVehicle = vehicle != null ? vehicle : skeleton.getVehicle();
        boolean fresh = vehicle != null && !skeleton.isAddedToLevel();
        // Plugin odds: D60 first 1/101 Definitivo, then nextInt(8) (warrior 2/8, the other six 1/8 each);
        // D30-59 nextInt(6) (warrior 2/6). Fabric used equal odds and 1/99 for the Definitivo.
        if (tier == Tier.D60 && level.random.nextInt(101) == 1) {
            definitive(skeleton, level, vehicle);
            return;
        }
        switch (level.random.nextInt(tier == Tier.D60 ? 8 : 6)) {
            case 2 -> tactical(skeleton, level, actualVehicle, tier);
            case 3 -> infernal(skeleton, level, actualVehicle, fresh, tier);
            case 4 -> assassin(skeleton, level, actualVehicle, fresh, tier);
            case 5 -> nightmare(skeleton, level, actualVehicle, tier);
            case 6 -> demonic(skeleton, level, actualVehicle, fresh);
            case 7 -> scientific(skeleton, level, actualVehicle, fresh);
            default -> warrior(skeleton, level, actualVehicle, fresh, tier);
        }
    }

    /** New rider skeleton with a random class (spiders, cave spiders, phantoms). */
    public static void addRider(Entity vehicle, ServerLevel level, Tier tier) {
        if (tier != Tier.D30 && vehicle.isVehicle()) {
            return;
        }
        Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
        skeleton.setPos(vehicle.getX(), vehicle.getY(), vehicle.getZ());
        applyRandomClass(skeleton, level, vehicle, tier);
    }

    private static String name(String base, Tier tier) {
        return tier == Tier.D60 ? "§6Ultra Esqueleto " + base : "§6Esqueleto " + base;
    }

    private static void warrior(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh, Tier tier) {
        int protection = tier == Tier.D60 ? 5 : 4;
        skeleton.setItemSlot(EquipmentSlot.HEAD, MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_HELMET), ench(Enchantments.PROTECTION, protection)));
        skeleton.setItemSlot(EquipmentSlot.CHEST, MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_CHESTPLATE), ench(Enchantments.PROTECTION, protection)));
        skeleton.setItemSlot(EquipmentSlot.LEGS, MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_LEGGINGS), ench(Enchantments.PROTECTION, protection)));
        skeleton.setItemSlot(EquipmentSlot.FEET, MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_BOOTS), ench(Enchantments.PROTECTION, protection)));
        ItemStack bow = new ItemStack(Items.BOW);
        if (tier == Tier.D60) {
            MobUtil.enchanted(level, bow, ench(Enchantments.POWER, 50));
        }
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, MobUtil.harmingArrow(1, MobUtil.HARMING_COLOR));
        MobUtil.setMaxHealth(skeleton, tier == Tier.D50 || tier == Tier.D60 ? 100.0 : 40.0);
        MobUtil.dropChances(skeleton, tier == Tier.D60 ? 0.0F : ARMOR_DROP, 0.0F);
        skeleton.setCustomName(Component.literal(name("Guerrero", tier)));
        mount(skeleton, level, vehicle, fresh);
    }

    private static void tactical(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, Tier tier) {
        WitherSkeleton wither = replaceWithWitherSkeleton(skeleton, level);
        int punch;
        int power;
        switch (tier) {
            case D60 -> {
                punch = 50;
                power = 110;
            }
            case D50 -> {
                punch = 50;
                power = 40;
            }
            default -> {
                punch = 30;
                power = 25;
            }
        }
        wither.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.PUNCH, punch), ench(Enchantments.POWER, power)));
        armor(wither, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
        wither.setItemSlot(EquipmentSlot.OFFHAND, MobUtil.harmingArrow(1, MobUtil.HARMING_COLOR));
        MobUtil.setMaxHealth(wither, tier == Tier.D60 ? 60.0 : 40.0);
        MobUtil.dropChances(wither, ARMOR_DROP, 0.0F);
        wither.setCustomName(Component.literal(name("Táctico", tier)));
        spawnReplacement(wither, skeleton, level, vehicle, tier != Tier.D30);
    }

    private static void infernal(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh, Tier tier) {
        ItemStack axe = switch (tier) {
            case D60 -> MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_AXE), ench(Enchantments.FIRE_ASPECT, 20), ench(Enchantments.SHARPNESS, 100));
            case D50 -> MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_AXE), ench(Enchantments.FIRE_ASPECT, 20), ench(Enchantments.SHARPNESS, 25));
            default -> MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_AXE), ench(Enchantments.FIRE_ASPECT, 10));
        };
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, axe);
        armor(skeleton, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, MobUtil.harmingArrow(1, MobUtil.HARMING_COLOR));
        MobUtil.setMaxHealth(skeleton, tier == Tier.D60 ? 100.0 : 40.0);
        MobUtil.dropChances(skeleton, ARMOR_DROP, 0.0F);
        skeleton.setCustomName(Component.literal(name("Infernal", tier)));
        mount(skeleton, level, vehicle, fresh);
    }

    private static void assassin(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh, Tier tier) {
        int sharpness = switch (tier) {
            case D60 -> 100;
            case D50 -> 50;
            default -> 25;
        };
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.CROSSBOW), ench(Enchantments.SHARPNESS, sharpness)));
        armor(skeleton, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, MobUtil.harmingArrow(1, MobUtil.HARMING_COLOR));
        skeleton.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, tier == Tier.D60 ? 3 : 1, false, true));
        MobUtil.setMaxHealth(skeleton, tier == Tier.D60 ? 60.0 : 40.0);
        MobUtil.dropChances(skeleton, ARMOR_DROP, 0.0F);
        skeleton.setCustomName(Component.literal(name("Asesino", tier)));
        mount(skeleton, level, vehicle, fresh);
    }

    private static void nightmare(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, Tier tier) {
        WitherSkeleton wither = replaceWithWitherSkeleton(skeleton, level);
        int power = switch (tier) {
            case D60 -> 150;
            case D50 -> 60;
            default -> 50;
        };
        wither.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.POWER, power)));
        leatherArmor(wither);
        wither.setItemSlot(EquipmentSlot.OFFHAND, MobUtil.harmingArrow(1, MobUtil.HARMING_COLOR));
        MobUtil.setMaxHealth(wither, tier == Tier.D60 ? 60.0 : 40.0);
        MobUtil.dropChances(wither, 0.0F, 0.0F);
        wither.setCustomName(Component.literal(name("Pesadilla", tier)));
        spawnReplacement(wither, skeleton, level, vehicle, tier != Tier.D30);
    }

    private static void scientific(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh) {
        armorSet(skeleton, ModItems.SCIENTIFIC_NETHERITE);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
        arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(SCIENTIFIC_ARROW_COLOR), List.of(
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SCIENTIFIC_EFFECT_TICKS, 2),
                new MobEffectInstance(MobEffects.WEAKNESS, SCIENTIFIC_EFFECT_TICKS, 0),
                new MobEffectInstance(MobEffects.GLOWING, SCIENTIFIC_EFFECT_TICKS, 0),
                new MobEffectInstance(MobEffects.POISON, SCIENTIFIC_EFFECT_TICKS, 2))));
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, arrow);
        MobUtil.setMaxHealth(skeleton, 100.0);
        MobUtil.dropChances(skeleton, 0.0F, 0.0F);
        skeleton.setCustomName(Component.literal("§6Ultra Esqueleto Científico"));
        mount(skeleton, level, vehicle, fresh);
    }

    private static void demonic(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh) {
        armorSet(skeleton, ModItems.DEMONIC_NETHERITE);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, MobUtil.harmingArrow(1, DEMONIC_ARROW_COLOR));
        skeleton.addTag(DEMONIC_SKELETON_TAG);
        skeleton.setCustomName(Component.literal("§6Ultra Esqueleto Demoníaco"));
        MobUtil.setMaxHealth(skeleton, 100.0);
        MobUtil.dropChances(skeleton, 0.0F, 0.0F);
        mount(skeleton, level, vehicle, fresh);
    }

    private static void definitive(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle) {
        WitherSkeleton wither = replaceWithWitherSkeleton(skeleton, level);
        // Power 32765 in the jar; enchantment levels are capped at 255 by ItemEnchantments (same as Fabric).
        wither.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.POWER, 32765)));
        MobUtil.setMaxHealth(wither, 400.0);
        MobUtil.dropChances(wither, 0.0F, 0.0F);
        // Plugin: Speed II and never despawns.
        wither.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, true));
        wither.setPersistenceRequired();
        wither.setCustomName(Component.literal("§6Ultra Esqueleto Definitivo"));
        spawnReplacement(wither, skeleton, level, vehicle, true);
    }

    // ------------------------------------------------------------------------------------------- helpers

    private static WitherSkeleton replaceWithWitherSkeleton(Skeleton skeleton, ServerLevel level) {
        WitherSkeleton wither = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
        wither.setPos(skeleton.getX(), skeleton.getY(), skeleton.getZ());
        wither.setYRot(skeleton.getYRot());
        wither.setYHeadRot(skeleton.getYHeadRot());
        MobTracking.markProcessed(wither, CLASSED_KEY);
        return wither;
    }

    private static void spawnReplacement(WitherSkeleton wither, Skeleton original, ServerLevel level, @Nullable Entity vehicle, boolean forceRide) {
        level.addFreshEntity(wither);
        if (vehicle != null) {
            if (original.isPassenger()) {
                original.stopRiding();
            }
            wither.startRiding(vehicle, forceRide);
        }
        if (original.isAddedToLevel()) {
            original.discard();
        }
    }

    private static void mount(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh) {
        if (vehicle != null && fresh) {
            level.addFreshEntity(skeleton);
            skeleton.startRiding(vehicle, true);
        }
    }

    private static void armor(AbstractSkeleton mob, Item head, Item chest, Item legs, Item feet) {
        MobUtil.equipArmor(mob, new ItemStack(head), new ItemStack(chest), new ItemStack(legs), new ItemStack(feet));
    }

    private static void armorSet(AbstractSkeleton mob, ModItems.ArmorSet set) {
        MobUtil.equipArmor(mob, new ItemStack(set.helmet().get()), new ItemStack(set.chestplate().get()),
                new ItemStack(set.leggings().get()), new ItemStack(set.boots().get()));
    }

    private static void leatherArmor(AbstractSkeleton mob) {
        ItemStack helmet = MobUtil.dyed(new ItemStack(Items.LEATHER_HELMET), LEATHER_COLOR);
        MobUtil.unbreakable(helmet);
        MobUtil.equipArmor(mob, helmet,
                MobUtil.dyed(new ItemStack(Items.LEATHER_CHESTPLATE), LEATHER_COLOR),
                MobUtil.dyed(new ItemStack(Items.LEATHER_LEGGINGS), LEATHER_COLOR),
                MobUtil.dyed(new ItemStack(Items.LEATHER_BOOTS), LEATHER_COLOR));
    }
}
