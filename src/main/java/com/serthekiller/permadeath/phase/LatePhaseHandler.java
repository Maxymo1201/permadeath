package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.mechanics.GameplayRules;
import com.serthekiller.permadeath.mechanics.LockedSlots;
import com.serthekiller.permadeath.mobs.BeginningMobs;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.ExplodingAnimals;
import com.serthekiller.permadeath.mobs.HostileMobConverter;
import com.serthekiller.permadeath.mobs.MobGoals;
import com.serthekiller.permadeath.mobs.MobReplacements;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.PigmanClasses;
import com.serthekiller.permadeath.mobs.RandomEffects;
import com.serthekiller.permadeath.mobs.SkeletonClasses;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Behaviour shared by the D40-49, D50-59 and D60 handlers (the three Fabric classes were copies of each other
 * with different numbers). Subclasses supply the numbers and the phase-only rules.
 */
public abstract class LatePhaseHandler implements PhaseHandler {
    private static final String HOSTILE_ENDERMAN_TAG = "permadeath:hostile_enderman";
    private int mobPassCooldown;

    protected abstract SkeletonClasses.Tier tier();

    /** Ticks between two passes over all loaded mobs (replacements, farm animals, spider effects). */
    protected abstract int mobPassInterval();

    protected abstract String enderCreeperName();

    protected abstract void insertEffect(LivingEntity entity, ServerLevel level);

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        PhaseCommon.forEachLoadedEntity(overworld.getServer(), entity -> {
            if (entity instanceof LivingEntity living && entity.level() instanceof ServerLevel level) {
                passEntity(living, level, false);
            }
        });
    }

    @Override
    public void onPhaseEnd(ServerLevel overworld) {
        mobPassCooldown = 0;
        for (ServerPlayer player : overworld.getServer().getPlayerList().getPlayers()) {
            LockedSlots.clear(player);
        }
    }

    @Override
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (LockedSlots.isBlocker(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    // ----------------------------------------------------------------------------------------------- joins

    @Override
    public void onEntityJoin(Entity entity, ServerLevel level, boolean loadedFromDisk) {
        if (entity instanceof ItemEntity item && LockedSlots.isBlocker(item.getItem())) {
            item.discard();
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (beforeCommonJoin(living, level, loadedFromDisk)) {
            return;
        }
        if (living instanceof EnderMan enderman) {
            // Plugin: 1 % of the endermen outside the End hunt players (Fabric: 20 %, End included). Goals are not
            // saved, so the roll result is kept as a tag and the goal re-added on every load.
            if (level.dimension() != Level.END && !enderman.isRemoved()) {
                if (MobTracking.tryClaim(enderman, "enderman_hostile_roll") && level.random.nextInt(100) == 0) {
                    enderman.addTag(HOSTILE_ENDERMAN_TAG);
                }
                if (enderman.getTags().contains(HOSTILE_ENDERMAN_TAG)) {
                    enderman.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(enderman, Player.class, true));
                }
            }
            if (level.dimension() == Level.END && handleEndEnderman(enderman, level, loadedFromDisk)) {
                return;
            }
            if (level.dimension() == Level.NETHER) {
                Creeper creeper = new Creeper(EntityType.CREEPER, level);
                creeper.setPos(enderman.getX(), enderman.getY(), enderman.getZ());
                creeper.setCustomName(Component.literal(enderCreeperName()));
                creeper.addTag(GameplayRules.NETHER_CREEPER_TAG);
                level.addFreshEntity(creeper);
                enderman.discard();
                return;
            }
        }
        if (living instanceof Creeper creeper) {
            PhaseCommon.makePowered(creeper);
            handleCreeper(creeper, level);
        }
        if (handleBeginning(living, level)) {
            return;
        }
        if (MobReplacements.tryReplace(living, level, Permadeath.day())) {
            return;
        }
        // Like Fabric, every skeleton is (re)classed once per phase with the table of that phase.
        if (living instanceof Skeleton skeleton && MobTracking.tryClaim(skeleton, tier().classKey())) {
            SkeletonClasses.applyRandomClass(skeleton, level, skeleton.getVehicle(), tier());
            if (skeleton.isRemoved()) {
                return;
            }
        }
        handleClassPigman(living, level);
        insertEffect(living, level);
        if (living.isRemoved()) {
            return;
        }
        SpecialMobs.transformGiantMobs(living, level);
        afterCommonJoin(living, level);
        if (living instanceof Phantom phantom && PhaseCommon.enlargePhantom(phantom)) {
            if (replacePhantomWithGhasts(phantom, level)) {
                return;
            }
            SkeletonClasses.addRider(phantom, level, tier());
        }
        PhaseCommon.convertIfNeeded(living);
    }

    protected String insertEffectKey() {
        return tier().insertKey();
    }

    /** Phase specific joins evaluated first (giants, emperors). @return true when the entity was consumed. */
    protected boolean beforeCommonJoin(LivingEntity entity, ServerLevel level, boolean loadedFromDisk) {
        return false;
    }

    /** Phase specific tweaks applied to every surviving living entity (fire resistance, weapon enchants). */
    protected void afterCommonJoin(LivingEntity entity, ServerLevel level) {
    }

    protected void handleCreeper(Creeper creeper, ServerLevel level) {
        if (level.dimension() == Level.END || level.dimension() == Level.NETHER) {
            creeper.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
        }
    }

    protected boolean handleBeginning(LivingEntity living, ServerLevel level) {
        return false;
    }

    protected void handleClassPigman(LivingEntity living, ServerLevel level) {
    }

    /** D50/D60: some phantoms bring four Ender Ghasts. @return true when the phantom was consumed. */
    protected boolean replacePhantomWithGhasts(Phantom phantom, ServerLevel level) {
        return false;
    }

    /** End endermen: see {@link Day30to39Handler#endEndermanRoll}. */
    private boolean handleEndEnderman(EnderMan enderman, ServerLevel level, boolean loadedFromDisk) {
        return Day30to39Handler.endEndermanRoll(enderman, level, loadedFromDisk, enderCreeperName());
    }

    // ----------------------------------------------------------------------------------------------- damage

    @Override
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (handleDamage(entity, source) || Permadeath.day() >= 50 && ExplodingAnimals.onPolarBearHit(entity, source)) {
            event.setCanceled(true);
            return;
        }
        if (PigmanClasses.isGhastMount(entity)) {
            EnderMobs.teleportGhastPigman(entity);
        }
        if (entity instanceof ZombifiedPiglin pigman && pigman.getTags().contains(PigmanClasses.GHAST_PIGMAN_TAG)
                && pigman.getVehicle() instanceof Ghast ghast) {
            EnderMobs.teleportGhastPigman(ghast);
        }
        if (source.getDirectEntity() instanceof Projectile) {
            boolean magmaPig = entity instanceof ZombifiedPiglin && entity.getTags().contains(PigmanClasses.MAGMA_PIGMAN_TAG);
            boolean magmaMount = entity instanceof MagmaCube && entity.getTags().contains(PigmanClasses.MAGMA_MOUNT_TAG);
            if (magmaPig || magmaMount) {
                event.setCanceled(true);
            }
        }
    }

    /** Phase specific dodges/immunities. @return true to cancel the damage. */
    protected abstract boolean handleDamage(LivingEntity entity, DamageSource source);

    protected static boolean isArrow(DamageSource source) {
        return source.getDirectEntity() instanceof AbstractArrow;
    }

    // ----------------------------------------------------------------------------------------------- death

    @Override
    public void onDeath(LivingEntity entity, DamageSource source, ServerLevel level) {
        SpecialMobs.handleStackDrops(entity, level);
    }

    @Override
    public void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (SpecialMobs.isGoldUltraRavager(entity) && entity.level().dimension() != Level.NETHER) {
            // Plugin: the gold Ultra Ravager drops nothing outside the Nether.
            PhaseCommon.clearDrops(event);
            return;
        }
        if (!Day30to39Handler.clearsLoot(entity)) {
            return;
        }
        if (entity instanceof ZombifiedPiglin && entity.getTags().contains(SpecialMobs.CARLOS_PIGMAN)) {
            return;
        }
        if (keepsLoot(entity)) {
            return;
        }
        PhaseCommon.clearDrops(event);
    }

    protected boolean keepsLoot(LivingEntity entity) {
        return false;
    }

    // ----------------------------------------------------------------------------------------------- ticks

    @Override
    public void onLevelTick(ServerLevel level) {
        int day = Permadeath.day();
        for (ServerPlayer player : level.players()) {
            playerTick(player, day);
            LockedSlots.apply(player, day);
        }
        ExplodingAnimals.tick(level);
        if (level.getGameTime() % 20L == 0L) {
            SpecialMobs.stackRavagersBreakNetherrack(level);
        }
        levelTick(level);
        if (--mobPassCooldown <= 0) {
            mobPassCooldown = mobPassInterval();
            List<Entity> snapshot = new ArrayList<>();
            level.getAllEntities().forEach(snapshot::add);
            for (Entity entity : snapshot) {
                if (entity instanceof LivingEntity living && !living.isRemoved()) {
                    passEntity(living, level, true);
                }
            }
        }
    }

    protected abstract void playerTick(ServerPlayer player, int day);

    protected void levelTick(ServerLevel level) {
    }

    /** One periodic pass: replacements, farm animals → ravagers, hostility and the spider family. */
    private void passEntity(LivingEntity living, ServerLevel level, boolean withEffects) {
        if (MobReplacements.tryReplace(living, level, Permadeath.day())) {
            return;
        }
        EntityType<?> type = living.getType();
        if (!HostileMobConverter.isSpecialNeutral(type)) {
            if (type == EntityType.PIG || type == EntityType.COW || type == EntityType.SHEEP || type == EntityType.CHICKEN
                    || type == EntityType.MOOSHROOM) {
                HostileMobConverter.convertToRavager(living, level);
            } else if (!HostileMobConverter.isAlreadyHostile(living)) {
                HostileMobConverter.convertToHostile(living);
            }
        }
        if (withEffects && !living.isRemoved()) {
            applySpiderFamily(living, level);
        }
    }

    private void applySpiderFamily(LivingEntity entity, ServerLevel level) {
        int day = Permadeath.day();
        if (entity instanceof Spider spider && !(spider instanceof CaveSpider)) {
            CaveSpider caveSpider = new CaveSpider(EntityType.CAVE_SPIDER, level);
            caveSpider.moveTo(spider.getX(), spider.getY(), spider.getZ(), spider.getYRot(), spider.getXRot());
            level.addFreshEntity(caveSpider);
            spider.discard();
            if (level.random.nextInt(100) <= 10) {
                SpecialMobs.setupCaveSpiderStack(caveSpider, level);
            }
            return;
        }
        if (entity instanceof CaveSpider caveSpider) {
            if (MobTracking.tryClaim(caveSpider, "spider_effects")) {
                RandomEffects.apply(caveSpider, day, 5, level.random);
                if (caveSpider.getPassengers().isEmpty()) {
                    SkeletonClasses.addRider(caveSpider, level, tier());
                }
            }
        } else if (entity instanceof Silverfish || entity instanceof Endermite) {
            if (MobTracking.tryClaim((Mob) entity, "spider_effects")) {
                RandomEffects.apply(entity, day, 5, level.random);
            }
        } else if (entity instanceof Pig pig && pig.hasCustomName()) {
            if (!pig.getTags().contains("EffectsApplied")) {
                pig.addTag("EffectsApplied");
                RandomEffects.apply(pig, day, 5, level.random);
            }
        } else if (day >= 50 && entity instanceof Phantom phantom) {
            if (!phantom.getTags().contains("EffectsApplied")) {
                phantom.addTag("EffectsApplied");
                RandomEffects.apply(phantom, day, 3, level.random);
            }
        } else if (day >= 60 && entity instanceof Vindicator vindicator) {
            if (!vindicator.getTags().contains("EffectsApplied")) {
                vindicator.addTag("EffectsApplied");
                RandomEffects.apply(vindicator, day, 5, level.random);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------- helpers

    protected static boolean beginningJoin(LivingEntity living, ServerLevel level) {
        if (level.dimension() != BeginningDimension.LEVEL_KEY) {
            return false;
        }
        if (living instanceof WitherSkeleton skeleton) {
            BeginningMobs.pinkWitherSkeleton(skeleton, level);
        }
        if (living instanceof Ghast ghast && BeginningMobs.replaceGhast(ghast, level)) {
            return true;
        }
        if (living instanceof Vex vex) {
            BeginningMobs.vexDefinitivo(vex, level);
        }
        return false;
    }
}
