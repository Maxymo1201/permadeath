package com.serthekiller.permadeath.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * D40+: hostile mobs also appear in mushroom fields at night (Fabric MushroomSpawn). Every 300 ticks (the
 * Fabric check required the game time to be a multiple of both 60 and 100), two attempts per player.
 */
public final class MushroomSpawn {
    private static boolean enabled;

    private MushroomSpawn() {
    }

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void tick(ServerLevel level) {
        if (!enabled || level.getGameTime() % 300L != 0L) {
            return;
        }
        long time = level.getDayTime() % 24000L;
        if (time < 13000L || time > 23000L) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (level.getBiome(player.blockPosition()).is(Biomes.MUSHROOM_FIELDS)) {
                spawnNear(player, level);
            }
        }
    }

    private static void spawnNear(ServerPlayer player, ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            BlockPos origin = player.blockPosition();
            int x = origin.getX() + level.random.nextInt(40) - 20;
            int z = origin.getZ() + level.random.nextInt(40) - 20;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.sqrt(player.distanceToSqr(x, y, z)) < 5.0 || !level.getBlockState(pos.below()).isSolid() || !level.getBlockState(pos).isAir()) {
                continue;
            }
            Mob mob = switch (level.random.nextInt(5)) {
                case 0 -> new Zombie(EntityType.ZOMBIE, level);
                case 1 -> {
                    Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
                    skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    yield skeleton;
                }
                case 2 -> new Spider(EntityType.SPIDER, level);
                case 3 -> new Creeper(EntityType.CREEPER, level);
                default -> new EnderMan(EntityType.ENDERMAN, level);
            };
            mob.setPos(x + 0.5, y, z + 0.5);
            level.addFreshEntity(mob);
        }
    }
}
