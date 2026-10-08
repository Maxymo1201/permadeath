package com.serthekiller.permadeath.mobs;

import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import com.serthekiller.permadeath.util.ServerScheduler;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Ocelot;
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
 * <p>Fixes over the Fabric handlers: the D40 supernova countdown was decremented by 10 every tick (2 s);
 * supernova cats born from a galactic cat (D50+) were stored with an absolute tick in a map only processed for
 * polar bears and never exploded.</p>
 *
 * <p>Plugin numbers (Permadeath SpawnListener / EntityEvents / GatoGalacticoTask): supernova cats and ocelots are
 * named "Gato Supernova", explode after 30 s with power 200 and at most 2 are pending at the same time (extra cats
 * vanish); galactic cats announce the curse and count down 5 s before summoning; a polar bear that hits a player
 * explodes 0.5 s later with power 1.5 and fire, without breaking blocks (Fabric: any bear within 20 blocks
 * blew up 2 s later with power 10, breaking blocks).</p>
 */
public final class ExplodingAnimals {
    public static final String GALACTIC_CAT_TAG = "galactic_cat";
    public static final String SUPERNOVA_CAT_TAG = "SupernovaCat";
    public static final String GALACTIC_DRAGON_TAG = "galactic_dragon";
    public static final String GALACTIC_GOLEM_TAG = "permadeath:galactic_golem";
    private static final String BEAR_PRIMED_TAG = "permadeath:bear_primed";
    private static final int MAX_PENDING_SUPERNOVAS = 2;

    private static final Map<UUID, Integer> SUPERNOVA_TIMERS = new HashMap<>();
    private static final Map<UUID, Float> SUPERNOVA_POWER = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> SUPERNOVA_LEVEL = new HashMap<>();
    private static final Set<UUID> WARNED_CATS = new HashSet<>();

    private ExplodingAnimals() {
    }

    public static void reset() {
        SUPERNOVA_TIMERS.clear();
        SUPERNOVA_POWER.clear();
        SUPERNOVA_LEVEL.clear();
        WARNED_CATS.clear();
    }

    private static boolean isCat(Entity entity) {
        return entity instanceof Cat || entity instanceof Ocelot;
    }

    /** Called every tick of every level by the D40+ phase handlers (scans every 10 ticks, like Fabric). */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 10 == 0) {
            if (Permadeath.day() < 50) {
                markSupernovaCats(level);
            } else {
                markGalacticCats(level);
            }
            processSupernovaTimers(level, 10);
        }
    }

    private static void markSupernovaCats(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (Animal cat : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(200.0), c -> isCat(c) && c.isAlive() && !c.isRemoved())) {
                if (player.distanceTo(cat) < 200.0F && WARNED_CATS.add(cat.getUUID())) {
                    startSupernova(level, cat, DayRules.SUPERNOVA_POWER);
                }
            }
        }
    }

    private static void startSupernova(ServerLevel level, Animal cat, float power) {
        if (SUPERNOVA_TIMERS.size() >= MAX_PENDING_SUPERNOVAS) {
            // Plugin: with two supernovas already pending, the extra cat simply disappears.
            cat.discard();
            return;
        }
        cat.addTag(SUPERNOVA_CAT_TAG);
        MobUtil.name(cat, "§6Gato Supernova");
        Texts.broadcast(level.getServer(), Component.literal("Un gato supernova va a explotar en: " + cat.getBlockX() + " "
                + cat.getBlockY() + " " + cat.getBlockZ() + " (" + level.dimension().location() + ").").withStyle(ChatFormatting.RED));
        SUPERNOVA_TIMERS.put(cat.getUUID(), DayRules.SUPERNOVA_FUSE_TICKS);
        SUPERNOVA_POWER.put(cat.getUUID(), power);
        SUPERNOVA_LEVEL.put(cat.getUUID(), level.dimension());
    }

    /**
     * Counts down the pending supernovas of {@code level}. A cat whose chunk was unloaded is forgotten (and armed
     * again when a player comes back), so it never keeps one of the two pending slots forever.
     */
    private static void processSupernovaTimers(ServerLevel level, int elapsed) {
        Iterator<Map.Entry<UUID, Integer>> it = SUPERNOVA_TIMERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (SUPERNOVA_LEVEL.get(entry.getKey()) != level.dimension()) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || !isCat(entity) || !(entity instanceof LivingEntity cat) || !cat.isAlive()) {
                it.remove();
                WARNED_CATS.remove(entry.getKey());
                SUPERNOVA_POWER.remove(entry.getKey());
                SUPERNOVA_LEVEL.remove(entry.getKey());
                continue;
            }
            int left = entry.getValue();
            if (left <= 0) {
                float power = SUPERNOVA_POWER.getOrDefault(cat.getUUID(), DayRules.SUPERNOVA_POWER);
                level.explode(cat, cat.getX(), cat.getY(), cat.getZ(), power, Level.ExplosionInteraction.TNT);
                cat.discard();
                it.remove();
                SUPERNOVA_POWER.remove(entry.getKey());
                SUPERNOVA_LEVEL.remove(entry.getKey());
            } else {
                entry.setValue(Math.max(0, left - elapsed));
            }
        }
    }

    private static void markGalacticCats(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (Animal cat : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(200.0), c -> isCat(c) && c.isAlive() && !c.isRemoved())) {
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

    /**
     * D50+: a polar bear that hits a player does not deal the hit; it freezes, hisses and explodes 0.5 s later
     * (power 1.5, fire, no block damage). @return true to cancel the damage.
     */
    public static boolean onPolarBearHit(LivingEntity target, DamageSource source) {
        if (!(target instanceof ServerPlayer) || !(source.getDirectEntity() instanceof PolarBear bear)
                || !(bear.level() instanceof ServerLevel level) || bear.getTags().contains(BEAR_PRIMED_TAG)) {
            return false;
        }
        bear.addTag(BEAR_PRIMED_TAG);
        bear.setNoAi(true);
        level.playSound(null, bear.getX(), bear.getY(), bear.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
        ServerScheduler.schedule(10, () -> {
            if (bear.isAlive()) {
                level.explode(bear, bear.getX(), bear.getY(), bear.getZ(), 1.5F, true, Level.ExplosionInteraction.NONE);
                bear.discard();
            }
        });
        return true;
    }

    /**
     * Death of a galactic cat (plugin GatoGalacticoTask): the curse is announced, a 5 s countdown with a note
     * block sound follows, then a random mob is summoned and announced.
     */
    public static void startGalacticCurse(ServerLevel level, double x, double y, double z) {
        MinecraftServer server = level.getServer();
        String coords = (int) x + ", " + (int) y + ", " + (int) z;
        Texts.broadcast(server, Component.literal("La maldición de un Gato Galáctico ha comenzado en: " + coords).withStyle(ChatFormatting.RED));
        for (int i = 0; i < 5; i++) {
            int left = 5 - i;
            ServerScheduler.schedule(20 * i, () -> {
                Texts.broadcast(server, Component.literal("Un gato galáctico invocará un mob al azar en: ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(String.valueOf(left)).withStyle(ChatFormatting.AQUA)));
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.0F, 2.0F);
                }
            });
        }
        ServerScheduler.schedule(100, () -> {
            Entity summoned = onGalacticCatDeath(level, x, y, z);
            if (summoned != null) {
                Texts.broadcast(server, Component.literal("Un gato galáctico ha invocado un(a) ").withStyle(ChatFormatting.YELLOW)
                        .append(summoned.getDisplayName().copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                        .append(Component.literal(" (" + coords + ")").withStyle(ChatFormatting.GRAY)));
            }
        });
    }

    /**
     * Summon of a galactic cat curse: small explosion and a random mob (42-sided roll, Fabric
     * spawnRandomMobFromGalacticCat). @return the summoned mob (for the announcement).
     */
    public static Entity onGalacticCatDeath(ServerLevel level, double x, double y, double z) {
        level.explode(null, x, y, z, 2.0F, Level.ExplosionInteraction.NONE);
        int roll = level.getRandom().nextInt(42);
        Entity[] alreadySpawned = new Entity[1];
        Entity spawned = switch (roll) {
            case 0 -> {
                CaveSpider spider = new CaveSpider(EntityType.CAVE_SPIDER, level);
                Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
                skeleton.setPos(x, y, z);
                level.addFreshEntity(skeleton);
                spider.setPos(x, y, z);
                level.addFreshEntity(spider);
                spider.startRiding(skeleton, true);
                alreadySpawned[0] = spider;
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
                alreadySpawned[0] = SpecialMobs.summonGiant(zombie, level);
                yield null;
            }
            case 29 -> {
                Cat cat = new Cat(EntityType.CAT, level);
                cat.setPos(x, y, z);
                level.addFreshEntity(cat);
                WARNED_CATS.add(cat.getUUID());
                startSupernova(level, cat, 55.0F);
                alreadySpawned[0] = cat;
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
            return spawned;
        }
        return alreadySpawned[0];
    }

}
