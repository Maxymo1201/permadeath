package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Supernova cats (D40-49), galactic cats (D50+) and explosive polar bears (D50+).
 *
 * <p>Fixes over the Fabric handlers: the D40 supernova countdown was decremented by 10 every tick (2 s instead
 * of the 20 s implied by its 10-tick interval constant); supernova cats born from a galactic cat (D50+) were
 * stored with an absolute tick in a map only processed for polar bears and never exploded.</p>
 */
public final class ExplodingAnimals {
    public static final String GALACTIC_CAT_TAG = "galactic_cat";
    public static final String SUPERNOVA_CAT_TAG = "SupernovaCat";
    public static final String GALACTIC_DRAGON_TAG = "galactic_dragon";
    public static final String GALACTIC_GOLEM_TAG = "permadeath:galactic_golem";
    private static final int SUPERNOVA_TICKS = 400;
    private static final int POLAR_BEAR_TICKS = 40;

    private static final Map<UUID, Integer> SUPERNOVA_TIMERS = new HashMap<>();
    private static final Map<UUID, Float> SUPERNOVA_POWER = new HashMap<>();
    private static final Set<UUID> WARNED_CATS = new HashSet<>();
    private static final Map<UUID, Integer> BEAR_TIMERS = new HashMap<>();

    private ExplodingAnimals() {
    }

    public static void reset() {
        SUPERNOVA_TIMERS.clear();
        SUPERNOVA_POWER.clear();
        WARNED_CATS.clear();
        BEAR_TIMERS.clear();
    }

    /** Called every tick of every level by the D40+ phase handlers (scans every 10 ticks, like Fabric). */
    public static void tick(ServerLevel level) {
        int day = Permadeath.day();
        if (level.getGameTime() % 10 == 0) {
            if (day < 50) {
                markSupernovaCats(level);
            } else {
                markGalacticCats(level);
                detectPolarBears(level);
            }
            processSupernovaTimers(level, 10);
        }
        if (day >= 50) {
            processPolarBearTimers(level);
        }
    }

    private static void markSupernovaCats(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (Cat cat : level.getEntitiesOfClass(Cat.class, player.getBoundingBox().inflate(200.0), c -> c.isAlive() && !c.isRemoved())) {
                if (player.distanceTo(cat) < 200.0F && WARNED_CATS.add(cat.getUUID())) {
                    startSupernova(level, cat, 250.0F);
                }
            }
        }
    }

    private static void startSupernova(ServerLevel level, Cat cat, float power) {
        cat.addTag(SUPERNOVA_CAT_TAG);
        Texts.broadcast(level.getServer(), Component.literal("¡Un gato supernova va a explotar en " + (int) cat.getX() + ", "
                + (int) cat.getY() + ", " + (int) cat.getZ() + "!").withStyle(ChatFormatting.RED));
        SUPERNOVA_TIMERS.put(cat.getUUID(), SUPERNOVA_TICKS);
        SUPERNOVA_POWER.put(cat.getUUID(), power);
    }

    private static void processSupernovaTimers(ServerLevel level, int elapsed) {
        Iterator<Map.Entry<UUID, Integer>> it = SUPERNOVA_TIMERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            if (!(entity instanceof Cat cat) || !cat.isAlive()) {
                it.remove();
                WARNED_CATS.remove(entry.getKey());
                SUPERNOVA_POWER.remove(entry.getKey());
                continue;
            }
            int left = entry.getValue();
            if (left <= 0) {
                float power = SUPERNOVA_POWER.getOrDefault(cat.getUUID(), 250.0F);
                level.explode(cat, cat.getX(), cat.getY(), cat.getZ(), power, Level.ExplosionInteraction.TNT);
                cat.discard();
                it.remove();
                SUPERNOVA_POWER.remove(entry.getKey());
            } else {
                entry.setValue(Math.max(0, left - elapsed));
            }
        }
    }

    private static void markGalacticCats(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (Cat cat : level.getEntitiesOfClass(Cat.class, player.getBoundingBox().inflate(200.0), c -> c.isAlive() && !c.isRemoved())) {
                if (cat.getTags().contains(SUPERNOVA_CAT_TAG)) {
                    continue;
                }
                if (player.distanceTo(cat) < 200.0F && WARNED_CATS.add(cat.getUUID())) {
                    cat.setCustomName(Component.literal("Gato Galáctico").withStyle(ChatFormatting.GOLD));
                    cat.setCustomNameVisible(true);
                    cat.addTag(GALACTIC_CAT_TAG);
                }
            }
        }
    }

    private static void detectPolarBears(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (PolarBear bear : level.getEntitiesOfClass(PolarBear.class, player.getBoundingBox().inflate(20.0), b -> b.isAlive() && !b.isRemoved())) {
                if (player.distanceTo(bear) < 20.0F) {
                    BEAR_TIMERS.putIfAbsent(bear.getUUID(), POLAR_BEAR_TICKS);
                }
            }
        }
    }

    private static void processPolarBearTimers(ServerLevel level) {
        Iterator<Map.Entry<UUID, Integer>> it = BEAR_TIMERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            if (!(entity instanceof PolarBear bear) || !bear.isAlive()) {
                it.remove();
            } else if (entry.getValue() <= 0) {
                level.explode(bear, bear.getX(), bear.getY(), bear.getZ(), 10.0F, Level.ExplosionInteraction.TNT);
                bear.discard();
                it.remove();
            } else {
                entry.setValue(entry.getValue() - 1);
            }
        }
    }

    /** Death of a galactic cat: small explosion and a random mob (42-sided roll, Fabric spawnRandomMobFromGalacticCat). */
    public static void onGalacticCatDeath(ServerLevel level, double x, double y, double z) {
        level.explode(null, x, y, z, 2.0F, Level.ExplosionInteraction.NONE);
        int roll = level.getRandom().nextInt(42);
        Entity spawned = switch (roll) {
            case 0 -> {
                CaveSpider spider = new CaveSpider(EntityType.CAVE_SPIDER, level);
                Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
                skeleton.setPos(x, y, z);
                level.addFreshEntity(skeleton);
                spider.setPos(x, y, z);
                level.addFreshEntity(spider);
                spider.startRiding(skeleton, true);
                yield null;
            }
            case 1 -> new Illusioner(EntityType.ILLUSIONER, level);
            case 2 -> new Skeleton(EntityType.SKELETON, level);
            case 3 -> new Creeper(EntityType.CREEPER, level);
            case 4 -> new EnderMan(EntityType.ENDERMAN, level);
            case 5 -> new Blaze(EntityType.BLAZE, level);
            case 6 -> new WitherSkeleton(EntityType.WITHER_SKELETON, level);
            case 7 -> new Phantom(EntityType.PHANTOM, level);
            case 8 -> new Drowned(EntityType.DROWNED, level);
            case 9 -> new Husk(EntityType.HUSK, level);
            case 10 -> new Stray(EntityType.STRAY, level);
            case 11 -> new Pillager(EntityType.PILLAGER, level);
            case 12 -> new Vindicator(EntityType.VINDICATOR, level);
            case 13 -> new Evoker(EntityType.EVOKER, level);
            case 14 -> new Endermite(EntityType.ENDERMITE, level);
            case 15 -> new Vex(EntityType.VEX, level);
            case 16 -> new Slime(EntityType.SLIME, level);
            case 17 -> new MagmaCube(EntityType.MAGMA_CUBE, level);
            case 18 -> new Ghast(EntityType.GHAST, level);
            case 19 -> new Warden(EntityType.WARDEN, level);
            case 20 -> new ZombifiedPiglin(EntityType.ZOMBIFIED_PIGLIN, level);
            case 21 -> new Piglin(EntityType.PIGLIN, level);
            case 22 -> new Guardian(EntityType.GUARDIAN, level);
            case 23 -> new ElderGuardian(EntityType.ELDER_GUARDIAN, level);
            case 24 -> new Witch(EntityType.WITCH, level);
            case 25 -> new Bogged(EntityType.BOGGED, level);
            case 26 -> {
                Ravager ravager = new Ravager(EntityType.RAVAGER, level);
                MobUtil.setMaxHealth(ravager, 500.0);
                ravager.setCustomName(Component.literal("Ultra Ravager").withStyle(ChatFormatting.GOLD));
                ravager.setCustomNameVisible(false);
                ravager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, false));
                ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 1, false, false));
                yield ravager;
            }
            case 27 -> {
                // Positioned before the riders are created (Fabric spawned Carlos and Jess at 0,0,0).
                Ravager ravager = new Ravager(EntityType.RAVAGER, level);
                ravager.setPos(x, y, z);
                SpecialMobs.setupUltraRavagerStack(ravager, level);
                yield ravager;
            }
            case 28 -> {
                // Fabric summoned the giant from an unpositioned zombie (the giant appeared at 0,0,0).
                Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
                zombie.setPos(x, y, z);
                SpecialMobs.summonGiant(zombie, level);
                yield null;
            }
            case 29 -> {
                Cat cat = new Cat(EntityType.CAT, level);
                cat.setPos(x, y, z);
                level.addFreshEntity(cat);
                WARNED_CATS.add(cat.getUUID());
                startSupernova(level, cat, 55.0F);
                yield null;
            }
            case 30 -> new WitherBoss(EntityType.WITHER, level);
            case 31 -> {
                EnderDragon dragon = new EnderDragon(EntityType.ENDER_DRAGON, level);
                dragon.addTag(GALACTIC_DRAGON_TAG);
                yield dragon;
            }
            case 32 -> {
                IronGolem golem = new IronGolem(EntityType.IRON_GOLEM, level);
                golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 3, false, false));
                golem.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 2, false, false));
                golem.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(golem, Player.class, true));
                golem.addTag(GALACTIC_GOLEM_TAG);
                yield golem;
            }
            case 33 -> new CaveSpider(EntityType.CAVE_SPIDER, level);
            case 34 -> {
                WitherSkeleton skeleton = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
                SpecialMobs.makeEmperor(skeleton, level);
                yield skeleton;
            }
            default -> new Husk(EntityType.HUSK, level);
        };
        if (spawned != null) {
            spawned.setPos(x, y, z);
            level.addFreshEntity(spawned);
        }
    }

}
