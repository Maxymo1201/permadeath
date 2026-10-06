package com.serthekiller.permadeath.gametest;

import com.mojang.authlib.GameProfile;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.mechanics.LockedSlots;
import com.serthekiller.permadeath.phase.PhaseManager;
import com.serthekiller.permadeath.progression.DayController;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.recipes.RecipeFilter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * In-game tests of the day-dependent rules, run with {@code gradlew runGameTestServer}. The calendar is global,
 * so each batch fixes one day in {@link BeforeBatch} and batches run one after another.
 */
@GameTestHolder(PermadeathMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PermadeathGameTests {
    private static final String EMPTY = "permadeath:gametest_empty";
    private static final Random RANDOM_NAMES = new Random();

    private PermadeathGameTests() {
    }

    private static void setDay(ServerLevel level, int day) {
        MinecraftServer server = level.getServer();
        Permadeath.clock().setDay(day);
        Permadeath.data().setDirty();
        DayController.onServerTick(server);
    }

    @BeforeBatch(batch = "d0")
    public static void day0(ServerLevel level) {
        setDay(level, 0);
    }

    @BeforeBatch(batch = "d10")
    public static void day10(ServerLevel level) {
        setDay(level, 10);
    }

    @BeforeBatch(batch = "d40")
    public static void day40(ServerLevel level) {
        setDay(level, 40);
    }

    @BeforeBatch(batch = "d60")
    public static void day60(ServerLevel level) {
        setDay(level, 60);
    }


    /**
     * Server player with an in-memory connection, configured like the NeoForge test framework does
     * (ExtendedGameTestHelper#makeTickingMockServerPlayerInLevel) so that NeoForge networking accepts it.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "pd-test-" + RANDOM_NAMES.nextInt(100000)), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void tick() {
                super.tick();
                player.resetLastActionTime();
            }

            @Override
            public boolean isMemoryConnection() {
                return true;
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
                super.send(packet, listener, flush);
                if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                    player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
            }
        };
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        level.getServer().getConnection().getConnections().add(connection);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        return player;
    }

    private static void finish(GameTestHelper helper, ServerPlayer player) {
        if (!player.hasDisconnected()) {
            player.connection.disconnect(Component.literal("Permadeath GameTest finished"));
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ calendar

    @GameTest(template = EMPTY, batch = "d10")
    public static void calendarPhaseFollowsDay(GameTestHelper helper) {
        helper.assertTrue(Permadeath.day() == 10, "day should be 10, is " + Permadeath.day());
        helper.assertTrue(PhaseManager.currentPhase() == PermadeathCalendar.phaseForDay(10), "phase should be D10-19");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "d60")
    public static void calendarNeverGoesBeyondD60(GameTestHelper helper) {
        Permadeath.clock().setDay(70);
        helper.assertTrue(Permadeath.day() == PermadeathCalendar.FINAL_DAY, "D60 must be final, day is " + Permadeath.day());
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "d10")
    public static void hostileMobCapDoubledFromD10(GameTestHelper helper) {
        helper.assertTrue(MobCategory.MONSTER.getMaxInstancesPerChunk() == 140,
                "monster cap should be 140 on D10, is " + MobCategory.MONSTER.getMaxInstancesPerChunk());
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "d0")
    public static void hostileMobCapVanillaBeforeD10(GameTestHelper helper) {
        helper.assertTrue(MobCategory.MONSTER.getMaxInstancesPerChunk() == 70, "monster cap should be vanilla (70) on D0");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ drowning

    private static Pig submergedPig(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 2 ? Blocks.WATER : Blocks.GLASS);
                }
            }
        }
        helper.setBlock(new BlockPos(2, 5, 2), Blocks.GLASS);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(2.5, 1.0, 2.5));
        pig.setNoAi(true);
        return pig;
    }

    @GameTest(template = EMPTY, batch = "d60", timeoutTicks = 100)
    public static void drowningTenTimesFasterOnD60(GameTestHelper helper) {
        Pig pig = submergedPig(helper);
        helper.runAtTickTime(10, () -> {
            // vanilla: about 300 - 10 = 290 air left; x10: about 300 - 100.
            helper.assertTrue(pig.getAirSupply() <= 230, "D60 air should drop ~10/tick, air = " + pig.getAirSupply());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "d0", timeoutTicks = 100)
    public static void drowningVanillaBeforeD50(GameTestHelper helper) {
        Pig pig = submergedPig(helper);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(pig.getAirSupply() >= 280, "D0 air should drop ~1/tick, air = " + pig.getAirSupply());
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ recipes / loot

    @GameTest(template = EMPTY, batch = "d40")
    public static void torchRecipeRemovedOnD40(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeFilter.apply(server.getRecipeManager(), Permadeath.day());
        helper.assertTrue(server.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("torch")).isEmpty(),
                "minecraft:torch must not exist on D40");
        helper.assertTrue(server.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron")).isPresent(),
                "iron smelting is only removed from D50");
        helper.succeed();
    }

    private static List<ItemStack> dungeonLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }

    @GameTest(template = EMPTY, batch = "d60")
    public static void chestLootEmptyOnD60(GameTestHelper helper) {
        helper.assertTrue(dungeonLoot(helper).isEmpty(), "built-in chest loot must be empty on D60");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "d40")
    public static void chestLootPresentBeforeD60(GameTestHelper helper) {
        helper.assertTrue(!dungeonLoot(helper).isEmpty(), "dungeon loot must exist before D60");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ players

    @GameTest(template = EMPTY, batch = "d0")
    public static void oneTotemSavesBeforeD30(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        helper.assertTrue(player.isAlive(), "a single totem must save the player before D30");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the totem must be consumed");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "d40")
    public static void oneTotemIsNotEnoughOnD40(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        DamageSource source = helper.getLevel().damageSources().generic();
        player.hurt(source, 1000.0F);
        helper.assertTrue(player.isDeadOrDying(), "D40 requires two totems: the player must die");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the insufficient totem is consumed");
        // The dead player is banned and disconnected by DeathHandler 80 ticks later.
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "d0")
    public static void endClosedBeforeD30(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(player, Level.END);
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "travel to the End must be cancelled before D30");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "d40", timeoutTicks = 60)
    public static void maxHealthPenaltyAndLockedSlotsOnD40(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(Math.abs(player.getMaxHealth() - 12.0F) < 0.01F, "D40 max health should be 12, is " + player.getMaxHealth());
            helper.assertTrue(LockedSlots.isBlocker(player.getInventory().getItem(4)), "slot 4 must be locked on D40");
            helper.assertTrue(helper.getLevel().getServer().isPvpAllowed(), "PvP must be enabled from D40");
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d60", timeoutTicks = 60)
    public static void maxHealthPenaltyOnD60(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(Math.abs(player.getMaxHealth() - 4.0F) < 0.01F, "D60 max health should be 4, is " + player.getMaxHealth());
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d10")
    public static void pvpDisabledBeforeD40(GameTestHelper helper) {
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!helper.getLevel().getServer().isPvpAllowed(), "PvP must be disabled before D40");
            helper.succeed();
        });
    }
}
