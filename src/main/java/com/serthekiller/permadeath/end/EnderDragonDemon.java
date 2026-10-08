package com.serthekiller.permadeath.end;

import com.serthekiller.permadeath.mechanics.GameplayRules;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * "PERMADEATH DEMON" (Fabric EnderDragonMixin), implemented with entity join/tick events instead of a mixin.
 * <ul>
 *     <li>Every new dragon: 2000 max health and full health (plugin config; Fabric started at 1000/1600), gold
 *     bold name (the vanilla boss bar shows it).</li>
 *     <li>≤ 600 HP (plugin EnragedHealth; Fabric 1/6 of its max): "ENRAGED PERMADEATH DEMON" (faster attacks,
 *     other fireball table).</li>
 *     <li>Perched 200 ticks: 360° spin with eight white clouds every 10 ticks.</li>
 *     <li>Flying, every 1200 ticks (800 enraged, plugin 60 s / 40 s): night vision II (48%), TNT circle (34%),
 *     lightning storm on a random player for 10 s (16%), wither skeleton (1%) or endermite (1%).</li>
 *     <li>In the End (plugin EndTask): one lightning bolt per second within 20 blocks of the exit portal, a
 *     barrage of six power-15 TNT every 30-90 s that does not break the island or hurt the dragon (only when the
 *     dragon is at least 15 blocks from the portal) and crystals heal half as much.</li>
 *     <li>Extra dragon fireballs while SITTING_ATTACKING with a mob target (as in Fabric; vanilla never sets a
 *     mob target on the dragon, so in practice it does not trigger).</li>
 * </ul>
 * The per-dragon counters are transient as in Fabric (enraged state is recomputed from health after a reload).
 * The Fabric "day &lt; 70" guard is always true in these builds (D60 is final).
 */
public final class EnderDragonDemon {
    public static final String NAME = "PERMADEATH DEMON";
    public static final String ENRAGED_NAME = "ENRAGED PERMADEATH DEMON";
    public static final String DRAGON_SPAWN_TAG = "PermadeathDragonSpawn";
    public static final String DRAGON_TNT_TAG = "PermadeathDragonTNT";
    private static final double MAX_HEALTH = 2000.0;
    private static final float START_HEALTH = 2000.0F;
    private static final float ENRAGED_HEALTH = 600.0F;
    private static final double FABRIC_MAX_HEALTH = 1600.0;
    private static final int[][] TNT_OFFSETS = {{3, -3}, {3, 3}, {3, 0}, {-3, 3}, {-3, -3}, {-3, 0}};
    private static final float SPIN_SPEED = 3.6F;
    private static final int PERCH_TRIGGER_TICKS = 200;
    private static final int LIGHTNING_ATTACK_DURATION = 200;
    private static final int LIGHTNING_STRIKE_INTERVAL = 20;
    private static final int FIREBALL_INTERVAL_NORMAL = 40;
    private static final int FIREBALL_INTERVAL_ENRAGED = 20;

    private static final class State {
        boolean enraged;
        int ticksAtPortal;
        int flyingAttackTimer;
        boolean spinning;
        int spinTicks;
        int cloudSpawnTimer;
        boolean lightningActive;
        int lightningTicks;
        int lightningStrikeTimer;
        ServerPlayer lightningTarget;
        int fireballTimer;
        int tntTimer = 600;
        float healthBeforeTick;
    }

    private static final Map<EnderDragon, State> STATES = new WeakHashMap<>();

    private EnderDragonDemon() {
    }

    public static void reset() {
        STATES.clear();
    }

    /**
     * Fabric did this in the dragon constructor, so a dragon loaded from disk keeps its saved health,
     * attributes and name (only a missing name is filled in, as the constructor did before reading NBT).
     */
    public static void onJoin(EnderDragon dragon, boolean loadedFromDisk) {
        if (!loadedFromDisk) {
            MobUtil.setMaxHealth(dragon, MAX_HEALTH);
            dragon.setHealth(START_HEALTH);
            dragon.setCustomName(name(NAME));
            return;
        }
        if (!dragon.hasCustomName()) {
            dragon.setCustomName(name(NAME));
        }
        var maxHealth = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() == FABRIC_MAX_HEALTH) {
            // Dragon of an older version (1600 max): raise it to 2000, keeping the damage already dealt.
            float health = dragon.getHealth();
            maxHealth.setBaseValue(MAX_HEALTH);
            dragon.setHealth(health + (float) (MAX_HEALTH - FABRIC_MAX_HEALTH));
        }
    }

    public static boolean isEnraged(EnderDragon dragon) {
        return dragon.getCustomName() != null && dragon.getCustomName().getString().contains("ENRAGED");
    }

    private static Component name(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static State state(EnderDragon dragon) {
        return STATES.computeIfAbsent(dragon, d -> new State());
    }

    private static EnderDragonPhase<?> phase(EnderDragon dragon) {
        return dragon.getPhaseManager().getCurrentPhase().getPhase();
    }

    // ------------------------------------------------------------------------------------------------ tick

    /** Fabric: HEAD of tickMovement. */
    public static void onTickPre(EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel level) || !dragon.isAlive()) {
            return;
        }
        State state = state(dragon);
        state.healthBeforeTick = dragon.getHealth();
        if (phase(dragon) == EnderDragonPhase.DYING) {
            // The dragon stops attacking while it flies to the podium to die.
            stopAttacks(state);
            return;
        }
        checkEnraged(dragon, state);
        handleSpinning(dragon, state, level);
        if (!state.spinning) {
            handleFlyingAttacks(dragon, state, level);
            handleExtraFireballs(dragon, state, level);
        }
        if (level.dimension() == Level.END && phase(dragon) != EnderDragonPhase.DYING) {
            if (dragon.tickCount % 20 == 0) {
                ambientLightning(level);
            }
            if (!state.spinning) {
                tntBarrage(dragon, state, level);
            }
        }
    }

    /** Plugin tickRandomLighting: a real bolt on the surface within 20 blocks of (0, 0). */
    private static void ambientLightning(ServerLevel level) {
        RandomSource random = level.getRandom();
        int x = (random.nextBoolean() ? 1 : -1) * random.nextInt(21);
        int z = (random.nextBoolean() ? 1 : -1) * random.nextInt(21);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinBuildHeight()) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x + 0.5, y, z + 0.5);
            level.addFreshEntity(bolt);
        }
    }

    /** Plugin tickTnTAttack: six TNT around the dragon every 30-90 s (first after 30 s), fuse 3 s. */
    private static void tntBarrage(EnderDragon dragon, State state, ServerLevel level) {
        if (--state.tntTimer > 0) {
            return;
        }
        state.tntTimer = (30 + level.getRandom().nextInt(61)) * 20;
        BlockPos portal = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
        if (dragon.position().distanceTo(Vec3.atCenterOf(portal)) < 15.0) {
            return;
        }
        for (int[] offset : TNT_OFFSETS) {
            PrimedTnt tnt = new PrimedTnt(level, dragon.getX() + offset[0], dragon.getY(), dragon.getZ() + offset[1], dragon);
            tnt.setFuse(60);
            tnt.addTag(DRAGON_TNT_TAG);
            level.addFreshEntity(tnt);
        }
    }

    /** Fabric: TAIL of tickMovement. */
    public static void onTickPost(EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel level)) {
            return;
        }
        State state = STATES.get(dragon);
        if (state == null) {
            return;
        }
        float healed = dragon.getHealth() - state.healthBeforeTick;
        if (healed > 0.0F && dragon.nearestCrystal != null && dragon.isAlive()) {
            // Plugin onDragonRegen: crystals heal the demon half as much.
            dragon.setHealth(dragon.getHealth() - healed / 2.0F);
        }
        if (!dragon.isAlive() || phase(dragon) == EnderDragonPhase.DYING) {
            // Killed while perched (health 0, no DYING phase) or dying: no spin, clouds or lightning during the death animation.
            stopAttacks(state);
            return;
        }
        if (state.spinning) {
            performSpin(dragon, state, level);
        }
        if (state.lightningActive) {
            continueLightning(state, level);
        }
    }

    private static void stopAttacks(State state) {
        state.spinning = false;
        state.ticksAtPortal = 0;
        state.flyingAttackTimer = 0;
        state.lightningActive = false;
        state.lightningTarget = null;
    }

    private static void checkEnraged(EnderDragon dragon, State state) {
        if (!state.enraged && dragon.getHealth() <= ENRAGED_HEALTH) {
            state.enraged = true;
            dragon.setCustomName(name(ENRAGED_NAME));
        }
    }

    private static void handleExtraFireballs(EnderDragon dragon, State state, ServerLevel level) {
        if (phase(dragon) != EnderDragonPhase.SITTING_ATTACKING) {
            state.fireballTimer = 0;
            return;
        }
        LivingEntity target = dragon.getTarget();
        if (target == null) {
            return;
        }
        state.fireballTimer++;
        if (state.fireballTimer >= (state.enraged ? FIREBALL_INTERVAL_ENRAGED : FIREBALL_INTERVAL_NORMAL)) {
            Vec3 from = dragon.position().add(0.0, dragon.getBbHeight() / 2.0, 0.0);
            Vec3 to = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
            DragonFireball fireball = new DragonFireball(level, dragon, to.subtract(from));
            fireball.setPos(from.x, from.y, from.z);
            level.addFreshEntity(fireball);
            state.fireballTimer = 0;
        }
    }

    private static void handleSpinning(EnderDragon dragon, State state, ServerLevel level) {
        EnderDragonPhase<?> phase = phase(dragon);
        boolean perched = phase == EnderDragonPhase.SITTING_SCANNING || phase == EnderDragonPhase.SITTING_ATTACKING
                || phase == EnderDragonPhase.SITTING_FLAMING;
        if (!perched) {
            state.spinning = false;
            state.ticksAtPortal = 0;
            return;
        }
        if (state.spinning) {
            return;
        }
        state.ticksAtPortal++;
        if (state.ticksAtPortal >= PERCH_TRIGGER_TICKS) {
            state.spinning = true;
            state.spinTicks = 0;
            state.cloudSpawnTimer = 0;
            level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 5.0F, 0.5F);
        }
    }

    private static void performSpin(EnderDragon dragon, State state, ServerLevel level) {
        state.spinTicks++;
        float current = dragon.getYRot();
        float next = current + SPIN_SPEED;
        if (next >= 360.0F) {
            next -= 360.0F;
        }
        dragon.setYRot(next);
        dragon.yRotO = current;
        dragon.setYHeadRot(next);
        dragon.yBodyRot = next;
        state.cloudSpawnTimer++;
        if (state.cloudSpawnTimer >= 10) {
            spawnCircularWhiteClouds(level, dragon.position());
            state.cloudSpawnTimer = 0;
        }
        if (state.spinTicks % 5 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY() + 1.0, dragon.getZ(), 10, 2.0, 1.0, 2.0, 0.02);
        }
        if (state.spinTicks % 100 == 0) {
            level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.MASTER, 3.0F, 1.5F);
        }
    }

    private static void spawnCircularWhiteClouds(ServerLevel level, Vec3 center) {
        float radius = 5.0F;
        for (int i = 0; i < 8; i++) {
            double radians = Math.toRadians(i * 45.0);
            double x = center.x + radius * Math.cos(radians);
            double z = center.z + radius * Math.sin(radians);
            double spawnY = center.y;
            BlockPos.MutableBlockPos search = new BlockPos.MutableBlockPos(x, center.y, z);
            for (int yOff = 0; yOff < 10; yOff++) {
                if (!level.getBlockState(search).isAir()) {
                    spawnY = search.getY() + 1.5;
                    break;
                }
                search.move(0, -1, 0);
            }
            AreaEffectCloud cloud = new AreaEffectCloud(level, x, spawnY, z);
            cloud.setRadius(2.5F);
            cloud.setDuration(60);
            cloud.setWaitTime(0);
            cloud.setParticle(ParticleTypes.CLOUD);
            level.addFreshEntity(cloud);
        }
    }

    private static void handleFlyingAttacks(EnderDragon dragon, State state, ServerLevel level) {
        EnderDragonPhase<?> phase = phase(dragon);
        if (phase == EnderDragonPhase.SITTING_SCANNING || phase == EnderDragonPhase.SITTING_ATTACKING) {
            state.flyingAttackTimer = 0;
            return;
        }
        state.flyingAttackTimer++;
        if (state.flyingAttackTimer < (state.enraged ? 800 : 1200)) {
            return;
        }
        float roll = level.getRandom().nextFloat();
        if (roll < 0.48F) {
            nightVisionAttack(level);
        } else if (roll < 0.82F) {
            tntCircle(dragon, level, dragon.position());
        } else if (roll < 0.98F) {
            startLightningAttack(dragon, state, level);
        } else if (roll < 0.99F) {
            spawnMinion(dragon, level, EntityType.WITHER_SKELETON.create(level), SoundEvents.WITHER_AMBIENT);
        } else {
            spawnMinion(dragon, level, EntityType.ENDERMITE.create(level), SoundEvents.ENDERMITE_AMBIENT);
        }
        state.flyingAttackTimer = 0;
    }

    private static void nightVisionAttack(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 1));
        }
    }

    private static void tntCircle(EnderDragon dragon, ServerLevel level, Vec3 center) {
        double radius = 5.0;
        for (double angle = 0.0; angle < 360.0; angle += 30.0) {
            double radians = Math.toRadians(angle);
            PrimedTnt tnt = new PrimedTnt(level, center.x + radius * Math.cos(radians), center.y, center.z + radius * Math.sin(radians), dragon);
            tnt.setFuse(80);
            tnt.addTag(GameplayRules.PERMADEATH_TNT);
            level.addFreshEntity(tnt);
        }
        level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 100.0F, 2.0F);
    }

    private static void startLightningAttack(EnderDragon dragon, State state, ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        state.lightningTarget = players.get(level.getRandom().nextInt(players.size()));
        state.lightningActive = true;
        state.lightningTicks = 0;
        state.lightningStrikeTimer = 0;
        level.playSound(null, dragon.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 10.0F, 1.0F);
        strikeNearTarget(state, level);
    }

    private static void continueLightning(State state, ServerLevel level) {
        if (state.lightningTarget == null || state.lightningTarget.isRemoved()) {
            state.lightningActive = false;
            state.lightningTarget = null;
            return;
        }
        state.lightningTicks++;
        state.lightningStrikeTimer++;
        if (state.lightningStrikeTimer >= LIGHTNING_STRIKE_INTERVAL) {
            strikeNearTarget(state, level);
            state.lightningStrikeTimer = 0;
        }
        if (state.lightningTicks >= LIGHTNING_ATTACK_DURATION) {
            state.lightningActive = false;
            state.lightningTarget = null;
        }
    }

    private static void strikeNearTarget(State state, ServerLevel level) {
        ServerPlayer target = state.lightningTarget;
        if (target == null) {
            return;
        }
        RandomSource random = level.getRandom();
        double x = target.getX() + (random.nextDouble() - 0.5) * 4.0;
        double z = target.getZ() + (random.nextDouble() - 0.5) * 4.0;
        // Fabric spawned the bolt in the dragon's level even if the player had left it; same here.
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, target.getY(), z);
            level.addFreshEntity(bolt);
        }
    }

    private static double findGroundY(ServerLevel level, double x, double startY, double z) {
        BlockPos.MutableBlockPos search = new BlockPos.MutableBlockPos(x, startY, z);
        for (int yOff = 0; yOff < 10; yOff++) {
            if (!level.getBlockState(search).isAir()) {
                return search.getY() + 1.0;
            }
            search.move(0, -1, 0);
        }
        return startY;
    }

    private static void spawnMinion(EnderDragon dragon, ServerLevel level, Mob mob, SoundEvent sound) {
        if (mob == null) {
            return;
        }
        Vec3 center = dragon.position();
        BlockPos pos = BlockPos.containing(center.x, findGroundY(level, center.x, center.y, center.z), center.z);
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null);
        mob.addTag(DRAGON_SPAWN_TAG);
        level.addFreshEntity(mob);
        level.playSound(null, pos, sound, SoundSource.MASTER, 3.0F, 1.0F);
    }
}
