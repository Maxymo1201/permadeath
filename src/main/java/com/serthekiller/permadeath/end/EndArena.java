package com.serthekiller.permadeath.end;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * EndDragonFightMixin. When the End is first visited:
 * <ol>
 *     <li>35% of the end stone of the main island (radius 120) becomes end stone bricks;</li>
 *     <li>four red altars (N/S/E/W, 70 blocks from the centre) with a sea lantern and water bottle frames;</li>
 *     <li>every 3 s each altar throws a red splash potion (Speed III, Regeneration II, Resistance I, 20 s).</li>
 * </ol>
 * Fabric did step 1 synchronously and kept the altars only in memory (after a restart no potion was thrown
 * any more). The work is now spread over 256 ticks (one chunk per tick) and the altars are stored in the world.
 */
public final class EndArena {
    private static final int RADIUS = 120;
    private static final int CHUNK_MIN = -8;
    private static final int CHUNK_MAX = 7;
    private static int jobIndex = -1;
    private static int potionTimer;

    private EndArena() {
    }

    public static void reset() {
        jobIndex = -1;
        potionTimer = 0;
    }

    /** Called every End tick. */
    public static void tick(ServerLevel end) {
        if (!Permadeath.isRunning()) {
            return;
        }
        ProgressionState state = Permadeath.state();
        if (!state.endArenaPrepared) {
            if (jobIndex < 0) {
                if (end.players().isEmpty()) {
                    return;
                }
                jobIndex = 0;
                PermadeathMod.LOGGER.info("[Permadeath] Preparing the End arena...");
            }
            int size = CHUNK_MAX - CHUNK_MIN + 1;
            if (jobIndex < size * size) {
                int cx = CHUNK_MIN + jobIndex / size;
                int cz = CHUNK_MIN + jobIndex % size;
                processChunk(end, cx, cz);
                jobIndex++;
                return;
            }
            state.endAltarPositions = generateAltars(end);
            state.endArenaPrepared = true;
            state.markChanged();
            jobIndex = -1;
            PermadeathMod.LOGGER.info("[Permadeath] End arena prepared ({} altars)", state.endAltarPositions.length);
            return;
        }
        if (++potionTimer >= 60) {
            potionTimer = 0;
            for (long packed : state.endAltarPositions) {
                BlockPos altar = BlockPos.of(packed);
                BlockPos at = altar.above(3);
                if (end.isPositionEntityTicking(at)) {
                    throwPotion(end, at);
                }
            }
        }
    }

    private static void processChunk(ServerLevel end, int cx, int cz) {
        end.getChunk(cx, cz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx * 16; x < cx * 16 + 16; x++) {
            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                if (Math.sqrt(x * x + z * z) > RADIUS) {
                    continue;
                }
                int top = -1;
                for (int y = 128; y >= 0; y--) {
                    pos.set(x, y, z);
                    if (end.getBlockState(pos).is(Blocks.END_STONE)) {
                        top = y;
                        break;
                    }
                }
                if (top < 0) {
                    continue;
                }
                for (int y = top; y >= 0; y--) {
                    pos.set(x, y, z);
                    if (!end.getBlockState(pos).is(Blocks.END_STONE)) {
                        break;
                    }
                    if (end.random.nextFloat() < 0.35F) {
                        end.setBlock(pos, Blocks.END_STONE_BRICKS.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static long[] generateAltars(ServerLevel end) {
        int r = 70;
        String[] names = {"Norte", "Sur", "Este", "Oeste"};
        int[][] offsets = {{0, -r}, {0, r}, {r, 0}, {-r, 0}};
        List<Long> positions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int x = offsets[i][0];
            int z = offsets[i][1];
            int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos center = new BlockPos(x, y, z);
            PermadeathMod.LOGGER.info("[Permadeath] Altar {} at {} {} {}", names[i], x, y, z);
            positions.add(center.asLong());
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos current = center.offset(dx, 0, dz);
                    boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    end.setBlockAndUpdate(current, border ? Blocks.RED_GLAZED_TERRACOTTA.defaultBlockState() : Blocks.RED_CONCRETE.defaultBlockState());
                }
            }
            end.setBlockAndUpdate(center.above(), Blocks.RED_CARPET.defaultBlockState());
            BlockPos lantern = center.above(4);
            end.setBlockAndUpdate(lantern, Blocks.SEA_LANTERN.defaultBlockState());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                ItemFrame frame = new ItemFrame(end, lantern.relative(dir), dir);
                ItemStack water = new ItemStack(Items.POTION);
                water.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
                frame.setItem(water, false);
                end.addFreshEntity(frame);
            }
        }
        return positions.stream().mapToLong(Long::longValue).toArray();
    }

    private static void throwPotion(ServerLevel end, BlockPos pos) {
        ThrownPotion potion = new ThrownPotion(EntityType.POTION, end);
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(16711680), List.of(
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 2),
                new MobEffectInstance(MobEffects.REGENERATION, 400, 1),
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0))));
        potion.setItem(stack);
        potion.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        potion.shoot(0.0, -0.5, 0.0, 0.5F, 1.0F);
        end.addFreshEntity(potion);
    }
}
