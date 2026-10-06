package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;

/**
 * D40-59 pigman jockeys (Fabric handleClassPigman): 31% of the zombified piglins get a mount and a class.
 */
public final class PigmanClasses {
    public static final String GHAST_MOUNT_TAG = "ghast_pig_mount";
    public static final String GHAST_PIGMAN_TAG = "ghast_pig";
    public static final String MAGMA_PIGMAN_TAG = "magma_pig";
    public static final String MAGMA_MOUNT_TAG = "magma_pig_mount";

    private PigmanClasses() {
    }

    /**
     * @param d50 true for the D50-59 numbers (yellow attack 6, ghast attack 4, magma pigman at 1 HP without
     *            armour), false for D40-49 (yellow 12, ghast 8, magma pigman max health 1).
     */
    public static void handleClassPigman(LivingEntity entity, ServerLevel level, boolean d50) {
        if (!(entity instanceof ZombifiedPiglin pigman)) {
            return;
        }
        String outcomeKey = d50 ? "pigman_outcome_d50_59" : "pigman_outcome_d40_49";
        if (pigman.getTags().contains(SpecialMobs.PROCESSED_STACK) || pigman.isPassenger()
                || MobTracking.isProcessed(pigman, outcomeKey) || pigman.getTags().contains(SpecialMobs.CARLOS_PIGMAN)) {
            return;
        }
        if (pigman.getRandom().nextInt(100) > 30) {
            return;
        }
        MobTracking.markProcessed(pigman, outcomeKey);
        Mob mount;
        String classTag;
        String customName;
        switch (pigman.getRandom().nextInt(4)) {
            case 0 -> {
                mount = new Pig(EntityType.PIG, level);
                mount.setCustomName(Component.literal("Tony el cerdo").withStyle(ChatFormatting.DARK_PURPLE));
                classTag = "pink_pig";
                customName = "Pink Pigman";
                MobUtil.equipArmor(pigman, new ItemStack(ModItems.PINK_NETHERITE_HELMET.get()), new ItemStack(ModItems.PINK_NETHERITE_CHESTPLATE.get()),
                        new ItemStack(ModItems.PINK_NETHERITE_LEGGINGS.get()), new ItemStack(ModItems.PINK_NETHERITE_BOOTS.get()));
                MobUtil.setBase(pigman, Attributes.ATTACK_DAMAGE, 20.0);
            }
            case 1 -> {
                mount = new Bee(EntityType.BEE, level);
                var maxHealth = mount.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealth != null && maxHealth.getBaseValue() <= 26.0) {
                    maxHealth.setBaseValue(100.0);
                    mount.setHealth(100.0F);
                }
                classTag = "yellow_pig";
                customName = "Yellow Pigman";
                MobUtil.equipArmor(pigman, new ItemStack(ModItems.BOMB_NETHERITE_HELMET.get()), new ItemStack(ModItems.BOMB_NETHERITE_CHESTPLATE.get()),
                        new ItemStack(ModItems.BOMB_NETHERITE_LEGGINGS.get()), new ItemStack(ModItems.BOMB_NETHERITE_BOOTS.get()));
                MobUtil.setBase(pigman, Attributes.ATTACK_DAMAGE, d50 ? 6.0 : 12.0);
            }
            case 2 -> {
                mount = new Ghast(EntityType.GHAST, level);
                mount.setCustomName(Component.literal("ghast feliz"));
                mount.addTag(GHAST_MOUNT_TAG);
                // Fabric left the class tag empty, so its "ghast_pig" damage hook never ran; the tag is set now.
                classTag = GHAST_PIGMAN_TAG;
                customName = "";
                MobUtil.setBase(pigman, Attributes.ATTACK_DAMAGE, d50 ? 4.0 : 8.0);
                pigman.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 3, false, true));
                clearArmor(pigman);
            }
            default -> {
                MagmaCube magma = new MagmaCube(EntityType.MAGMA_CUBE, level);
                EventHooks.finalizeMobSpawn(magma, level, level.getCurrentDifficultyAt(pigman.blockPosition()), MobSpawnType.EVENT, null);
                magma.setSize(1, true);
                magma.setHealth(1.0F);
                magma.setCustomName(Component.literal("Mini"));
                magma.addTag(MAGMA_MOUNT_TAG);
                mount = magma;
                classTag = MAGMA_PIGMAN_TAG;
                customName = "Magma Pigman";
                pigman.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.harmingArrow(1, MobUtil.HARMING_COLOR));
                if (d50) {
                    pigman.setHealth(1.0F);
                    clearArmor(pigman);
                } else {
                    MobUtil.setBase(pigman, Attributes.MAX_HEALTH, 1.0);
                }
            }
        }
        mount.moveTo(pigman.getX(), pigman.getY(), pigman.getZ(), pigman.getYRot(), 0.0F);
        if (mount instanceof MagmaCube magma) {
            magma.setSize(1, true);
            magma.setHealth(1.0F);
        } else {
            EventHooks.finalizeMobSpawn(mount, level, level.getCurrentDifficultyAt(pigman.blockPosition()), MobSpawnType.EVENT, null);
        }
        level.addFreshEntity(mount);
        pigman.startRiding(mount);
        pigman.addTag(classTag);
        // The ghast rider gets an empty name, exactly like Fabric (it still makes the pigman persistent).
        pigman.setCustomName(Component.literal(customName).withStyle(ChatFormatting.GOLD));
        pigman.setCustomNameVisible(false);
    }

    private static void clearArmor(ZombifiedPiglin pigman) {
        MobUtil.equipArmor(pigman, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    public static boolean isGhastMount(LivingEntity entity) {
        return entity instanceof Ghast && entity.getTags().contains(GHAST_MOUNT_TAG);
    }
}
