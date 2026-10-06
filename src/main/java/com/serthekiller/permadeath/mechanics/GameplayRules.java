package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.data.SurvivalAchievementData;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Global rules that the Fabric mod implemented with Mixins, re-implemented with NeoForge events (one method
 * per former Mixin, the Mixin name is given in each Javadoc). Rules of days above 60 (D61-D70 content) are not
 * ported because D60 is final.
 */
public final class GameplayRules {
    public static final String POTION_SPAWNER_MINECART = "PotionSpawnerMinecart";
    public static final String PERMADEATH_TNT = "PermadeathTNT";
    public static final String DARK_HEARTS = "DarkHearts";
    public static final String LIGHTNING_CLOUD = "LightningCloud";

    private static final String[] CHEAT_PREFIXES = {"gamemode", "gm", "give", "clear", "effect", "xp", "experience", "enchant",
            "attribute", "data merge entity", "summon", "setblock", "fill", "clone", "spreadplayers"};
    private static final Map<UUID, Boolean> HAD_NIGHT_VISION = new HashMap<>();
    /** Creeper explosion of the current tick, used to drop its lingering effect cloud. */
    private static Vec3 lastCreeperExplosion;
    private static long lastCreeperExplosionTick = -1L;

    private GameplayRules() {
    }

    public static void reset() {
        HAD_NIGHT_VISION.clear();
        lastCreeperExplosion = null;
        lastCreeperExplosionTick = -1L;
    }

    private static int day() {
        return Permadeath.day();
    }

    // =========================================================================== entity ticks

    /** EntityTickEvent.Pre. */
    public static void onEntityTickPre(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof Creeper creeper) {
            quantumCreeper(creeper);
        } else if (entity instanceof Guardian guardian) {
            guardianBeam(guardian);
        } else if (entity instanceof PrimedTnt tnt) {
            permadeathTnt(tnt);
        } else if (entity instanceof AbstractMinecart minecart) {
            orphanedSpawnerMinecart(minecart);
        }
    }

    /** EntityTickEvent.Post. */
    public static void onEntityTickPost(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof AreaEffectCloud cloud) {
            customCloud(cloud);
        } else if (entity instanceof Bee bee) {
            beeStinger(bee);
        }
    }

    /** AreaEffectCloudMixin: damage/clear/lightning clouds created by the End fight (any day). */
    private static void customCloud(AreaEffectCloud cloud) {
        ServerLevel level = (ServerLevel) cloud.level();
        if (cloud.getTags().contains(LIGHTNING_CLOUD)) {
            if (cloud.tickCount % 20 == 0) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(cloud.getX(), cloud.getY(), cloud.getZ());
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                }
            }
            return;
        }
        if (cloud.tickCount % 10 != 0) {
            return;
        }
        boolean damageCloud = cloud.getParticle().getType() == ParticleTypes.CLOUD;
        boolean clearCloud = cloud.getParticle().getType() == ParticleTypes.CAMPFIRE_COSY_SMOKE;
        boolean darkHearts = cloud.getTags().contains(DARK_HEARTS);
        if (!damageCloud && !clearCloud && !darkHearts) {
            return;
        }
        LivingEntity owner = cloud.getOwner();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, cloud.getBoundingBox().inflate(0.5), e -> !e.isSpectator())) {
            if (damageCloud) {
                entity.hurt(owner != null ? cloud.damageSources().indirectMagic(cloud, owner) : cloud.damageSources().magic(), 10.0F);
            }
            if (clearCloud && entity instanceof Player) {
                clearEffectsPreserving(entity);
            }
            if (darkHearts) {
                entity.hurt(owner != null ? cloud.damageSources().indirectMagic(cloud, owner) : cloud.damageSources().magic(), 12.0F);
            }
        }
    }

    /** BeeEntityMixin: D20+ bees keep their stinger (and do not die after stinging). */
    private static void beeStinger(Bee bee) {
        if (bee.hasStung() && day() >= 20) {
            bee.setHasStung(false);
        }
    }

    /**
     * QuantumCreeperMixin: creepers named "Quantum Creeper"/"Ender Quantum Creeper" explode with radius 20 and,
     * from D60, have a 15 tick fuse.
     */
    private static void quantumCreeper(Creeper creeper) {
        if (!creeper.hasCustomName() || creeper.getCustomName() == null) {
            return;
        }
        String name = creeper.getCustomName().getString();
        if (!name.contains("Quantum Creeper")) {
            return;
        }
        creeper.explosionRadius = 20;
        if (day() >= 60) {
            creeper.maxSwell = 15;
        }
    }

    /** GuardianAttackGoalMixin: D40+ the guardian laser charges twice as fast. */
    private static void guardianBeam(Guardian guardian) {
        if (day() < 40 || !guardian.hasActiveAttackTarget()) {
            return;
        }
        for (WrappedGoal wrapped : guardian.goalSelector.getAvailableGoals()) {
            if (wrapped.isRunning() && wrapped.getGoal() instanceof Guardian.GuardianAttackGoal goal) {
                goal.attackTime++;
            }
        }
    }

    /** PrimedTntMixin: dragon TNT ("PermadeathTNT") throws every block within 6 blocks into the air. */
    private static void permadeathTnt(PrimedTnt tnt) {
        if (!tnt.getTags().contains(PERMADEATH_TNT) || tnt.getFuse() > 1) {
            return;
        }
        ServerLevel level = (ServerLevel) tnt.level();
        BlockPos center = tnt.blockPosition();
        int radius = 6;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.sqrt(x * x + y * y + z * z) > radius) {
                        continue;
                    }
                    BlockPos pos = center.offset(x, y, z);
                    var state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }
                    var falling = FallingBlockEntity.fall(level, pos, state);
                    falling.setDeltaMovement(x * 0.04 + (level.random.nextDouble() - 0.5) * 0.15, 0.6 + level.random.nextDouble() * 0.8,
                            z * 0.04 + (level.random.nextDouble() - 0.5) * 0.15);
                    falling.dropItem = false;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /** MinecartTickMixin: a potion spawner minecart that lost its shulker falls normally. */
    private static void orphanedSpawnerMinecart(AbstractMinecart minecart) {
        if (minecart.getTags().contains(POTION_SPAWNER_MINECART) && !minecart.isPassenger() && minecart.tickCount > 40) {
            minecart.noPhysics = false;
            minecart.setNoGravity(false);
        }
    }

    // =========================================================================== joins (immediate)

    /**
     * Immediate (non deferred) join rules: may cancel the join. Called from EntityJoinLevelEvent.
     */
    public static void onEntityJoinImmediate(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (entity instanceof AreaEffectCloud cloud && isCreeperCloud(cloud, level)) {
            // creepermixin: creepers never leave lingering effect clouds.
            event.setCanceled(true);
            return;
        }
        if (entity instanceof ThrownEnderpearl pearl && day() >= 60 && pearl.getOwner() instanceof ServerPlayer player
                && !event.loadedFromDisk()) {
            // EnderPearlCooldownMixin: D60 doubles the ender pearl cooldown (20 → 40 ticks).
            player.getCooldowns().addCooldown(Items.ENDER_PEARL, 40);
        }
        if (entity instanceof LargeFireball fireball && fireball.getOwner() instanceof Ghast ghast && !event.loadedFromDisk()) {
            ghastFireballPower(fireball, ghast);
        }
        if (entity instanceof ItemFrame frame && level.dimension() == Level.END && day() >= 40) {
            ItemStack item = frame.getItem();
            if (item.is(Items.ELYTRA) && item.getDamageValue() != item.getMaxDamage() - 1) {
                // ItemFrameMixin: D40+ the End ship elytras are almost broken.
                ItemStack damaged = item.copy();
                damaged.setDamageValue(damaged.getMaxDamage() - 1);
                frame.setItem(damaged, false);
            }
        }
    }

    /** LargeFireballMinix: explosion power of the custom ghasts (D25+). */
    private static void ghastFireballPower(LargeFireball fireball, Ghast ghast) {
        if (day() < 25 || !ghast.hasCustomName() || ghast.getCustomName() == null) {
            return;
        }
        String name = ghast.getCustomName().getString();
        if (name.contains("Demoníaco")) {
            fireball.explosionPower = 3 + ghast.level().getRandom().nextInt(3);
        } else if (name.contains("Ender Ghast")) {
            fireball.explosionPower = 6;
        } else if (name.contains("Demonio Flotante") || name.contains("ghast feliz")) {
            // Fabric compared with the misspelled "Demonio Flotate", so this ghast kept power 1.
            fireball.explosionPower = 0;
        } else if (name.contains("Ghast Definitivo")) {
            fireball.explosionPower = 12;
        }
    }

    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (event.getExplosion().getDirectSourceEntity() instanceof Creeper && event.getLevel() instanceof ServerLevel level) {
            Vec3 center = event.getExplosion().center();
            lastCreeperExplosion = center;
            lastCreeperExplosionTick = level.getGameTime();
        }
    }

    private static boolean isCreeperCloud(AreaEffectCloud cloud, ServerLevel level) {
        return lastCreeperExplosion != null && lastCreeperExplosionTick == level.getGameTime()
                && cloud.position().distanceToSqr(lastCreeperExplosion) < 1.0E-4;
    }

    /** ExplosionMixin: explosions never break blocks in The Beginning. */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == BeginningDimension.LEVEL_KEY) {
            event.getAffectedBlocks().clear();
        }
    }

    // =========================================================================== damage

    /** LivingIncomingDamageEvent rules that do not depend on the phase. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (entity.getTags().contains(SpecialMobs.POTION_IMMUNE) && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
            // LivingEntityMixin#blockPotionDamage
            event.setCanceled(true);
            return;
        }
        if (entity instanceof EnderMan enderman) {
            // EndermanMixin: endermen inside a green (happy villager) cloud regenerate and hold a totem.
            AABB box = enderman.getBoundingBox().inflate(1.0);
            for (AreaEffectCloud cloud : enderman.level().getEntitiesOfClass(AreaEffectCloud.class, box)) {
                if (cloud.getParticle().getType() == ParticleTypes.HAPPY_VILLAGER) {
                    enderman.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, 2));
                    enderman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /** MixinCaveSpider: D50+ cave spider hits add Poison III and Nausea (10 s). */
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof CaveSpider && day() >= 50 && event.getNewDamage() > 0.0F) {
            LivingEntity target = event.getEntity();
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, true));
        }
    }

    /** VehicleEntityMixin: the potion spawner minecart of the death module cannot be destroyed. */
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity().getTags().contains(POTION_SPAWNER_MINECART)) {
            event.setInvulnerable(true);
        }
    }

    /** WitherShieldBreakMixin: D60 Wither attacks ignore shields and disable them for 5 s. */
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (day() < 60 || !isFromWither(event.getDamageSource())) {
            return;
        }
        LivingEntity self = event.getEntity();
        if (self.isBlocking()) {
            ItemStack blocking = self.getUseItem();
            self.level().broadcastEntityEvent(self, (byte) 30);
            if (self instanceof Player player) {
                player.getCooldowns().addCooldown(blocking.getItem(), 100);
                player.stopUsingItem();
            }
        }
        event.setBlocked(false);
    }

    private static boolean isFromWither(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        if (direct instanceof WitherBoss || causing instanceof WitherBoss) {
            return true;
        }
        return direct instanceof WitherSkull skull && skull.getOwner() instanceof WitherBoss;
    }

    /** WitherBossMixin: Withers do not break blocks in The Beginning. */
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (event.getEntity() instanceof WitherBoss && event.getEntity().level().dimension() == BeginningDimension.LEVEL_KEY) {
            event.setCanceled(true);
        }
    }

    // =========================================================================== death / drops

    /** LivingEntityMixin#onShulkerDeath: a primed TNT (4 s) and a rare shell that survives it. */
    public static void onDeath(LivingEntity entity) {
        if (!(entity instanceof Shulker shulker) || !(shulker.level() instanceof ServerLevel level)) {
            return;
        }
        PrimedTnt tnt = new PrimedTnt(level, shulker.getX(), shulker.getY(), shulker.getZ(), null);
        tnt.setFuse(80);
        level.addFreshEntity(tnt);
        int d = day();
        float chance = d < 40 ? (d == 35 ? 0.4F : 0.2F) : 0.02F;
        if (shulker.getRandom().nextFloat() < chance) {
            ItemEntity shell = new ItemEntity(level, shulker.getX(), shulker.getY(), shulker.getZ(), new ItemStack(Items.SHULKER_SHELL));
            shell.setInvulnerable(true);
            shell.setPickUpDelay(85);
            level.addFreshEntity(shell);
        }
    }

    /** LivingEntityMixin#cancelShulkerVanillaDrops: shulkers never drop their vanilla loot. */
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Shulker) {
            event.getDrops().clear();
        }
    }

    // =========================================================================== projectiles

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        if (projectile instanceof ShulkerBullet bullet) {
            // ShulkerBulletMixin: every shulker bullet impact primes a TNT (4 s).
            PrimedTnt tnt = new PrimedTnt(level, bullet.getX(), bullet.getY(), bullet.getZ(), null);
            tnt.setFuse(80);
            level.addFreshEntity(tnt);
        } else if (projectile instanceof LlamaSpit spit && day() >= 50 && hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living) {
            // MixinLlamaSpit: D50+ poison III (30 s), nausea and a strong push.
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 2, false, true));
            living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, true));
            Vec3 dir = living.position().subtract(spit.position()).normalize();
            living.setDeltaMovement(living.getDeltaMovement().add(dir.x * 1.2, 0.4, dir.z * 1.2));
            living.hurtMarked = true;
        } else if (projectile instanceof LargeFireball fireball && hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player player && fireball.getOwner() instanceof Ghast ghast
                && MobUtil.nameContains(ghast, "Demonio Flotante")) {
            // LargeFireballMinix#onHitPlayer
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 400, 49));
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 4));
        }
    }

    // =========================================================================== effects

    /** MobEffectEvent.Applicable: hero of the village cap and doubled mining fatigue (D50+). */
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Holder<MobEffect> effect = instance.getEffect();
        int d = day();
        if (d >= 50 && effect == MobEffects.HERO_OF_THE_VILLAGE && instance.getDuration() > 6000) {
            // RaidHeroEffectMixin (its equals() always failed in Fabric, the cap never applied).
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            entity.forceAddEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, instance.getAmplifier()), null);
        } else if (d >= 50 && effect == MobEffects.DIG_SLOWDOWN && !instance.isAmbient()) {
            // MixinLivingEntityFatigue: double duration, stored as ambient so it is not doubled again.
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            entity.forceAddEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, instance.getDuration() * 2, instance.getAmplifier(), true,
                    instance.isVisible()), null);
        }
    }

    /**
     * MixinLivingEntityFatigue#clearStatusEffects: from D50 milk and totems do not remove Mining Fatigue, Hunger
     * or Poison.
     */
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (day() < 50 || event.getCure() == null) {
            return;
        }
        if ((event.getCure() == EffectCures.MILK || event.getCure() == EffectCures.PROTECTED_BY_TOTEM) && isPreserved(event.getEffect())) {
            event.setCanceled(true);
        }
    }

    private static boolean isPreserved(Holder<MobEffect> effect) {
        return effect == MobEffects.DIG_SLOWDOWN || effect == MobEffects.HUNGER || effect == MobEffects.POISON;
    }

    private static void clearEffectsPreserving(LivingEntity entity) {
        if (day() < 50) {
            entity.removeAllEffects();
            return;
        }
        for (MobEffectInstance instance : java.util.List.copyOf(entity.getActiveEffects())) {
            if (!isPreserved(instance.getEffect())) {
                entity.removeEffect(instance.getEffect());
            }
        }
    }

    /** MixinPlayerFoodEffects: harmful foods (D50+) and pumpkin pie (saturation D50-59, harm D60). */
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack item = event.getItem();
        int d = day();
        if (item.is(Items.PUMPKIN_PIE)) {
            if (d >= 60) {
                player.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 3));
            } else if (d >= 50) {
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 100, 0, false, true));
            }
            return;
        }
        if (d < 50) {
            return;
        }
        if (item.is(Items.SPIDER_EYE)) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, MobUtil.INFINITE, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, MobUtil.INFINITE, 0, false, true));
        } else if (item.is(Items.ROTTEN_FLESH)) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, MobUtil.INFINITE, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MobUtil.INFINITE, 0, false, true));
        } else if (item.is(Items.POISONOUS_POTATO)) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, MobUtil.INFINITE, 1, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MobUtil.INFINITE, 0, false, true));
        } else if (item.is(Items.PUFFERFISH)) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, MobUtil.INFINITE, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, MobUtil.INFINITE, 1, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, MobUtil.INFINITE, 1, false, true));
        }
    }

    // =========================================================================== players

    /** PlayerMixin: D30-39, in the End with the dragon alive, losing Night Vision leaves a "dark hearts" cloud. */
    public static void onPlayerTick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.END) {
            HAD_NIGHT_VISION.remove(player.getUUID());
            return;
        }
        int d = day();
        if (d < 30 || d >= 40 || !EnderMobs.isDragonAlive(level)) {
            return;
        }
        boolean hasNightVision = player.hasEffect(MobEffects.NIGHT_VISION);
        if (Boolean.TRUE.equals(HAD_NIGHT_VISION.get(player.getUUID())) && !hasNightVision) {
            AreaEffectCloud cloud = new AreaEffectCloud(level, player.getX(), player.getY(), player.getZ());
            cloud.setRadius(1.5F);
            cloud.setDuration(600);
            cloud.setWaitTime(0);
            cloud.setParticle(ParticleTypes.DAMAGE_INDICATOR);
            cloud.addTag(DARK_HEARTS);
            level.addFreshEntity(cloud);
        }
        HAD_NIGHT_VISION.put(player.getUUID(), hasNightVision);
    }

    /** BucketItemMixin: from D50 empty buckets cannot pick up water or lava. */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(Items.BUCKET) || day() < 50 || event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        BlockHitResult hit = event.getLevel().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            FluidState fluid = event.getLevel().getFluidState(hit.getBlockPos());
            if (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }

    /** ShulkerMixin#cancelTeleport: the red shulker of the death module never teleports. */
    public static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (event.getEntity() instanceof Shulker && event.getEntity().getTags().contains("ShulkerRojo")) {
            event.setCanceled(true);
        }
    }

    /** MagmaCubeSpawnLimitMixin: D25+ at most 20 magma cubes within 128 blocks in basalt deltas. */
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != EntityType.MAGMA_CUBE || day() < 25) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!event.getLevel().getBiome(pos).is(Biomes.BASALT_DELTAS)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (level.getEntitiesOfClass(MagmaCube.class, new AABB(pos).inflate(128.0)).size() >= 20) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /** CommandMonitorMixin: non-operators using cheat commands disqualify the (inactive) D70 achievement. */
    public static void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || source.hasPermission(2)) {
            return;
        }
        String command = event.getParseResults().getReader().getString().trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        for (String prefix : CHEAT_PREFIXES) {
            if (command.startsWith(prefix)) {
                SurvivalAchievementData.get(player.server).flagCheatDetected(player.getUUID(), "comando: /" + command);
                break;
            }
        }
    }
}
