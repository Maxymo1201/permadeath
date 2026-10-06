package com.serthekiller.permadeath.end;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;

import java.util.List;

/**
 * EndPillarBedrockHandler: the obsidian pillars of the End become bedrock (they cannot be mined to reach the
 * crystals). Fabric scanned 401x401 columns x 256 blocks synchronously on world load; the port converts the
 * vanilla spikes ({@link SpikeFeature#getSpikesForLevel}), one spike per tick, the first time a player is in
 * the End and 40 ticks after every dragon (re)spawn, when vanilla rebuilds the pillars.
 */
public final class EndPillars {
    private static boolean convertedThisSession;

    private EndPillars() {
    }

    public static void reset() {
        convertedThisSession = false;
    }

    public static void onPlayerInEnd(ServerLevel end) {
        if (!convertedThisSession) {
            convertedThisSession = true;
            schedule(end, 0);
        }
    }

    public static void onDragonJoin(ServerLevel end) {
        schedule(end, 40);
    }

    private static void schedule(ServerLevel end, int delay) {
        List<SpikeFeature.EndSpike> spikes = SpikeFeature.getSpikesForLevel(end);
        for (int i = 0; i < spikes.size(); i++) {
            SpikeFeature.EndSpike spike = spikes.get(i);
            ServerScheduler.schedule(delay + i, () -> convert(end, spike));
        }
    }

    private static void convert(ServerLevel end, SpikeFeature.EndSpike spike) {
        int radius = spike.getRadius();
        int converted = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = spike.getCenterX() - radius; x <= spike.getCenterX() + radius; x++) {
            for (int z = spike.getCenterZ() - radius; z <= spike.getCenterZ() + radius; z++) {
                for (int y = end.getMinBuildHeight(); y < spike.getHeight(); y++) {
                    pos.set(x, y, z);
                    if (end.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                        end.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
                        converted++;
                    }
                }
            }
        }
        if (converted > 0) {
            PermadeathMod.LOGGER.debug("[Permadeath] End pillar at {},{}: {} obsidian blocks → bedrock", spike.getCenterX(), spike.getCenterZ(), converted);
        }
    }
}
