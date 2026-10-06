package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.core.component.DataComponents;
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
    private static final int DEMONIC_ARROW_COLOR = 8454016;
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

    /** D20-29 spider jockeys (Fabric Day20to29Handler#addSkeletonRider). */
    public static void addD20Rider(Entity spider, ServerLevel level) {
        int type = level.random.nextInt(5);
        AbstractSkeleton rider;
        switch (type) {
            case 0 -> {
                Skeleton s = new Skeleton(EntityType.SKELETON, level);
                armor(s, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                MobUtil.setMaxHealth(s, 20.0);
                rider = s;
            }
            case 1 -> {
                WitherSkeleton s = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
                s.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.PUNCH, 20)));
                armor(s, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
                MobUtil.setMaxHealth(s, 40.0);
                rider = s;
            }
            case 2 -> {
                Skeleton s = new Skeleton(EntityType.SKELETON, level);
                s.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.IRON_AXE), ench(Enchantments.FIRE_ASPECT, 2)));
                armor(s, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                MobUtil.setMaxHealth(s, 20.0);
                rider = s;
            }
            case 3 -> {
                Skeleton s = new Skeleton(EntityType.SKELETON, level);
                s.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.CROSSBOW), ench(Enchantments.SHARPNESS, 20)));
                armor(s, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
                MobUtil.setMaxHealth(s, 40.0);
                rider = s;
            }
            default -> {
                WitherSkeleton s = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
                s.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.POWER, 10)));
                leatherArmor(s);
                MobUtil.setMaxHealth(s, 40.0);
                rider = s;
            }
        }
        MobTracking.markProcessed(rider, CLASSED_KEY);
        rider.setPos(spider.getX(), spider.getY(), spider.getZ());
        level.addFreshEntity(rider);
        rider.startRiding(spider);
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
        boolean fresh = vehicle != null && !skeleton.isAddedToWorld();
        if (tier == Tier.D60) {
            int roll = level.random.nextInt(99);
            if (roll < 14) {
                warrior(skeleton, level, actualVehicle, fresh, tier);
            } else if (roll < 28) {
                tactical(skeleton, level, actualVehicle, tier);
            } else if (roll < 42) {
                infernal(skeleton, level, actualVehicle, fresh, tier);
            } else if (roll < 56) {
                assassin(skeleton, level, actualVehicle, fresh, tier);
            } else if (roll < 70) {
                nightmare(skeleton, level, actualVehicle, tier);
            } else if (roll < 84) {
                scientific(skeleton, level, actualVehicle, fresh);
            } else if (roll < 98) {
                demonic(skeleton, level, actualVehicle, fresh);
            } else {
                definitive(skeleton, level, vehicle);
            }
            return;
        }
        switch (level.random.nextInt(5)) {
            case 0 -> warrior(skeleton, level, actualVehicle, fresh, tier);
            case 1 -> tactical(skeleton, level, actualVehicle, tier);
            case 2 -> infernal(skeleton, level, actualVehicle, fresh, tier);
            case 3 -> assassin(skeleton, level, actualVehicle, fresh, tier);
            default -> nightmare(skeleton, level, actualVehicle, tier);
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
        skeleton.setCustomName(net.minecraft.network.chat.Component.literal(name("Guerrero", tier)));
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
        wither.setCustomName(net.minecraft.network.chat.Component.literal(name("Táctico", tier)));
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
        skeleton.setCustomName(net.minecraft.network.chat.Component.literal(name("Infernal", tier)));
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
        skeleton.setCustomName(net.minecraft.network.chat.Component.literal(name("Asesino", tier)));
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
        MobUtil.setMaxHealth(wither, 40.0);
        wither.setCustomName(net.minecraft.network.chat.Component.literal(name("Pesadilla", tier)));
        spawnReplacement(wither, skeleton, level, vehicle, tier != Tier.D30);
    }

    private static void scientific(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh) {
        armorSet(skeleton, ModItems.SCIENTIFIC_NETHERITE);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
        arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(SCIENTIFIC_ARROW_COLOR), List.of(
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 360, 2),
                new MobEffectInstance(MobEffects.WEAKNESS, 360, 0),
                new MobEffectInstance(MobEffects.GLOWING, 360, 0),
                new MobEffectInstance(MobEffects.POISON, 360, 2))));
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, arrow);
        MobUtil.setMaxHealth(skeleton, 100.0);
        skeleton.setCustomName(net.minecraft.network.chat.Component.literal("§6Ultra Esqueleto Científico"));
        mount(skeleton, level, vehicle, fresh);
    }

    private static void demonic(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle, boolean fresh) {
        armorSet(skeleton, ModItems.DEMONIC_NETHERITE);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, MobUtil.harmingArrow(1, DEMONIC_ARROW_COLOR));
        skeleton.addTag(DEMONIC_SKELETON_TAG);
        skeleton.setCustomName(net.minecraft.network.chat.Component.literal("§6Ultra Esqueleto Demoníaco"));
        MobUtil.setMaxHealth(skeleton, 100.0);
        mount(skeleton, level, vehicle, fresh);
    }

    private static void definitive(Skeleton skeleton, ServerLevel level, @Nullable Entity vehicle) {
        WitherSkeleton wither = replaceWithWitherSkeleton(skeleton, level);
        // Power 32765 in the jar; enchantment levels are capped at 255 by ItemEnchantments (same as Fabric).
        wither.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.POWER, 32765)));
        MobUtil.setMaxHealth(wither, 400.0);
        wither.setCustomName(net.minecraft.network.chat.Component.literal("§6Ultra Esqueleto Definitivo"));
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
        if (original.isAddedToWorld()) {
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
