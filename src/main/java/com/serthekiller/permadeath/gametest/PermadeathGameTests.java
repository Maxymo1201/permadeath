package com.serthekiller.permadeath.gametest;

import com.mojang.authlib.GameProfile;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.end.EnderDragonDemon;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.mechanics.LockedSlots;
import com.serthekiller.permadeath.mechanics.PlayerHealth;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.phase.PhaseManager;
import com.serthekiller.permadeath.progression.DayController;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.recipes.RecipeFilter;
import com.serthekiller.permadeath.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
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
        Permadeath.state().lifeOrbDeadlineEpochMillis = -1L;
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
}
