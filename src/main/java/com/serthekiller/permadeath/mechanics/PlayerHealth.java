package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.items.CustomArmorItem;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Every max-health modifier of the mod, kept in sync with the state every tick (state based instead of the
 * Fabric phase start/end hooks, so day jumps, rollbacks and reconnections always end in the right state).
 * The modifier ids are the Fabric ones, so existing player data stays consistent.
 */
public final class PlayerHealth {
    public static final ResourceLocation DAY40_PENALTY = id("day40_penalty");
    public static final ResourceLocation DAY60_PENALTY = id("day60_penalty");
    public static final ResourceLocation LIFE_ORB_PENALTY = id("life_orb_penalty");
    /** Leftover id removed by the Fabric D60 handler at phase end; cleaned up if present. */
    public static final ResourceLocation LEGACY_ORB_PENALTY = id("orb_life");
    public static final ResourceLocation ARMOR_BONUS = id("armor_health_bonus");
    public static final ResourceLocation HYPER_APPLE_BONUS = id("permanent_health_boost");

    private PlayerHealth() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PermadeathMod.MOD_ID, path);
    }

    public static void tick(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        int day = Permadeath.day();
        boolean changed = sync(health, DAY40_PENALTY, day >= 40 ? -8.0 : 0.0, true);
        changed |= sync(health, DAY60_PENALTY, day >= 60 ? -8.0 : 0.0, true);
        changed |= sync(health, ARMOR_BONUS, armorBonus(player), true);
        if (health.getModifier(LEGACY_ORB_PENALTY) != null) {
            health.removeModifier(LEGACY_ORB_PENALTY);
            changed = true;
        }
        if (changed && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        if (hasFullInfernalSet(player)) {
            MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance == null || resistance.getDuration() < 20) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false));
            }
        }
    }

    /** @return true if the attribute changed. {@code amount == 0} removes the modifier. */
    public static boolean sync(AttributeInstance attribute, ResourceLocation id, double amount, boolean permanent) {
        AttributeModifier current = attribute.getModifier(id);
        if (amount == 0.0) {
            if (current != null) {
                attribute.removeModifier(id);
                return true;
            }
            return false;
        }
        if (current != null && current.amount() == amount) {
            return false;
        }
        if (current != null) {
            attribute.removeModifier(id);
        }
        AttributeModifier modifier = new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
        if (permanent) {
            attribute.addPermanentModifier(modifier);
        } else {
            attribute.addTransientModifier(modifier);
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ armour

    /** Full set of custom armour pieces with the same bonus (Fabric ArmorBonusHandler). */
    static double armorBonus(ServerPlayer player) {
        int pieces = 0;
        double first = 0.0;
        boolean hasFirst = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = player.getItemBySlot(slot);
            double bonus;
            if (stack.getItem() instanceof CustomArmorItem armor) {
                bonus = armor.getBonusHealth();
            } else if (slot == EquipmentSlot.CHEST && isArmoredElytra(stack)) {
                bonus = 10.0;
            } else {
                continue;
            }
            pieces++;
            // A first piece with no bonus must not let the next pieces start a new set.
            if (!hasFirst) {
                first = bonus;
                hasFirst = true;
            } else if (first != bonus) {
                return 0.0;
            }
        }
        return pieces == 4 ? first : 0.0;
    }

    public static boolean isArmoredElytra(ItemStack stack) {
        if (!stack.is(Items.ELYTRA)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains("permadeath") && tag.getCompound("permadeath").getBoolean("armored_elytra");
    }

    private static boolean hasFullInfernalSet(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.INFERNAL_NETHERITE_HELMET.get())
                && (chest.is(ModItems.INFERNAL_NETHERITE_CHESTPLATE.get()) || isArmoredElytra(chest))
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.INFERNAL_NETHERITE_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.INFERNAL_NETHERITE_BOOTS.get());
    }

    // ------------------------------------------------------------------------------------------------ apples

    /** Re-applies the Hyper Golden Apple + bonus (4 HP per apple eaten) to a respawned player. */
    public static void applyHyperAppleBonus(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null || !Permadeath.isRunning()) {
            return;
        }
        int consumed = Permadeath.state().hyperApplesConsumed.getOrDefault(player.getUUID(), 0);
        sync(health, HYPER_APPLE_BONUS, consumed * 4.0, true);
    }
}
