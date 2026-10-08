package com.serthekiller.permadeath.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Small helpers shared by the custom mobs (all values come from the Fabric jar). */
public final class MobUtil {
    /** Infinite effect duration (MobEffectInstance.INFINITE_DURATION). */
    public static final int INFINITE = MobEffectInstance.INFINITE_DURATION;
    /** Colour of the Harming tipped arrows used by the skeleton classes (0x430A09). */
    public static final int HARMING_COLOR = 4393481;

    private MobUtil() {
    }

    public static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static void setMaxHealth(LivingEntity entity, double value) {
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
        if (instance != null) {
            instance.setBaseValue(value);
            entity.setHealth((float) value);
        }
    }

    public static void multiplyMaxHealth(LivingEntity entity, double factor) {
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
            entity.setHealth(entity.getMaxHealth());
        }
    }

    public static void effect(LivingEntity entity, Holder<MobEffect> effect, int duration, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    public static void effect(LivingEntity entity, Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) {
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, visible));
    }

    public static Optional<Holder.Reference<Enchantment>> enchantment(Level level, ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
    }

    /** Sets (replaces) the enchantments of a stack; levels are clamped to 255 by vanilla exactly like in Fabric. */
    public static ItemStack enchanted(Level level, ItemStack stack, EnchantmentLevel... levels) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        for (EnchantmentLevel l : levels) {
            enchantment(level, l.key()).ifPresent(holder -> mutable.set(holder, l.level()));
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    public record EnchantmentLevel(ResourceKey<Enchantment> key, int level) {
    }

    public static EnchantmentLevel ench(ResourceKey<Enchantment> key, int level) {
        return new EnchantmentLevel(key, level);
    }

    /** Tipped arrow with a 1-tick Instant Damage effect (amplifier as given) and a custom colour. */
    public static ItemStack harmingArrow(int amplifier, int color) {
        ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
        arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(color),
                List.of(new MobEffectInstance(MobEffects.HARM, 1, amplifier))));
        return arrow;
    }

    public static ItemStack dyed(ItemStack stack, int color) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        return stack;
    }

    public static ItemStack unbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        return stack;
    }

    public static void equipArmor(Mob mob, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
        mob.setItemSlot(EquipmentSlot.HEAD, head);
        mob.setItemSlot(EquipmentSlot.CHEST, chest);
        mob.setItemSlot(EquipmentSlot.LEGS, legs);
        mob.setItemSlot(EquipmentSlot.FEET, feet);
    }

    /** Drop chance of the four armour slots and of both hands. */
    public static void dropChances(Mob mob, float armor, float hands) {
        mob.setDropChance(EquipmentSlot.HEAD, armor);
        mob.setDropChance(EquipmentSlot.CHEST, armor);
        mob.setDropChance(EquipmentSlot.LEGS, armor);
        mob.setDropChance(EquipmentSlot.FEET, armor);
        mob.setDropChance(EquipmentSlot.MAINHAND, hands);
        mob.setDropChance(EquipmentSlot.OFFHAND, hands);
    }

    public static void name(LivingEntity entity, String legacyFormatted) {
        entity.setCustomName(Component.literal(legacyFormatted));
        entity.setCustomNameVisible(false);
    }

    public static boolean nameContains(LivingEntity entity, String text) {
        return entity.hasCustomName() && entity.getCustomName() != null && entity.getCustomName().getString().contains(text);
    }

    public static boolean spawn(ServerLevel level, Entity entity) {
        return level.addFreshEntity(entity);
    }
}
