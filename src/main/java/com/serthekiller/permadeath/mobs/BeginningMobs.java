package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/** Mobs of The Beginning (D50+): Wither Skeleton Rosáceo, Ghast Definitivo and Vex Definitivo. */
public final class BeginningMobs {
    public static final String VEX_HONEY_TAG = "permadeath_vex_honey_head";

    private BeginningMobs() {
    }

    /** Netherite sword Sharpness 25, triple health, pink netherite chestplate/boots (once per skeleton). */
    public static void pinkWitherSkeleton(WitherSkeleton skeleton, ServerLevel level) {
        if (!MobTracking.tryClaim(skeleton, "pink_wither_skeleton")) {
            return;
        }
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.NETHERITE_SWORD), ench(Enchantments.SHARPNESS, 25)));
        MobUtil.multiplyMaxHealth(skeleton, 3.0);
        skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.PINK_NETHERITE_CHESTPLATE.get()));
        skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.PINK_NETHERITE_BOOTS.get()));
        skeleton.setCustomName(Component.literal("§6Wither Skeleton Rosáceo"));
    }

    /**
     * Natural vexes only spawn in The Beginning (its biome is the only one listing them, plugin "Vex Definitivo"
     * spawn); vanilla registers no placement for vexes, so they get the usual monster rules on the ground.
     */
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(EntityType.VEX, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Unnamed ghasts of The Beginning are replaced by a Ghast Definitivo 5 blocks higher (plugin). */
    public static boolean replaceGhast(Ghast ghast, ServerLevel level) {
        if (ghast.hasCustomName()) {
            return false;
        }
        EnderMobs.spawnEnderGhast(level, ghast.getX(), ghast.getY() + 5.0, ghast.getZ());
        ghast.discard();
        return true;
    }

    /** Unnamed vexes become "Vex Definitivo": End crystal with Knockback XX and a honey block "head". */
    public static void vexDefinitivo(Vex vex, ServerLevel level) {
        if (vex.hasCustomName()) {
            return;
        }
        vex.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.END_CRYSTAL), ench(Enchantments.KNOCKBACK, 20)));
        vex.setCustomName(Component.literal("§6Vex Definitivo"));
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setPos(vex.getX(), vex.getY(), vex.getZ());
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:honey_block");
        item.putInt("count", 1);
        tag.put("item", item);
        tag.putString("item_display", "head");
        tag.putString("billboard", "vertical");
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0F, -0.15F, 0.0F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("scale", floats(0.55F, 0.55F, 0.55F));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put("transformation", transformation);
        display.load(tag);
        display.addTag(VEX_HONEY_TAG);
        level.addFreshEntity(display);
        display.startRiding(vex, true);
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    /** Removes honey heads whose vex is gone (Fabric END_WORLD_TICK every 20 ticks). */
    public static void cleanupHoneyHeads(ServerLevel level) {
        for (Display.ItemDisplay display : level.getEntities(EntityType.ITEM_DISPLAY, d -> d.getTags().contains(VEX_HONEY_TAG))) {
            Entity vehicle = display.getVehicle();
            if (vehicle == null || vehicle.isRemoved() || !vehicle.isAlive()) {
                display.discard();
            }
        }
    }

    /** On the death of a Vex Definitivo its display passengers are removed. */
    public static void onVexDeath(Entity entity) {
        if (entity instanceof Vex vex && MobUtil.nameContains(vex, "Vex Definitivo")) {
            for (Entity passenger : vex.getPassengers()) {
                if (passenger instanceof Display.ItemDisplay) {
                    passenger.discard();
                }
            }
        }
    }
}
