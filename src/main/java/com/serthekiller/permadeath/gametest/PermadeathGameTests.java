package com.serthekiller.permadeath.gametest;

import com.mojang.authlib.GameProfile;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.beginning.BeginningEffects;
import com.serthekiller.permadeath.core.FinalParticipant;
import com.serthekiller.permadeath.core.FinalPhaseState;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionMode;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.core.time.CampaignTimers;
import com.serthekiller.permadeath.core.time.FinalChallenge;
import com.serthekiller.permadeath.core.time.PermadeathTimings;
import com.serthekiller.permadeath.core.time.TimerMigration;
import com.serthekiller.permadeath.data.BeginningCurseData;
import com.serthekiller.permadeath.data.DeathRecordsData;
import com.serthekiller.permadeath.data.PermadeathData;
import com.serthekiller.permadeath.end.EnderDragonDemon;
import com.serthekiller.permadeath.mechanics.DeathHandler;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.mechanics.FinalChallengeManager;
import com.serthekiller.permadeath.mechanics.LifeOrb;
import com.serthekiller.permadeath.mechanics.LockedSlots;
import com.serthekiller.permadeath.mechanics.Participants;
import com.serthekiller.permadeath.mechanics.PlayerHealth;
import com.serthekiller.permadeath.mechanics.ShulkerShellEvent;
import com.serthekiller.permadeath.mechanics.WitherSpawner;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.phase.PhaseManager;
import com.serthekiller.permadeath.progression.CampaignTicker;
import com.serthekiller.permadeath.progression.DayController;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.recipes.RecipeFilter;
import com.serthekiller.permadeath.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    private static final String EMPTY = "gametest_empty";
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

    @BeforeBatch(batch = "d20")
    public static void day20(ServerLevel level) {
        setDay(level, 20);
    }

    @BeforeBatch(batch = "d40")
    public static void day40(ServerLevel level) {
        setDay(level, 40);
    }

    /** Own batch: the power 15 explosion of the dragon TNT would hurt the players of the other D40 tests. */
    @BeforeBatch(batch = "d40tnt")
    public static void day40Tnt(ServerLevel level) {
        setDay(level, 40);
    }

    @BeforeBatch(batch = "d50")
    public static void day50(ServerLevel level) {
        setDay(level, 50);
    }

    @BeforeBatch(batch = "d60")
    public static void day60(ServerLevel level) {
        setDay(level, 60);
        quietD60();
    }

    /** No Mikecrack creepers near the low-health test players of the D60 batches. */
    private static void quietD60() {
        Permadeath.state().mikecrackDisabled = true;
        Permadeath.state().markChanged();
    }

    @BeforeBatch(batch = "d50storm")
    public static void day50Storm(ServerLevel level) {
        setDay(level, 50);
    }

    @BeforeBatch(batch = "d60orb")
    public static void day60LifeOrb(ServerLevel level) {
        setDay(level, 60);
        quietD60();
        Permadeath.state().lifeOrbActive = true;
        Permadeath.state().lifeOrbRemainingMillis = -1L;
        Permadeath.state().markChanged();
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
        // From D40 the periodic mob pass turns unnamed pigs into ravagers; a named pig with its effects already
        // applied (like the pigman's pig) stays a plain pig.
        pig.setCustomName(Component.literal("Cerdo de prueba"));
        pig.addTag("EffectsApplied");
        return pig;
    }

    /** Air lost by the submerged pig between ticks 10 and 20 (the first ticks depend on how the pig settles). */
    private static void assertAirLossOverTenTicks(GameTestHelper helper, Pig pig, int min, int max, String expected) {
        int[] before = new int[1];
        helper.runAtTickTime(10, () -> before[0] = pig.getAirSupply());
        helper.runAtTickTime(20, () -> {
            int lost = before[0] - pig.getAirSupply();
            helper.assertTrue(lost >= min && lost <= max, expected + ", lost " + lost + " air in 10 ticks");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "d60", timeoutTicks = 100)
    public static void drowningTenTimesFasterOnD60(GameTestHelper helper) {
        // vanilla: 1 air per tick; x10: 10 per tick.
        assertAirLossOverTenTicks(helper, submergedPig(helper), 80, 100, "D60 air should drop ~10/tick");
    }

    @GameTest(template = EMPTY, batch = "d0", timeoutTicks = 100)
    public static void drowningVanillaBeforeD50(GameTestHelper helper) {
        assertAirLossOverTenTicks(helper, submergedPig(helper), 1, 10, "D0 air should drop ~1/tick");
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

    /** A player that just joined is invulnerable for 60 ticks (vanilla spawn protection): hit after that. */
    private static final int AFTER_SPAWN_PROTECTION = 65;

    @GameTest(template = EMPTY, batch = "d0", timeoutTicks = 120)
    public static void oneTotemSavesBeforeD30(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        helper.runAtTickTime(AFTER_SPAWN_PROTECTION, () -> {
            player.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
            helper.assertTrue(player.isAlive(), "a single totem must save the player before D30");
            helper.assertTrue(player.getMainHandItem().isEmpty(), "the totem must be consumed");
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d40", timeoutTicks = 120)
    public static void oneTotemIsNotEnoughOnD40(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        helper.runAtTickTime(AFTER_SPAWN_PROTECTION, () -> {
            DamageSource source = helper.getLevel().damageSources().generic();
            player.hurt(source, 1000.0F);
            helper.assertTrue(player.isDeadOrDying(), "D40 requires two totems: the player must die");
            helper.assertTrue(player.getMainHandItem().isEmpty(), "the insufficient totem is consumed");
            // The dead player is banned and disconnected by DeathHandler 80 ticks later.
            helper.succeed();
        });
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

    @GameTest(template = EMPTY, batch = "d40", timeoutTicks = 60)
    public static void numberKeySwapIntoLockedSlotReturnsTheItemToTheChest(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        helper.runAtTickTime(5, () -> {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, chest), Component.literal("test")));
            // Number key 5 over the first chest slot: the diamonds go to the locked hotbar slot 4.
            player.containerMenu.clicked(0, 4, ClickType.SWAP, player);
            helper.assertTrue(player.getInventory().getItem(4).is(Items.DIAMOND), "the swap should have reached the locked slot");
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(LockedSlots.isBlocker(player.getInventory().getItem(4)), "slot 4 must be locked again");
            helper.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7,
                    "the diamonds must go back to the chest, found " + chest.getItem(0));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8.0)).isEmpty(),
                    "nothing may be thrown on the ground");
            player.closeContainer();
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d40", timeoutTicks = 60)
    public static void relicOnTheCursorKeepsTheSlotsUnlocked(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        helper.runAtTickTime(5, () -> {
            // Full inventory, End Relic picked up with the cursor.
            for (int i = 0; i < 36; i++) {
                player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            player.getInventory().setItem(40, new ItemStack(Items.TORCH, 16));
            player.containerMenu.setCarried(new ItemStack(ModItems.END_RELIC.get()));
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(player.getInventory().getItem(4).is(Items.COBBLESTONE), "slot 4 was locked while the relic was on the cursor");
            helper.assertTrue(player.getInventory().getItem(40).is(Items.TORCH), "the off hand was locked while the relic was on the cursor");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8.0)).isEmpty(),
                    "nothing may be thrown on the ground");
            player.containerMenu.setCarried(ItemStack.EMPTY);
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d40", timeoutTicks = 60)
    public static void spectatorDeathIsNoNewPermadeath(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        helper.runAtTickTime(5, () -> {
            player.setGameMode(GameType.SPECTATOR);
            // /kill (and the void) bypass the invulnerability of spectators.
            player.kill();
        });
        helper.runAtTickTime(10, () -> {
            helper.assertFalse(player.isAlive(), "the spectator should have died");
            helper.assertTrue(DeathRecordsData.get(helper.getLevel().getServer()).get(player.getUUID()) == null,
                    "a spectator killed by /kill or the void was recorded as a new Permadeath");
            helper.assertFalse(player.getTags().contains(DeathHandler.DEATH_TAG), "a spectator death must not be handled");
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d40")
    public static void crafterRefusesTheSpecialRecipes(GameTestHelper helper) {
        // Super Golden Apple+: 8 gold ingots on every edge, 1 golden apple in the centre.
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(i == 4 ? new ItemStack(Items.GOLDEN_APPLE) : new ItemStack(Items.GOLD_INGOT, 8));
        }
        CraftingInput input = CraftingInput.of(3, 3, items);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).isPresent(),
                "the Super Golden Apple+ recipe must match by hand on D40");
        helper.assertTrue(CrafterBlock.getPotentialResults(level, input).isEmpty(),
                "the Crafter must refuse the Super Golden Apple+ (it skipped the extra cost)");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "d40tnt", timeoutTicks = 60)
    public static void dragonTntExplodesOnceWithoutBreakingBlocks(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 2, 3);
        List<BlockPos> around = List.of(center.below(), center.north(), center.south(), center.east(), center.west());
        around.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        BlockPos abs = helper.absolutePos(center);
        PrimedTnt tnt = new PrimedTnt(helper.getLevel(), abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, null);
        tnt.setFuse(5);
        tnt.addTag(EnderDragonDemon.DRAGON_TNT_TAG);
        helper.getLevel().addFreshEntity(tnt);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(tnt.isRemoved(), "the dragon TNT must have exploded");
            // Only the plugin explosion (power 15, no block damage) may run, not the vanilla power 4 one.
            around.forEach(pos -> helper.assertBlockPresent(Blocks.STONE, pos));
            helper.succeed();
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

    // ------------------------------------------------------------------------------------------------ plugin rules

    @GameTest(template = EMPTY, batch = "d20", timeoutTicks = 200)
    public static void villageIronGolemHuntsPlayersFromD20(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.getTarget() == player, "a village golem must target the player from D20");
            player.connection.disconnect(Component.literal("Permadeath GameTest finished"));
            golem.discard();
        });
    }

    @GameTest(template = EMPTY, batch = "d20", timeoutTicks = 120)
    public static void playerBuiltIronGolemStaysFriendly(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 1, 4));
        golem.setPlayerCreated(true);
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(golem.getTarget() != player, "a golem built by a player must not target players");
            golem.discard();
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d20", timeoutTicks = 40)
    public static void naturalSkeletonGetsAClassOnD20(GameTestHelper helper) {
        helper.spawn(EntityType.SKELETON, new BlockPos(3, 1, 3));
        helper.runAtTickTime(5, () -> {
            List<AbstractSkeleton> skeletons = helper.getLevel().getEntitiesOfClass(AbstractSkeleton.class, helper.getBounds());
            helper.assertTrue(skeletons.size() == 1, "one (wither) skeleton expected, found " + skeletons.size());
            AbstractSkeleton skeleton = skeletons.get(0);
            helper.assertTrue(!skeleton.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "a D20 skeleton must wear a class armour");
            helper.assertTrue(skeleton.getMaxHealth() == 20.0F || skeleton.getMaxHealth() == 40.0F,
                    "D20 class health must be 20 or 40, is " + skeleton.getMaxHealth());
            skeleton.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "d50")
    public static void drowningHitDealsFiveOnD50(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        LivingDrownEvent event = NeoForge.EVENT_BUS.post(new LivingDrownEvent(player));
        helper.assertTrue(event.getDamageAmount() == 5.0F, "D50 drowning hit must be 5, is " + event.getDamageAmount());
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "d60")
    public static void drowningHitDealsTenOnD60(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        LivingDrownEvent event = NeoForge.EVENT_BUS.post(new LivingDrownEvent(player));
        helper.assertTrue(event.getDamageAmount() == 10.0F, "D60 drowning hit must be 10, is " + event.getDamageAmount());
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "d50")
    public static void milkKeepsOnlyMiningFatigueOnD50(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
        player.removeEffectsCuredBy(EffectCures.MILK);
        helper.assertTrue(player.hasEffect(MobEffects.DIG_SLOWDOWN), "milk must not remove Mining Fatigue from D50");
        helper.assertTrue(!player.hasEffect(MobEffects.POISON), "milk must cure poison");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "d50")
    public static void pufferfishUsesPluginEffectsOnD50(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.PUFFERFISH), 0, ItemStack.EMPTY));
        assertInfiniteEffect(helper, player, MobEffects.POISON, 3);
        assertInfiniteEffect(helper, player, MobEffects.HUNGER, 2);
        assertInfiniteEffect(helper, player, MobEffects.CONFUSION, 1);
        finish(helper, player);
    }

    private static void assertInfiniteEffect(GameTestHelper helper, ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance instance = player.getEffect(effect);
        helper.assertTrue(instance != null && instance.getAmplifier() == amplifier && instance.isInfiniteDuration(),
                effect.getRegisteredName() + " " + (amplifier + 1) + " infinite expected, got " + instance);
    }

    @GameTest(template = EMPTY, batch = "d50")
    public static void superGoldenApplePlusIsAGoldenApple(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        new ItemStack(ModItems.SUPER_GOLDEN_APPLE_PLUS.get()).finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION), "the apple must give Absorption like a golden apple");
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "the apple must give Regeneration like a golden apple");
        helper.assertTrue(player.hasEffect(MobEffects.HEALTH_BOOST), "the apple must give Health Boost");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "d50")
    public static void enderCreeperDodgesEverythingButMelee(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(5, 1, 5));
        MobTracking.markProcessed(creeper, "creeper_variant_d50");
        creeper.setCustomName(Component.literal(EnderMobs.ENDER_CREEPER_NAME));
        float health = creeper.getHealth();
        creeper.hurt(helper.getLevel().damageSources().magic(), 4.0F);
        helper.assertTrue(creeper.getHealth() == health, "an Ender Creeper must dodge magic damage");
        creeper.hurt(helper.getLevel().damageSources().playerAttack(player), 4.0F);
        helper.assertTrue(creeper.getHealth() < health, "an Ender Creeper must take melee damage");
        creeper.discard();
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "d50", timeoutTicks = 40)
    public static void enderCreeperReplacementKeepsItsNameOnD50(GameTestHelper helper) {
        // Like the Ender Creepers that replace Nether endermen: named before their deferred phase join runs. Eight of
        // them, because the old re-roll kept the name 20 % of the time.
        List<Creeper> creepers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(1 + i % 4, 1, 4 + i / 4 * 2));
            creeper.setCustomName(Component.literal(EnderMobs.ENDER_CREEPER_NAME));
            creepers.add(creeper);
        }
        helper.runAtTickTime(5, () -> {
            for (Creeper creeper : creepers) {
                helper.assertTrue(EnderMobs.isEnderCreeper(creeper), "an Ender Creeper was renamed to " + creeper.getName().getString());
                helper.assertTrue(creeper.hasEffect(MobEffects.INVISIBILITY), "a D50 Ender Creeper is invisible");
                creeper.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "d40", timeoutTicks = 40)
    public static void zombifiedPiglinKeepsItsAttackWhenTurnedHostile(GameTestHelper helper) {
        ZombifiedPiglin pigman = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, new BlockPos(5, 1, 5));
        helper.runAtTickTime(5, () -> {
            double attack = pigman.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            // Vanilla 5, or the pigman class value (20, 12 or 8 on D40); the hostile conversion used to set 2.
            helper.assertTrue(attack >= 5.0, "zombified piglin attack lowered to " + attack);
            pigman.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "d50storm", timeoutTicks = 40)
    public static void deathTrainBuffsArePermanentOnD50(GameTestHelper helper) {
        // A witch: D40+ zombies are replaced by vindicators.
        Witch witch = helper.spawn(EntityType.WITCH, new BlockPos(3, 1, 3));
        helper.runAtTickTime(2, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DeathTrain.trigger(server, 50);
            MobEffectInstance strength = witch.getEffect(MobEffects.DAMAGE_BOOST);
            MobEffectInstance fire = witch.getEffect(MobEffects.FIRE_RESISTANCE);
            DeathTrain.reset(server);
            witch.discard();
            helper.assertTrue(strength != null && strength.getAmplifier() == 1 && strength.isInfiniteDuration(),
                    "D50 Death Train must give infinite Strength II, got " + strength);
            helper.assertTrue(fire != null && fire.isInfiniteDuration(), "D50-59 Death Train must give infinite Fire Resistance");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "d60orb", timeoutTicks = 160)
    public static void lifeOrbPenaltyLowersOnlyMaxHealthAndIsSaved(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        helper.runAtTickTime(AFTER_SPAWN_PROTECTION + 70, () -> {
            AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
            boolean saved = health != null && health.save().toString().contains(PlayerHealth.LIFE_ORB_PENALTY.toString());
            boolean alive = player.isAlive();
            float current = player.getHealth();
            float max = player.getMaxHealth();
            Permadeath.state().lifeOrbActive = false;
            Permadeath.state().markChanged();
            helper.assertTrue(alive, "the Life Orb penalty must not kill the player (only -16 max HP)");
            helper.assertTrue(saved, "the Life Orb penalty must be saved with the player (no new penalty after a relog)");
            helper.assertTrue(current <= max && max <= 1.0F, "D60 without Life Orb: max health 1, got " + current + "/" + max);
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "d60", timeoutTicks = 140)
    public static void pearlCooldownSixSecondsAfterLandingOnD60(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        ThrownEnderpearl pearl = new ThrownEnderpearl(helper.getLevel(), player);
        Vec3 target = player.position();
        EventHooks.onEnderPearlLand(player, target.x, target.y, target.z, pearl, 5.0F,
                BlockHitResult.miss(target, Direction.UP, player.blockPosition()));
        pearl.discard();
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(player.getCooldowns().isOnCooldown(Items.ENDER_PEARL), "D60 pearl cooldown must last 6 s after landing");
            finish(helper, player);
        });
    }

    // ------------------------------------------------------------------------------------------------ campaign timers

    /** Every timer batch starts clean: no storm, event, Life Orb countdown, final challenge or Wither counter. */
    private static void resetTimers(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ProgressionState s = Permadeath.state();
        DeathTrain.reset(server);
        s.shulkerEventRemainingMillis = 0L;
        FinalChallenge.reset(s);
        CampaignTimers.clearLifeOrb(s);
        s.witherRemainingMillis.clear();
        s.markChanged();
    }

    /** The progression file as a restart would read it again (save + load through the SavedData factory). */
    private static PermadeathData reloadProgression(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = Permadeath.data().save(new CompoundTag(), registries);
        return PermadeathData.factory().deserializer().apply(tag, registries);
    }

    private static int discardWithers(GameTestHelper helper, ServerPlayer player) {
        List<WitherBoss> withers = helper.getLevel().getEntitiesOfClass(WitherBoss.class, player.getBoundingBox().inflate(16.0));
        withers.forEach(WitherBoss::discard);
        return withers.size();
    }

    private static void assertMillis(GameTestHelper helper, long actual, long expected, String what) {
        helper.assertTrue(actual == expected, what + ": expected " + expected + " ms (" + TimeFormat.compact(expected) + "), got "
                + actual + " ms (" + TimeFormat.compact(actual) + ")");
    }

    @BeforeBatch(batch = "tdeaths")
    public static void timerDeathsBatch(ServerLevel level) {
        setDay(level, 40);
        resetTimers(level);
    }

    @BeforeBatch(batch = "tpause")
    public static void timerPauseBatch(ServerLevel level) {
        setDay(level, 40);
        resetTimers(level);
    }

    @BeforeBatch(batch = "tuhc")
    public static void timerUhcBatch(ServerLevel level) {
        setDay(level, 50);
        resetTimers(level);
    }

    @BeforeBatch(batch = "twither")
    public static void timerWitherBatch(ServerLevel level) {
        setDay(level, 60);
        quietD60();
        resetTimers(level);
    }

    @BeforeBatch(batch = "tfinal")
    public static void timerFinalBatch(ServerLevel level) {
        setDay(level, 60);
        quietD60();
        resetTimers(level);
    }

    @BeforeBatch(batch = "tfinalfail")
    public static void timerFinalFailBatch(ServerLevel level) {
        setDay(level, 60);
        quietD60();
        resetTimers(level);
    }

    @BeforeBatch(batch = "tevents")
    public static void timerEventsBatch(ServerLevel level) {
        setDay(level, 50);
        resetTimers(level);
    }

    @GameTest(template = EMPTY, batch = "tpause")
    public static void deathTrainDurationOfEveryDayFollowsTheProfile(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionState s = Permadeath.state();
        boolean game60 = Permadeath.mode() == ProgressionMode.GAME60;
        long total = 0L;
        for (int day = 0; day <= PermadeathCalendar.FINAL_DAY; day++) {
            long original = DayRules.deathTrainDurationMillis(day);
            long expected = game60 ? Math.max(60_000L, original / 72L) : original / 2L;
            long added = DeathTrain.trigger(server, day);
            total += expected;
            assertMillis(helper, added, expected, "Death Train of D" + day);
        }
        assertMillis(helper, s.deathTrainRemainingMillis, total, "deaths of D0-D60 must add up");
        // Reference values of the specification.
        assertMillis(helper, Permadeath.timings().deathTrainMillis(1), game60 ? 60_000L : 30L * PermadeathTimings.MINUTE, "D1");
        assertMillis(helper, Permadeath.timings().deathTrainMillis(24), game60 ? 20L * PermadeathTimings.MINUTE : 12L * PermadeathTimings.HOUR, "D24");
        assertMillis(helper, Permadeath.timings().deathTrainMillis(40), game60 ? 800_000L : 8L * PermadeathTimings.HOUR, "D40");
        assertMillis(helper, Permadeath.timings().deathTrainMillis(50), game60 ? 60_000L : 15L * PermadeathTimings.MINUTE, "D50");
        assertMillis(helper, Permadeath.timings().deathTrainMillis(60), game60 ? 275_000L : 165L * PermadeathTimings.MINUTE, "D60");
        DeathTrain.reset(server);
        helper.assertTrue(!DeathTrain.isActive(), "reset must end the storm");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "tpause")
    public static void deathTrainRunsOnlyWithAnEligibleSurvivorAndSurvivesARestart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionState s = Permadeath.state();
        DeathTrain.reset(server);
        long one = Permadeath.timings().deathTrainMillis(40);
        helper.assertTrue(!Participants.anyEligible(server), "no eligible survivor may be online at the start of this test");
        DeathTrain.trigger(server, 40);
        DeathTrain.trigger(server, 40);
        assertMillis(helper, s.deathTrainRemainingMillis, 2 * one, "two deaths add two storms");
        CampaignTicker.run(server, 60_000L);
        assertMillis(helper, s.deathTrainRemainingMillis, 2 * one, "an empty server must not consume the storm");

        ServerPlayer player = mockPlayer(helper);
        CampaignTicker.run(server, 60_000L);
        assertMillis(helper, s.deathTrainRemainingMillis, 2 * one - 60_000L, "a survivor online consumes real time");
        player.setGameMode(GameType.SPECTATOR);
        CampaignTicker.run(server, 60_000L);
        player.setGameMode(GameType.CREATIVE);
        CampaignTicker.run(server, 60_000L);
        assertMillis(helper, s.deathTrainRemainingMillis, 2 * one - 60_000L, "spectators and creative players keep it paused");
        player.setGameMode(GameType.SURVIVAL);

        long before = s.deathTrainRemainingMillis;
        DeathTrain.addMillis(server, PermadeathTimings.HOUR);
        assertMillis(helper, s.deathTrainRemainingMillis, before + PermadeathTimings.HOUR, "storm add is effective real time, not scaled");
        helper.assertTrue(DeathTrain.removeMillis(server, PermadeathTimings.HOUR), "storm remove must work while it runs");
        assertMillis(helper, s.deathTrainRemainingMillis, before, "storm remove");

        PermadeathData reloaded = reloadProgression(helper);
        assertMillis(helper, reloaded.state().deathTrainRemainingMillis, before, "the remaining time must be saved (restart)");

        CampaignTimers.Step step = CampaignTicker.run(server, before);
        helper.assertTrue(step.deathTrainEnded() && !DeathTrain.isActive(), "the storm must end when its time is used up");
        helper.assertTrue(s.deathTrainRemainingMillis == 0L, "never negative: " + s.deathTrainRemainingMillis);
        helper.assertTrue(!CampaignTicker.run(server, 60_000L).deathTrainEnded(), "the end is reported once");
        helper.assertTrue(!DeathTrain.removeMillis(server, 1_000L), "nothing to remove without a storm");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "tdeaths", timeoutTicks = 120)
    public static void simultaneousDeathsAddBothStorms(GameTestHelper helper) {
        ServerPlayer first = mockPlayer(helper);
        ServerPlayer second = mockPlayer(helper);
        helper.runAtTickTime(AFTER_SPAWN_PROTECTION, () -> {
            DamageSource source = helper.getLevel().damageSources().generic();
            first.hurt(source, 1000.0F);
            second.hurt(source, 1000.0F);
        });
        helper.runAtTickTime(AFTER_SPAWN_PROTECTION + 5, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            long remaining = Permadeath.state().deathTrainRemainingMillis;
            long expected = 2 * Permadeath.timings().deathTrainMillis(40);
            helper.assertTrue(!first.isAlive() && !second.isAlive(), "both players must have died");
            boolean eligible = Participants.anyEligible(server);
            CampaignTicker.run(server, 60_000L);
            long afterStep = Permadeath.state().deathTrainRemainingMillis;
            DeathTrain.reset(server);
            assertMillis(helper, remaining, expected, "two deaths in the same tick add both storms");
            helper.assertTrue(!eligible, "dead players are not eligible survivors");
            assertMillis(helper, afterStep, expected, "without survivors the storm is paused");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "tuhc")
    public static void deathTrainRestoresNaturalRegenerationOnlyIfItChangedIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionState s = Permadeath.state();
        GameRules.BooleanValue regeneration = server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        helper.assertTrue(!WitherSpawner.running(), "periodic Withers only exist on D60");
        ServerPlayer player = mockPlayer(helper);

        regeneration.set(true, server);
        DeathTrain.trigger(server, 50);
        CampaignTicker.run(server, 0L);
        helper.assertTrue(!regeneration.get() && s.deathTrainUhcActive, "a D50 storm turns naturalRegeneration off (UHC)");
        helper.assertTrue(reloadProgression(helper).state().deathTrainUhcActive, "the UHC flag must be saved");
        CampaignTicker.run(server, s.deathTrainRemainingMillis);
        helper.assertTrue(regeneration.get() && !s.deathTrainUhcActive, "the end of the storm restores naturalRegeneration");

        regeneration.set(false, server);
        DeathTrain.trigger(server, 50);
        CampaignTicker.run(server, 0L);
        helper.assertTrue(!s.deathTrainUhcActive, "a rule already off was not changed by the storm");
        CampaignTicker.run(server, s.deathTrainRemainingMillis);
        boolean keptOff = !regeneration.get();
        regeneration.set(true, server);
        helper.assertTrue(keptOff, "naturalRegeneration turned off by the server must stay off after the storm");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "twither")
    public static void witherCounterIsPerPlayerAndOnlyRunsForEligiblePlayers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionState s = Permadeath.state();
        long interval = Permadeath.timings().witherIntervalMillis();
        ServerPlayer a = mockPlayer(helper);
        ServerPlayer b = mockPlayer(helper);
        CampaignTicker.run(server, 0L);
        helper.assertTrue(WitherSpawner.running(), "periodic Withers run on D60");
        assertMillis(helper, WitherSpawner.remainingFor(a.getUUID()), interval, "counter of a new player");

        s.witherRemainingMillis.put(b.getUUID(), interval / 2);
        CampaignTicker.run(server, interval / 2);
        int summoned = discardWithers(helper, a);
        helper.assertTrue(summoned == 1, "exactly one Wither (for the second player) expected, got " + summoned);
        assertMillis(helper, WitherSpawner.remainingFor(a.getUUID()), interval / 2, "first player keeps its own counter");
        assertMillis(helper, WitherSpawner.remainingFor(b.getUUID()), interval, "the counter restarts once the Wither exists");

        a.setGameMode(GameType.SPECTATOR);
        CampaignTicker.run(server, interval / 4);
        assertMillis(helper, WitherSpawner.remainingFor(a.getUUID()), interval / 2, "a spectator's counter does not run");
        assertMillis(helper, WitherSpawner.remainingFor(b.getUUID()), interval - interval / 4, "an eligible player's counter runs");
        a.setGameMode(GameType.SURVIVAL);

        // A long freeze (or catch-up) never summons a burst of Withers. The final challenge is kept running for it.
        a.setGameMode(GameType.SPECTATOR);
        s.finalPhaseRemainingMillis = 100 * interval;
        CampaignTicker.run(server, 10 * interval);
        summoned = discardWithers(helper, b);
        helper.assertTrue(summoned == 1, "one Wither after a long step, got " + summoned);
        assertMillis(helper, WitherSpawner.remainingFor(b.getUUID()), interval, "counter after the long step");
        a.setGameMode(GameType.SURVIVAL);

        PermadeathData reloaded = reloadProgression(helper);
        assertMillis(helper, reloaded.state().witherRemainingMillis.getOrDefault(a.getUUID(), -1L), interval / 2,
                "the counter of each player must be saved");
        resetTimers(helper.getLevel());
        discardWithers(helper, a);
        a.connection.disconnect(Component.literal("Permadeath GameTest finished"));
        finish(helper, b);
    }

    @GameTest(template = EMPTY, batch = "tfinal")
    public static void finalChallengeVictoryTimeline(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionState s = Permadeath.state();
        PermadeathTimings timings = Permadeath.timings();
        ServerPlayer player = mockPlayer(helper);
        CampaignTicker.run(server, 0L);
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.ACTIVE, "the final challenge starts with a survivor on D60, is " + s.finalPhaseState);
        assertMillis(helper, s.finalPhaseRemainingMillis, timings.finalPhaseMillis(), "final challenge duration");
        assertMillis(helper, s.lifeOrbRemainingMillis, timings.lifeOrbCountdownMillis(), "Life Orb countdown starts at T0");
        helper.assertTrue(s.finalParticipants.containsKey(player.getUUID()), "the survivor must be a participant");

        player.setGameMode(GameType.SPECTATOR);
        CampaignTicker.run(server, 60_000L);
        assertMillis(helper, s.finalPhaseRemainingMillis, timings.finalPhaseMillis(), "the final is paused without survivors");
        assertMillis(helper, s.lifeOrbRemainingMillis, timings.lifeOrbCountdownMillis(), "the Life Orb countdown is paused too");
        player.setGameMode(GameType.SURVIVAL);

        CampaignTicker.run(server, timings.lifeOrbCountdownMillis() - 60_000L);
        discardWithers(helper, player);
        assertMillis(helper, s.lifeOrbRemainingMillis, 60_000L, "Life Orb time left one minute before the deadline");
        player.getInventory().add(new ItemStack(ModItems.LIFE_ORB.get()));
        CampaignTicker.run(server, 0L);
        FinalParticipant participant = s.finalParticipants.get(player.getUUID());
        helper.assertTrue(participant.lifeOrbBeforeDeadline, "a Life Orb held before the deadline must count");

        CampaignTimers.Step step = CampaignTicker.run(server, 60_000L);
        helper.assertTrue(step.lifeOrbExpired() && s.lifeOrbActive, "the Life Orb deadline must be reached");
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(health != null && health.getModifier(PlayerHealth.LIFE_ORB_PENALTY) == null, "a Life Orb holder gets no penalty");
        assertMillis(helper, s.finalPhaseRemainingMillis, timings.finalPhaseMillis() - timings.lifeOrbCountdownMillis(),
                "final time left at the Life Orb deadline");

        step = CampaignTicker.run(server, s.finalPhaseRemainingMillis);
        discardWithers(helper, player);
        helper.assertTrue(step.finalPhaseEnded(), "the final challenge must end");
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.COMPLETED, "one winner: COMPLETED expected, got " + s.finalPhaseState);
        helper.assertTrue(participant.result == FinalParticipant.Result.VICTORY, "the survivor with a Life Orb wins, got " + participant.result);
        helper.assertTrue(Permadeath.day() == PermadeathCalendar.FINAL_DAY, "the calendar stays on D60");
        long ended = s.finalPhaseEndedEpochMillis;

        PermadeathData reloaded = reloadProgression(helper);
        helper.assertTrue(reloaded.state().finalPhaseState == FinalPhaseState.COMPLETED, "the result must be saved");
        FinalParticipant saved = reloaded.state().finalParticipants.get(player.getUUID());
        helper.assertTrue(saved != null && saved.result == FinalParticipant.Result.VICTORY, "the participant result must be saved");

        CampaignTicker.run(server, timings.finalPhaseMillis());
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.COMPLETED && s.finalPhaseEndedEpochMillis == ended, "the result is recorded once");
        helper.assertTrue(!WitherSpawner.running(), "periodic Withers stop after the campaign (freezeAfterCampaign)");
        int afterCampaign = discardWithers(helper, player);
        helper.assertTrue(afterCampaign == 0, "no Wither after the campaign, got " + afterCampaign);
        resetTimers(helper.getLevel());
        finish(helper, player);
    }

    @BeforeBatch(batch = "tlifeorb")
    public static void timerLifeOrbBatch(ServerLevel level) {
        setDay(level, 60);
        quietD60();
        resetTimers(level);
    }

    @GameTest(template = EMPTY, batch = "tlifeorb", timeoutTicks = 200)
    public static void lifeOrbDeadlineWithoutOrbPenalizesAfterTheSyncGrace(GameTestHelper helper) {
        // The countdown is run down to 0 and the shared timers reach the deadline (lifeOrbActive is not set directly);
        // a player without a Life Orb then loses max health, but only after the sync grace.
        ServerPlayer player = mockPlayer(helper);
        helper.runAtTickTime(AFTER_SPAWN_PROTECTION, () -> {
            ProgressionState s = Permadeath.state();
            if (!s.lifeOrbActive) {
                CampaignTimers.startLifeOrbCountdown(s, Permadeath.timings());
                s.lifeOrbRemainingMillis = 0L;
            }
        });
        helper.runAtTickTime(AFTER_SPAWN_PROTECTION + LifeOrb.GRACE_TICKS + 10, () -> {
            AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
            boolean penalized = health != null && health.getModifier(PlayerHealth.LIFE_ORB_PENALTY) != null;
            boolean alive = player.isAlive();
            CampaignTimers.clearLifeOrb(Permadeath.state());
            helper.assertTrue(penalized, "a player without a Life Orb must get the penalty after the deadline");
            helper.assertTrue(alive, "the penalty only lowers the max health");
            finish(helper, player);
        });
    }

    @GameTest(template = EMPTY, batch = "tfinalfail")
    public static void finalChallengeFailsWhenEveryParticipantIsEliminated(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionState s = Permadeath.state();
        ServerPlayer a = mockPlayer(helper);
        ServerPlayer b = mockPlayer(helper);
        CampaignTicker.run(server, 0L);
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.ACTIVE, "final challenge must be active");
        FinalChallengeManager.onPermadeath(a);
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.ACTIVE, "one participant is still alive");
        helper.assertTrue(s.finalParticipants.get(a.getUUID()).eliminated, "the dead participant is eliminated");
        FinalChallengeManager.onPermadeath(b);
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.FAILED, "every participant eliminated: FAILED, got " + s.finalPhaseState);
        long ended = s.finalPhaseEndedEpochMillis;
        FinalChallengeManager.onPermadeath(b);
        CampaignTicker.run(server, Permadeath.timings().finalPhaseMillis());
        discardWithers(helper, a);
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.FAILED && s.finalPhaseEndedEpochMillis == ended, "the result is recorded once");
        helper.assertTrue(s.finalParticipants.values().stream().allMatch(p -> p.result == FinalParticipant.Result.DEFEAT), "both lose");
        resetTimers(helper.getLevel());
        a.connection.disconnect(Component.literal("Permadeath GameTest finished"));
        finish(helper, b);
    }

    @GameTest(template = EMPTY, batch = "tevents")
    public static void shulkerEventUsesActiveTime(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionState s = Permadeath.state();
        long duration = Permadeath.timings().shulkerEventMillis();
        ServerPlayer player = mockPlayer(helper);
        helper.assertTrue(ShulkerShellEvent.start(), "the event must start");
        helper.assertTrue(!ShulkerShellEvent.start(), "a running event is not restarted");
        assertMillis(helper, s.shulkerEventRemainingMillis, duration, "X2 Shulker Shells duration");
        helper.assertTrue(ShulkerShellEvent.shellsPerDrop() == 2, "two shells per drop during the event");
        CampaignTicker.run(server, duration - 1_000L);
        player.setGameMode(GameType.SPECTATOR);
        CampaignTicker.run(server, 60_000L);
        assertMillis(helper, s.shulkerEventRemainingMillis, 1_000L, "the event is paused without survivors");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(reloadProgression(helper).state().shulkerEventRemainingMillis == 1_000L, "the event time must be saved");
        CampaignTimers.Step step = CampaignTicker.run(server, 5_000L);
        helper.assertTrue(step.shulkerEventEnded() && ShulkerShellEvent.shellsPerDrop() == 1, "the event must end");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "tevents")
    public static void beginningCurseAndBlessingFollowTheirOwnClock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        PermadeathTimings timings = Permadeath.timings();
        BeginningCurseData data = BeginningCurseData.get(server);
        ServerPlayer player = mockPlayer(helper);
        UUID uuid = player.getUUID();
        long curse = timings.beginningCurseMillis();
        data.curse(uuid, curse);
        BeginningEffects.applyCurse(player, curse);
        helper.assertTrue(data.isCursed(uuid), "the player must be cursed");

        BeginningEffects.tick(server, 60_000L, true);
        assertMillis(helper, data.curseRemaining(uuid), curse - 60_000L, "curse time of an online player");
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        long expectedTicks = (curse - 60_000L) / 50L;
        helper.assertTrue(slowness != null && Math.abs(slowness.getDuration() - expectedTicks) <= 40,
                "Slowness must follow the curse clock: expected ~" + expectedTicks + " ticks, got " + slowness);
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "the curse includes Weakness");

        player.setGameMode(GameType.SPECTATOR);
        BeginningEffects.tick(server, 60_000L, true);
        assertMillis(helper, data.curseRemaining(uuid), curse - 60_000L, "the curse time of a spectator does not run");
        player.setGameMode(GameType.SURVIVAL);

        CompoundTag saved = data.save(new CompoundTag(), registries);
        assertMillis(helper, BeginningCurseData.factory().deserializer().apply(saved, registries).curseRemaining(uuid), curse - 60_000L,
                "the curse time must be saved");

        BeginningEffects.tick(server, curse, true);
        helper.assertTrue(!data.isCursed(uuid), "the curse must end with its time");
        helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !player.hasEffect(MobEffects.WEAKNESS),
                "Slowness and Weakness end together with the milk ban");

        long blessing = timings.beginningBlessingMillis();
        data.bless(uuid, blessing);
        BeginningEffects.applyBlessing(player, blessing);
        BeginningEffects.tick(server, 60_000L, true);
        assertMillis(helper, data.blessingRemaining(uuid), blessing - 60_000L, "blessing time of an online player");
        MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        helper.assertTrue(resistance != null && resistance.getAmplifier() == 1, "the blessing is Resistance II, got " + resistance);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        BeginningEffects.tick(server, 50L, true);
        helper.assertTrue(data.blessingRemaining(uuid) == 0L, "a blessing removed by milk is over");

        // Format 1 (absolute "Until"): at most the curse duration of the profile is left; expired curses are dropped.
        CompoundTag v1 = new CompoundTag();
        ListTag cursed = new ListTag();
        UUID longCurse = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        for (UUID id : List.of(longCurse, expired)) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", id);
            entry.putLong("Until", System.currentTimeMillis() + (id == longCurse ? 1000L * PermadeathTimings.HOUR : -1_000L));
            cursed.add(entry);
        }
        v1.put("Cursed", cursed);
        BeginningCurseData migrated = BeginningCurseData.factory().deserializer().apply(v1, registries);
        assertMillis(helper, migrated.curseRemaining(longCurse), curse, "an old curse is capped to the profile duration");
        helper.assertTrue(!migrated.isCursed(expired), "an expired old curse is dropped");
        finish(helper, player);
    }

    @GameTest(template = EMPTY, batch = "tmigration")
    public static void formatOneTimersBecomeRemainingTimeOnce(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        PermadeathTimings timings = Permadeath.timings();
        long now = System.currentTimeMillis();
        CompoundTag v1 = new CompoundTag();
        v1.putInt("FormatVersion", 1);
        v1.putString("Mode", Permadeath.mode().name());
        v1.putBoolean("Initialized", true);
        v1.putInt("MaxEffectiveDay", 60);
        v1.putIntArray("ExecutedMilestones", new int[]{10, 20, 30, 40, 50, 60});
        v1.putLong("DeathTrainEnd", now + 2L * PermadeathTimings.HOUR);
        v1.putLong("LifeOrbDeadline", now + PermadeathTimings.HOUR);
        v1.putLong("ShulkerEventEnd", now - 1L);
        PermadeathData data = PermadeathData.factory().deserializer().apply(v1, registries);
        ProgressionState s = data.state();
        helper.assertTrue(s.formatVersion == 1 && s.legacyTimers != null, "format 1 timers must be read as legacy timers");
        CompoundTag unmigrated = data.save(new CompoundTag(), registries);
        helper.assertTrue(unmigrated.getLong("DeathTrainEnd") == now + 2L * PermadeathTimings.HOUR,
                "a format 1 file saved before the migration keeps its values");

        List<String> log = TimerMigration.migrate(s, timings, now);
        long orb = Math.min(PermadeathTimings.HOUR, timings.lifeOrbCountdownMillis());
        helper.assertTrue(!log.isEmpty() && s.formatVersion == ProgressionState.CURRENT_FORMAT_VERSION && s.migratedFromVersion == 1,
                "the migration must run once and be logged");
        assertMillis(helper, s.deathTrainRemainingMillis, 2L * PermadeathTimings.HOUR, "storm: observable time, not scaled again");
        assertMillis(helper, s.shulkerEventRemainingMillis, 0L, "an expired event stays over (never negative)");
        assertMillis(helper, s.lifeOrbRemainingMillis, orb, "Life Orb time left, capped to the profile countdown");
        helper.assertTrue(s.finalPhaseState == FinalPhaseState.ACTIVE, "a D60 world gets an active final challenge");
        assertMillis(helper, s.finalPhaseRemainingMillis, orb + timings.finalPhaseMillis() - timings.lifeOrbCountdownMillis(),
                "final timeline aligned with the Life Orb countdown");
        helper.assertTrue(s.executedMilestones.contains(60), "milestones are kept");

        CompoundTag migrated = data.save(new CompoundTag(), registries);
        helper.assertTrue(!migrated.contains("DeathTrainEnd") && migrated.getCompound("FormatV1").contains("DeathTrainEnd"),
                "format 2 stores remaining time; the old values are only kept for reference");
        PermadeathData reloaded = PermadeathData.factory().deserializer().apply(migrated, registries);
        helper.assertTrue(TimerMigration.migrate(reloaded.state(), timings, now + PermadeathTimings.HOUR).isEmpty(),
                "a migrated world is never migrated again");
        assertMillis(helper, reloaded.state().deathTrainRemainingMillis, 2L * PermadeathTimings.HOUR, "remaining time after a restart");

        CompoundTag late = new CompoundTag();
        late.putInt("FormatVersion", 1);
        late.putInt("MaxEffectiveDay", 60);
        late.putLong("LifeOrbDeadline", now - 1_000L);
        ProgressionState expired = PermadeathData.factory().deserializer().apply(late, registries).state();
        TimerMigration.migrate(expired, timings, now);
        helper.assertTrue(expired.lifeOrbActive && expired.lifeOrbRemainingMillis == -1L, "an expired Life Orb deadline stays expired");
        helper.succeed();
    }
}
