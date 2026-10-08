package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModBlocks;
import com.serthekiller.permadeath.registry.ModItems;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartSpawner;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Optional;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/**
 * Named custom mobs of the Fabric "Cambios" package: Ultra Ravager stack, Zombie Gigante, Wither Emperador,
 * Módulo de Muerte (Araña Abismal), Shulker Críptico and the D25 giant slimes/magma cubes/ghasts.
 */
public final class SpecialMobs {
    public static final String PROCESSED_STACK = "ProcessedStack";
    public static final String ULTRA_RAVAGER = "UltraRavager";
    /** Gold "Ultra Ravager" of D50+ (converted farm animals and natural ravagers), see {@link #makeGoldUltraRavager}. */
    public static final String GOLD_ULTRA_RAVAGER = "permadeath:ultra_ravager_gold";
    public static final String CARLOS_PIGMAN = "CarlosPigman";
    public static final String JESS_VILLAGER = "JessVillager";
    public static final String GIANT_TAG = "permadeath:giant_zombie";
    public static final String EMPEROR_TAG = "wither_emperor";
    public static final String POTION_IMMUNE = "PotionImmune";
    public static final String CRIPTIC_SHULKER_TAG = "permadeath:criptic_shulker";

    private SpecialMobs() {
    }

    // ------------------------------------------------------------------------------------------- Ultra Ravager

    /**
     * Ultra Ravager (240 HP) ridden by "Carlos el Esclavo" (150 HP) ridden by "Jess la Emperatriz" (500 HP), the
     * plugin numbers (Fabric Ravagercreator had the ravager and Jess swapped: 500 / 240).
     */
    public static void setupUltraRavagerStack(Ravager ravager, ServerLevel level) {
        ravager.addTag(PROCESSED_STACK);
        ravager.addTag(ULTRA_RAVAGER);
        MobUtil.setMaxHealth(ravager, 240.0);
        ravager.setCustomName(Component.literal("Ultra Ravager").withStyle(ChatFormatting.GREEN));
        ravager.setCustomNameVisible(false);
        ravager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, false));
        ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 1, false, false));

        ZombifiedPiglin pigman = new ZombifiedPiglin(EntityType.ZOMBIFIED_PIGLIN, level);
        pigman.addTag(PROCESSED_STACK);
        pigman.addTag(CARLOS_PIGMAN);
        MobUtil.setMaxHealth(pigman, 150.0);
        pigman.setCustomName(Component.literal("Carlos el Esclavo").withStyle(ChatFormatting.GREEN));
        pigman.setCustomNameVisible(false);
        MobUtil.setBase(pigman, Attributes.ARMOR, 12.0);
        MobUtil.setBase(pigman, Attributes.ARMOR_TOUGHNESS, 4.0);
        MobUtil.setBase(pigman, Attributes.ATTACK_DAMAGE, 9.0);
        MobUtil.setBase(pigman, Attributes.ATTACK_KNOCKBACK, 10.0);
        pigman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
        pigman.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        pigman.setPos(ravager.getX(), ravager.getY(), ravager.getZ());
        level.addFreshEntity(pigman);
        pigman.startRiding(ravager, true);

        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.addTag(PROCESSED_STACK);
        villager.addTag(JESS_VILLAGER);
        MobUtil.setMaxHealth(villager, 500.0);
        villager.setCustomName(Component.literal("Jess la Emperatriz").withStyle(ChatFormatting.GREEN));
        villager.setCustomNameVisible(false);
        villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        villager.setPos(ravager.getX(), ravager.getY(), ravager.getZ());
        level.addFreshEntity(villager);
        villager.startRiding(pigman, true);
    }

    /**
     * Drops of the stack (plugin): Carlos 32 gold ingots and Jess 2 golden apples (100 %, 33 % on D60). The stack
     * ravager drops nothing (Fabric gave a guaranteed totem, a totem farm that bypassed the totem tables). The gold
     * Ultra Ravager drops a totem only when it dies in the Nether (100 %, 33 % on D60).
     */
    public static void handleStackDrops(LivingEntity entity, ServerLevel level) {
        int chance = Permadeath.day() >= 60 ? 33 : 100;
        if (entity instanceof ZombifiedPiglin && entity.getTags().contains(CARLOS_PIGMAN)) {
            if (level.random.nextInt(100) < chance) {
                drop(level, entity, new ItemStack(Items.GOLD_INGOT, 32));
            }
        } else if (entity instanceof Villager && entity.getTags().contains(JESS_VILLAGER)) {
            if (level.random.nextInt(100) < chance) {
                drop(level, entity, new ItemStack(Items.GOLDEN_APPLE, 2));
            }
        } else if (isGoldUltraRavager(entity) && level.dimension() == Level.NETHER && level.random.nextInt(100) < chance) {
            drop(level, entity, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
    }

    /**
     * D40+ (plugin tickWorlds): every second, outside the End, the stack ravager breaks the netherrack around the
     * first 5 blocks it is looking at, so it can dig its way towards the players in the Nether.
     */
    public static void stackRavagersBreakNetherrack(ServerLevel level) {
        if (level.dimension() == Level.END) {
            return;
        }
        for (Ravager ravager : level.getEntities(EntityType.RAVAGER, r -> r.isAlive() && r.getTags().contains(ULTRA_RAVAGER))) {
            Vec3 eye = ravager.getEyePosition();
            Vec3 look = ravager.getViewVector(1.0F);
            for (int step = 1; step <= 10; step++) {
                BlockPos sight = BlockPos.containing(eye.add(look.scale(step * 0.5)));
                for (BlockPos pos : BlockPos.betweenClosed(sight.offset(-1, -1, -1), sight)) {
                    if (level.getBlockState(pos).is(Blocks.NETHERRACK)) {
                        level.destroyBlock(pos, false, ravager);
                    }
                }
            }
        }
    }

    /** D50+ gold "Ultra Ravager" (plugin): 500 HP, Speed II, Strength II, visible name. */
    public static void makeGoldUltraRavager(Ravager ravager) {
        ravager.addTag(GOLD_ULTRA_RAVAGER);
        MobUtil.setMaxHealth(ravager, 500.0);
        ravager.setCustomName(Component.literal("Ultra Ravager").withStyle(ChatFormatting.GOLD));
        ravager.setCustomNameVisible(true);
        ravager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, false));
        ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 1, false, false));
    }

    public static boolean isGoldUltraRavager(LivingEntity entity) {
        return entity instanceof Ravager && entity.getTags().contains(GOLD_ULTRA_RAVAGER);
    }

    /** D25-39: every ravager gets Speed II and Strength I (plugin). */
    public static void buffEarlyRavager(LivingEntity entity) {
        if (entity instanceof Ravager ravager && !ravager.getTags().contains(PROCESSED_STACK) && MobTracking.tryClaim(ravager, "ravager_buff_d25")) {
            ravager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, true));
            ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 0, false, true));
        }
    }

    // ------------------------------------------------------------------------------------------- Giant

    private static void configureGiant(Giant giant, double attackDamage) {
        MobUtil.setBase(giant, Attributes.FOLLOW_RANGE, 32.0);
        MobUtil.setMaxHealth(giant, 600.0);
        MobUtil.setBase(giant, Attributes.ATTACK_DAMAGE, attackDamage);
        MobUtil.setBase(giant, Attributes.MOVEMENT_SPEED, 0.35);
        giant.setCustomName(Component.literal("§eZombie Gigante"));
        giant.setCustomNameVisible(false);
        giant.addTag(GIANT_TAG);
        injectGiantAI(giant);
    }

    /** Galactic cat roll: replaces {@code entity} with a Zombie Gigante (attack 15). */
    public static Giant summonGiant(LivingEntity entity, ServerLevel level) {
        Giant giant = new Giant(EntityType.GIANT, level);
        giant.setPos(entity.getX(), entity.getY(), entity.getZ());
        giant.setYRot(entity.getYRot());
        configureGiant(giant, 15.0);
        level.addFreshEntity(giant);
        if (entity.isAddedToLevel()) {
            entity.discard();
        }
        return giant;
    }

    /**
     * D50+: zombies in plains under open sky become a Zombie Gigante (plugin: 1/500, 1/125 from D60, attack 2000;
     * Fabric used 5 % / 20 % and attack 30). Fabric re-rolled every time the zombie was loaded; the roll is now
     * done once per zombie.
     */
    public static boolean shouldBecomeGiant(LivingEntity entity, ServerLevel level) {
        if (!(entity instanceof Zombie) || entity instanceof Drowned || entity instanceof ZombifiedPiglin) {
            return false;
        }
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        BlockPos pos = entity.blockPosition();
        if (!level.getBiome(pos).is(Biomes.PLAINS) || !level.canSeeSky(pos)) {
            return false;
        }
        if (!(entity instanceof Mob mob) || !MobTracking.tryClaim(mob, "giant_roll")) {
            return false;
        }
        return level.random.nextInt(DayRules.giantOneIn(Permadeath.day())) == 0;
    }

    public static void spawnGiantReplacing(LivingEntity zombie, ServerLevel level) {
        Giant giant = new Giant(EntityType.GIANT, level);
        giant.setPos(zombie.getX(), zombie.getY(), zombie.getZ());
        giant.setYRot(zombie.getYRot());
        configureGiant(giant, 2000.0);
        level.addFreshEntity(giant);
        zombie.discard();
    }

    /** Vanilla giants have no AI; this is the Fabric injectGiantAI (also re-applied after a reload). */
    public static void injectGiantAI(Giant giant) {
        giant.goalSelector.removeAllGoals(goal -> true);
        giant.targetSelector.removeAllGoals(goal -> true);
        giant.goalSelector.addGoal(8, new LookAtPlayerGoal(giant, Player.class, 8.0F));
        giant.goalSelector.addGoal(8, new RandomLookAroundGoal(giant));
        giant.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(giant, 1.0));
        giant.goalSelector.addGoal(2, new MeleeAttackGoal(giant, 1.0, false));
        giant.targetSelector.addGoal(1, new HurtByTargetGoal(giant).setAlertOthers());
        giant.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(giant, Player.class, true));
    }

    /** Before D60 the giant drops "Arco de Gigante" (Power X). */
    public static void handleGiantDrops(LivingEntity entity, ServerLevel level) {
        if (entity instanceof Giant && entity.getTags().contains(GIANT_TAG) && Permadeath.day() < 60) {
            ItemStack bow = MobUtil.enchanted(level, new ItemStack(Items.BOW), ench(Enchantments.POWER, 10));
            bow.set(DataComponents.CUSTOM_NAME, Component.literal("Arco de Gigante").withStyle(ChatFormatting.AQUA));
            ItemEntity item = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), bow);
            item.setExtendedLifetime();
            level.addFreshEntity(item);
        }
    }

    // ------------------------------------------------------------------------------------------- Wither Emperor

    /** Turns a wither skeleton into the "Wither Emperador" (80 HP, attack 18, Power C / Punch V bow, banner). */
    public static void makeEmperor(WitherSkeleton emperor, ServerLevel level) {
        emperor.setCustomName(Component.literal("§6Wither Skeleton Emperador"));
        emperor.setCustomNameVisible(false);
        emperor.addTag(EMPEROR_TAG);
        MobUtil.setMaxHealth(emperor, 80.0);
        MobUtil.setBase(emperor, Attributes.ATTACK_DAMAGE, 18.0);
        MobUtil.setBase(emperor, Attributes.MOVEMENT_SPEED, 0.28);
        emperor.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.BOW),
                ench(Enchantments.POWER, 100), ench(Enchantments.PUNCH, 5)));
        emperor.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        emperor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        emperor.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
        emperor.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        emperor.setDropChance(EquipmentSlot.CHEST, 0.0F);
        emperor.setDropChance(EquipmentSlot.LEGS, 0.0F);
        emperor.setDropChance(EquipmentSlot.FEET, 0.0F);
        emperor.setItemSlot(EquipmentSlot.HEAD, createEmperorBanner(level));
        emperor.setDropChance(EquipmentSlot.HEAD, 0.0F);
    }

    /**
     * D50+: wither skeletons in nether biomes become emperors (plugin: 1/50, 1/13 from D60; Fabric 5 % / 20 %).
     * Fabric re-rolled on every load and reset the emperor health to 80; the roll is now done once per wither
     * skeleton.
     */
    public static boolean tryMakeEmperor(LivingEntity entity, ServerLevel level) {
        if (!(entity instanceof WitherSkeleton skeleton) || skeleton.getTags().contains(EMPEROR_TAG)) {
            return false;
        }
        if (!level.getBiome(entity.blockPosition()).is(BiomeTags.IS_NETHER)) {
            return false;
        }
        if (!MobTracking.tryClaim(skeleton, "emperor_roll")) {
            return false;
        }
        if (level.random.nextInt(DayRules.emperorOneIn(Permadeath.day())) != 0) {
            return false;
        }
        makeEmperor(skeleton, level);
        return true;
    }

    private static ItemStack createEmperorBanner(ServerLevel level) {
        ItemStack banner = new ItemStack(Items.BLACK_BANNER);
        banner.set(DataComponents.CUSTOM_NAME, Component.literal("§4Estandarte del Wither Emperador"));
        BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
        level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).get(ModBlocks.WITHER_EMPEROR_PATTERN)
                .ifPresent(pattern -> builder.add(pattern, DyeColor.RED));
        banner.set(DataComponents.BANNER_PATTERNS, builder.build());
        return banner;
    }

    /** Before D60 the emperor drops a netherite sword with 50% chance. */
    public static void handleEmperorDrops(LivingEntity entity, ServerLevel level) {
        if (entity instanceof WitherSkeleton && entity.getTags().contains(EMPEROR_TAG) && Permadeath.day() < 60
                && level.random.nextFloat() < 0.5F) {
            ItemEntity item = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), new ItemStack(Items.NETHERITE_SWORD));
            item.setExtendedLifetime();
            level.addFreshEntity(item);
        }
    }

    // ------------------------------------------------------------------------------------------- Death module

    /**
     * "Araña Abismal" (Fabric ModuloDeMuerte, branch below D75): 60 HP potion-immune cave spider carrying a red
     * "Shulker Explosivo" (150 HP) carrying a spawner minecart of Instant Damage IV splash potions.
     */
    public static void setupCaveSpiderStack(CaveSpider spider, ServerLevel level) {
        spider.addTag(PROCESSED_STACK);
        spider.addTag("UltraCaveSpider");
        MobUtil.setMaxHealth(spider, 60.0);
        spider.setCustomName(Component.literal("Araña Abismal").withStyle(ChatFormatting.DARK_PURPLE));
        spider.setCustomNameVisible(false);
        spider.addTag(POTION_IMMUNE);

        Shulker shulker = new Shulker(EntityType.SHULKER, level);
        shulker.addTag(PROCESSED_STACK);
        shulker.addTag("ShulkerRojo");
        shulker.setVariant(Optional.of(DyeColor.RED));
        MobUtil.setMaxHealth(shulker, 150.0);
        shulker.setCustomName(Component.literal("Shulker Explosivo").withStyle(ChatFormatting.RED));
        shulker.setCustomNameVisible(false);
        shulker.setPos(spider.getX(), spider.getY() + 2.0, spider.getZ());
        level.addFreshEntity(shulker);
        shulker.setNoGravity(true);
        shulker.startRiding(spider, true);
        shulker.addTag(POTION_IMMUNE);

        MinecartSpawner minecart = new MinecartSpawner(EntityType.SPAWNER_MINECART, level);
        minecart.setPos(spider.getX(), spider.getY(), spider.getZ());
        minecart.addTag(PROCESSED_STACK);
        minecart.addTag("PotionSpawnerMinecart");
        CompoundTag nbt = minecart.saveWithoutId(new CompoundTag());
        nbt.merge(potionSpawnerData());
        minecart.load(nbt);
        minecart.noPhysics = true;
        level.addFreshEntity(minecart);
        minecart.startRiding(shulker, true);
    }

    private static CompoundTag potionSpawnerData() {
        CompoundTag instantDamage = new CompoundTag();
        instantDamage.putString("id", "minecraft:instant_damage");
        instantDamage.putInt("amplifier", 3);
        instantDamage.putInt("duration", 1);
        ListTag customEffects = new ListTag();
        customEffects.add(instantDamage);
        CompoundTag potionContents = new CompoundTag();
        potionContents.putString("potion", "minecraft:strong_harming");
        potionContents.put("custom_effects", customEffects);
        CompoundTag components = new CompoundTag();
        components.put("minecraft:potion_contents", potionContents);
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:splash_potion");
        item.putInt("count", 1);
        item.put("components", components);
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:potion");
        entity.put("Item", item);
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        CompoundTag spawner = new CompoundTag();
        spawner.put("SpawnData", spawnData);
        spawner.putShort("Delay", (short) 40);
        spawner.putShort("MinSpawnDelay", (short) 30);
        spawner.putShort("MaxSpawnDelay", (short) 50);
        spawner.putShort("SpawnCount", (short) 1);
        spawner.putShort("MaxNearbyEntities", (short) 6);
        spawner.putShort("RequiredPlayerRange", (short) 16);
        spawner.putShort("SpawnRange", (short) 2);
        return spawner;
    }

    // ------------------------------------------------------------------------------------------- Criptic shulker

    /** D60+, Nether: 1/600 per player tick, at most 4 within 48 blocks, 20-40 blocks away. */
    public static void trySpawnCripticShulker(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.NETHER || Permadeath.day() < 60 || level.random.nextInt(600) != 0) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(48.0);
        if (level.getEntitiesOfClass(Shulker.class, area, s -> s.getTags().contains(CRIPTIC_SHULKER_TAG)).size() >= 4) {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = 20.0 + level.random.nextDouble() * 20.0;
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        BlockPos pos = findAirPocket(level, x, player.getBlockY(), z);
        if (pos != null) {
            spawnCripticShulker(level, pos);
        }
    }

    private static BlockPos findAirPocket(ServerLevel level, int x, int startY, int z) {
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[]{1, -1}) {
                int y = startY + dy * sign;
                if (y >= level.getMinBuildHeight() + 1 && y <= level.getMaxBuildHeight() - 2) {
                    BlockPos ground = new BlockPos(x, y - 1, z);
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(ground).isSolid() && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                        return pos;
                    }
                }
                if (dy == 0) {
                    break;
                }
            }
        }
        return null;
    }

    public static Shulker spawnCripticShulker(ServerLevel level, BlockPos pos) {
        Shulker shulker = new Shulker(EntityType.SHULKER, level);
        shulker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        shulker.setVariant(Optional.of(DyeColor.GREEN));
        shulker.setCustomName(Component.literal("§2Shulker Críptico"));
        shulker.setCustomNameVisible(false);
        shulker.addTag(CRIPTIC_SHULKER_TAG);
        EventHooks.finalizeMobSpawn(shulker, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        level.addFreshEntity(shulker);
        return shulker;
    }

    // ------------------------------------------------------------------------------------------- D25 giants

    /** D25+: GIGA Slime (size 15, 2x HP), GIGA MagmaCube (size 16), Ghast Demoníaco / Demonio Flotante. */
    public static void transformGiantMobs(LivingEntity entity, ServerLevel level) {
        int day = Permadeath.day();
        if (day < 25) {
            return;
        }
        if (entity instanceof Slime slime && !(entity instanceof MagmaCube)) {
            int size = slime.getSize();
            if (size >= 1 && size <= 4 && !slime.hasCustomName()) {
                slime.setCustomName(Component.literal("§6GIGA Slime"));
                slime.setCustomNameVisible(false);
                slime.setSize(15, true);
                MobUtil.multiplyMaxHealth(slime, 2.0);
            }
        }
        if (entity instanceof MagmaCube magma) {
            int size = magma.getSize();
            if (size >= 1 && size <= 4 && !magma.hasCustomName()) {
                magma.setCustomName(Component.literal("§6GIGA MagmaCube"));
                magma.setSize(16, true);
                magma.setCustomNameVisible(false);
            }
        }
        if (entity instanceof Ghast ghast && level.dimension() != Level.END
                && level.dimension() != BeginningDimension.LEVEL_KEY) {
            if (day < 40) {
                if (!ghast.hasCustomName() || !MobUtil.nameContains(ghast, "Demoníaco") && !MobUtil.nameContains(ghast, "Ender")) {
                    MobUtil.setMaxHealth(ghast, 40 + level.random.nextInt(21));
                    MobUtil.name(ghast, "§6Ghast Demoníaco");
                }
            } else if (!ghast.hasCustomName()) {
                // Both variants have 40-60 HP (plugin; Fabric gave the Demonio Flotante a fixed 60).
                MobUtil.setMaxHealth(ghast, 40 + level.random.nextInt(21));
                MobUtil.name(ghast, level.random.nextInt(100) > 75 ? "§6Ghast Demoníaco" : "§6Demonio Flotante");
            }
        }
    }

    /**
     * Custom netherite armour drops, plugin rules (runNetheriteCheck): only on D25-29, only for mobs killed by a
     * player, 10 % per piece - cave spider → helmet, GIGA Slime → chestplate, GIGA MagmaCube → leggings, Ghast
     * Demoníaco → boots. Fabric used 20 % for the helmet and the boots, kept dropping from D25 to D60 and did not
     * require a player kill (mob farms).
     */
    public static void handleNetheriteArmorDrops(LivingEntity entity, DamageSource source, ServerLevel level) {
        if (!DayRules.netheriteArmorDropDay(Permadeath.day()) || !(source.getEntity() instanceof Player)) {
            return;
        }
        ItemStack piece = null;
        if (entity instanceof CaveSpider) {
            piece = new ItemStack(ModItems.NETHERITE_HELMET.get());
        } else if (entity instanceof Slime && !(entity instanceof MagmaCube) && MobUtil.nameContains(entity, "GIGA Slime")) {
            piece = new ItemStack(ModItems.NETHERITE_CHESTPLATE.get());
        } else if (entity instanceof MagmaCube && MobUtil.nameContains(entity, "GIGA MagmaCube")) {
            piece = new ItemStack(ModItems.NETHERITE_LEGGINGS.get());
        } else if (entity instanceof Ghast && MobUtil.nameContains(entity, "Demoníaco")) {
            piece = new ItemStack(ModItems.NETHERITE_BOOTS.get());
        }
        if (piece != null && level.random.nextInt(100) < DayRules.NETHERITE_ARMOR_DROP_PERCENT) {
            drop(level, entity, piece);
        }
    }

    static void drop(ServerLevel level, LivingEntity entity, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack);
        if (!level.addFreshEntity(item)) {
            PermadeathMod.LOGGER.debug("[Permadeath] Could not drop {} at {}", stack, entity.blockPosition());
        }
    }
}
