package com.serthekiller.permadeath.mechanics;

import com.mojang.authlib.GameProfile;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.data.CustomMessagesData;
import com.serthekiller.permadeath.data.SurvivalAchievementData;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModSounds;
import com.serthekiller.permadeath.util.ServerScheduler;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Player death (Fabric DeathHandler): spectator mode, "¡Permadeath!" titles, sounds, bedrock + fence + head
 * monument, messages, automatic respawn, permanent ban after 4 s and a Death Train. The Fabric threads with
 * Thread.sleep were replaced by tick-based scheduling on the server thread (same delays in ticks).
 */
public final class DeathHandler {
    public static final String DEATH_TAG = "muerte1";

    private DeathHandler() {
    }

    /** Called from LivingDeathEvent; the work is done at the end of the tick, after the vanilla death logic. */
    public static void onPlayerDeath(ServerPlayer player) {
        // The day of the death (not of the end of the tick) decides the Death Train duration and the sounds.
        int day = Permadeath.day();
        ServerScheduler.schedule(0, () -> {
            if (player.isAlive() || !Permadeath.isRunning()) {
                return;
            }
            handle(player, day);
        });
    }

    private static void handle(ServerPlayer player, int day) {
        MinecraftServer server = player.server;
        ServerLevel level = player.serverLevel();
        String name = player.getName().getString();
        BlockPos deathPos = player.blockPosition();

        SurvivalAchievementData.get(server).recordDeath(player.getUUID());
        player.addTag(DEATH_TAG);
        player.setGameMode(GameType.SPECTATOR);
        sendTitles(server, name);
        broadcastSound(server, SoundEvents.BLAZE_DEATH, 100.0F, 0.3F);
        ServerScheduler.schedule(100, () -> broadcastSound(server, SoundEvents.SKELETON_HORSE_DEATH, 100.0F, 1.0F));
        if (day >= 30) {
            broadcastSound(server, ModSounds.PERMADEATH.get(), 100.0F, 1.0F);
        }
        createMonument(level, player);
        sendMessages(server, name, player, deathPos);

        ServerScheduler.schedule(0, () -> {
            try {
                if (player.connection != null && !player.isAlive()) {
                    player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                }
                if (day >= 30) {
                    level.playSound(null, deathPos, ModSounds.PERMADEATH.get(), SoundSource.MASTER, 100.0F, 1.0F);
                }
            } catch (RuntimeException e) {
                PermadeathMod.LOGGER.error("[Permadeath] Automatic respawn failed", e);
            }
        });
        scheduleBan(server, player.getGameProfile());

        long added = DeathTrain.trigger(server, day);
        ServerScheduler.schedule(100, () -> Texts.broadcast(server,
                Component.literal("¡Comienza el Death Train con duración de " + DeathTrain.durationText(added) + "!").withStyle(ChatFormatting.RED)));
        PermadeathMod.LOGGER.info("[Permadeath] Player {} died on day {} - Permadeath applied", name, day);
    }

    private static void sendTitles(MinecraftServer server, String name) {
        Component title = Component.literal("¡Permadeath!").withStyle(ChatFormatting.RED);
        Component subtitle = Component.literal(name + " ha muerto");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static void broadcastSound(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.serverLevel().playSound(null, p.blockPosition(), sound, SoundSource.MASTER, volume, pitch);
        }
    }

    // ------------------------------------------------------------------------------------------------ monument

    private static void createMonument(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        GameProfile profile = player.getGameProfile();
        float yaw = player.getYRot();
        int rotation = (Math.round((yaw % 360.0F + 360.0F) % 360.0F / 22.5F) + 8) % 16;
        int baseY = Math.max(pos.getY() - 1, level.getMinBuildHeight());
        BlockPos base = new BlockPos(pos.getX(), baseY, pos.getZ());
        BlockPos skull = base.above(2);
        // Fabric: 150 ms (3 ticks) after the death, then a repair check after 1 s.
        ServerScheduler.schedule(3, () -> {
            placeMonument(level, base, skull, rotation, profile);
            ServerScheduler.schedule(20, () -> {
                if (level.getBlockState(base.above()).isAir() || level.getBlockState(skull).isAir()) {
                    PermadeathMod.LOGGER.info("[Permadeath] Repairing death monument at {}", base);
                    placeMonument(level, base, skull, rotation, profile);
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(skull).inflate(3.0), i -> i.getItem().is(Items.PLAYER_HEAD))) {
                        item.discard();
                    }
                }
            });
        });
    }

    private static void placeMonument(ServerLevel level, BlockPos base, BlockPos skull, int rotation, GameProfile profile) {
        level.setBlock(base, Blocks.BEDROCK.defaultBlockState(), 3);
        level.setBlock(base.above(), Blocks.OAK_FENCE.defaultBlockState(), 3);
        BlockState skullState = Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, rotation);
        level.setBlock(skull, skullState, 3);
        if (level.getBlockEntity(skull) instanceof SkullBlockEntity head) {
            head.setOwner(new ResolvableProfile(profile));
            head.setChanged();
            level.sendBlockUpdated(skull, head.getBlockState(), head.getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------------------------------------ messages

    private static void sendMessages(MinecraftServer server, String name, ServerPlayer player, BlockPos pos) {
        Texts.broadcast(server, Component.empty()
                .append(Component.literal("Este es el comienzo del sufrimiento eterno de ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(name).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal(". ¡HA SIDO PERMABANEADO!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        String custom = CustomMessagesData.get(server).getMessage(player.getUUID());
        Texts.broadcast(server, Component.empty()
                .append(Component.literal(name).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(custom).withStyle(ChatFormatting.GRAY)));
        Texts.broadcast(server, Component.empty()
                .append(Component.literal("Se ha generado una cabeza en " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.GRAY))
                .append(dimensionLabel(player.level().dimension())));
    }

    private static MutableComponent dimensionLabel(ResourceKey<Level> dimension) {
        String name;
        ChatFormatting color;
        if (dimension == Level.OVERWORLD) {
            name = "Overworld";
            color = ChatFormatting.GREEN;
        } else if (dimension == Level.NETHER) {
            name = "Nether";
            color = ChatFormatting.DARK_RED;
        } else if (dimension == Level.END) {
            name = "End";
            color = ChatFormatting.BLACK;
        } else if (dimension == BeginningDimension.LEVEL_KEY) {
            name = "The Beginning";
            color = ChatFormatting.DARK_PURPLE;
        } else {
            name = dimension.location().toString();
            color = ChatFormatting.GRAY;
        }
        return Component.literal(" [" + name + "]").withStyle(color, ChatFormatting.BOLD);
    }

    // ------------------------------------------------------------------------------------------------ ban

    /**
     * Permanent ban 4 s (80 ticks) after the death. The owner of an integrated (single player / LAN) server
     * is not banned: kicking the host would close the world (the Fabric mod did it anyway).
     */
    private static void scheduleBan(MinecraftServer server, GameProfile profile) {
        ServerScheduler.schedule(80, () -> {
            if (server.isSingleplayerOwner(profile)) {
                PermadeathMod.LOGGER.info("[Permadeath] {} is the owner of the integrated server: not banned", profile.getName());
                return;
            }
            server.getPlayerList().getBans().add(new UserBanListEntry(profile, null, "Permadeath", null,
                    "Has muerto en Permadeath. Baneo permanente."));
            ServerPlayer current = server.getPlayerList().getPlayer(profile.getId());
            if (current != null) {
                current.connection.disconnect(Component.literal("Has sido PERMABANEADO").withStyle(ChatFormatting.RED));
            }
        });
    }
}
