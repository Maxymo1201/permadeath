package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.mechanics.MushroomSpawn;
import com.serthekiller.permadeath.mobs.BeginningMobs;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.MobGoals;
import com.serthekiller.permadeath.mobs.MobReplacements;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.PigmanClasses;
import com.serthekiller.permadeath.mobs.SkeletonClasses;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.util.MobUtil;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/**
 * Fase 7 (D60, final): -16 max HP, 30 locked slots without relics, every creeper is an Ender Quantum Creeper,
 * no pigmen in the Nether, villagers → vindicators, guardians → elder guardians, demonic skeleton TNT
 * arrows, empty vanilla chests (loot modifier), wild snow golems, criptic shulkers, Life Orb and periodic
 * Wither (global mechanics), x10 drowning, 7% totem failure with 3 totems.
 */
public final class Day60Handler extends LatePhaseHandler {
    @Override
    public String name() {
        return "Fase 7: Día 60";
    }

    @Override
    protected SkeletonClasses.Tier tier() {
        return SkeletonClasses.Tier.D60;
    }

    @Override
    protected int mobPassInterval() {
        return 60;
    }

    @Override
    protected String enderCreeperName() {
        return EnderMobs.ENDER_QUANTUM_CREEPER_NAME;
    }

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        super.onPhaseStart(overworld);
        Texts.broadcast(overworld.getServer(), "§e=== Fase 7: Día 60 ===\n");
        MushroomSpawn.enable();
    }

    @Override
    public void onPhaseEnd(ServerLevel overworld) {
        super.onPhaseEnd(overworld);
        MushroomSpawn.disable();
    }

    // ----------------------------------------------------------------------------------------------- joins

    @Override
    protected boolean beforeCommonJoin(LivingEntity entity, ServerLevel level, boolean loadedFromDisk) {
        if (Day50to59Handler.lateJoin(entity, level, loadedFromDisk)) {
            return true;
        }
        if (!loadedFromDisk && MobReplacements.vindicatorRollD60(entity, level)) {
            return true;
        }
        if (entity instanceof ZombifiedPiglin pigman && !pigman.getTags().contains(SpecialMobs.PROCESSED_STACK)) {
            // Plugin: no pigmen at all on D60 (Fabric removed them only in the Nether).
            pigman.discard();
            return true;
        }
        return false;
    }

    @Override
    protected void handleCreeper(Creeper creeper, ServerLevel level) {
        if (MobTracking.tryClaim(creeper, "creeper_variant_d60")) {
            creeper.setCustomName(Component.literal(EnderMobs.ENDER_QUANTUM_CREEPER_NAME));
            if (level.dimension() == BeginningDimension.LEVEL_KEY) {
                Day50to59Handler.beginningCreeper(creeper);
            }
        }
        creeper.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
    }

    @Override
    protected boolean handleBeginning(LivingEntity living, ServerLevel level) {
        return beginningJoin(living, level);
    }

    @Override
    protected void insertEffect(LivingEntity entity, ServerLevel level) {
        if (entity instanceof Pillager pillager && MobTracking.isProcessed(pillager, insertEffectKey())) {
            MobGoals.ensureMachineGun(pillager);
            return;
        }
        if (entity instanceof Witch witch && MobTracking.isProcessed(witch, insertEffectKey())) {
            MobGoals.ensureImpossibleWitch(witch);
            return;
        }
        if (!(entity instanceof Mob mob) || !MobTracking.tryClaim(mob, insertEffectKey())) {
            return;
        }
        if (entity instanceof IronGolem golem) {
            golem.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 3, false, true));
            golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 3, false, true));
            golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 3, false, true));
            golem.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobUtil.INFINITE, 0, false, true));
        } else if (entity instanceof EnderMan enderman) {
            enderman.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 9, false, true));
        } else if (entity instanceof Pillager pillager) {
            pillager.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
            pillager.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.CROSSBOW), ench(Enchantments.QUICK_CHARGE, 1)));
            MobGoals.ensureMachineGun(pillager);
        } else if (entity instanceof Ravager ravager) {
            Day50to59Handler.lateRavager(ravager, level);
        } else if (entity instanceof Blaze blaze) {
            MobUtil.setMaxHealth(blaze, 200.0);
        } else if (entity instanceof Vindicator vindicator) {
            vindicator.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.DIAMOND_AXE), ench(Enchantments.SHARPNESS, 5)));
        } else if (entity instanceof Creeper creeper) {
            creeper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 1, false, true));
            creeper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, true));
        } else if (entity instanceof Witch witch) {
            Day40to49Handler.impossibleWitch(witch);
        } else if (entity instanceof Drowned drowned) {
            drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            MobUtil.setBase(drowned, Attributes.ATTACK_DAMAGE, 30.0);
        } else if (entity instanceof Bee bee) {
            MobUtil.setBase(bee, Attributes.ATTACK_DAMAGE, 30.0);
        } else if (entity instanceof Vex vex) {
            // Plugin: Strength III and attack 7 (Fabric 14).
            vex.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 2, false, true));
            vex.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 2, false, true));
            MobUtil.setBase(vex, Attributes.ATTACK_DAMAGE, 7.0);
        } else if (entity instanceof Slime && !entity.getTags().contains(PigmanClasses.MAGMA_MOUNT_TAG)) {
            // The "Mini" mount of the Magma Pigman keeps its 1 HP.
            MobUtil.setMaxHealth(entity, 200.0);
        } else if (entity instanceof Shulker shulker) {
            shulker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 5, false, true));
        } else if (entity instanceof Pufferfish pufferfish) {
            pufferfish.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 5, false, true));
            MobUtil.name(pufferfish, "§6Pufferfish invulnerable");
        }
    }

    /** Monsters' weapons: Fire Aspect II on melee weapons, Flame on bows/crossbows (once per mob). */
    @Override
    protected void afterCommonJoin(LivingEntity entity, ServerLevel level) {
        if (!(entity instanceof Enemy) || !(entity instanceof Mob mob) || !MobTracking.tryClaim(mob, "weapon_enchant_d60")) {
            return;
        }
        ItemStack stack = mob.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof PickaxeItem || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof ShovelItem || stack.getItem() instanceof HoeItem || stack.getItem() instanceof TridentItem) {
            MobUtil.enchanted(level, stack, ench(Enchantments.FIRE_ASPECT, 2));
        } else if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) {
            MobUtil.enchanted(level, stack, ench(Enchantments.FLAME, 1));
        }
    }

    @Override
    protected boolean replacePhantomWithGhasts(Phantom phantom, ServerLevel level) {
        Day50to59Handler.phantomGhasts(phantom, level);
        return false;
    }

    // ----------------------------------------------------------------------------------------------- damage

    @Override
    protected boolean handleDamage(LivingEntity entity, DamageSource source) {
        return Day50to59Handler.lateDamage(entity, source);
    }

    /**
     * Arrows of the "Ultra Esqueleto Demoníaco": hitting an entity spawns an instantly primed TNT (no arrow
     * damage), hitting a block explodes with power 6. Fabric scanned every entity of every level each tick
     * looking for stopped arrows; the impact event gives the same result without the scan.
     */
    @Override
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isDemonicArrow(arrow)) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity) {
            PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
            tnt.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
            tnt.setFuse(0);
            level.addFreshEntity(tnt);
            arrow.discard();
            event.setCanceled(true);
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            level.explode(arrow.getOwner(), arrow.getX(), arrow.getY(), arrow.getZ(), 6.0F, Level.ExplosionInteraction.TNT);
            arrow.discard();
            event.setCanceled(true);
        }
    }

    public static final String DEMONIC_ARROW_TAG = "DemonicArrow";

    private static boolean isDemonicArrow(AbstractArrow arrow) {
        if (arrow.getTags().contains(DEMONIC_ARROW_TAG)) {
            return true;
        }
        if (arrow.getOwner() instanceof Skeleton skeleton
                && skeleton.getTags().contains(SkeletonClasses.DEMONIC_SKELETON_TAG)) {
            arrow.addTag(DEMONIC_ARROW_TAG);
            return true;
        }
        return false;
    }

    // ----------------------------------------------------------------------------------------------- death

    @Override
    public void onDeath(LivingEntity entity, DamageSource source, ServerLevel level) {
        super.onDeath(entity, source, level);
        Day50to59Handler.lateDeath(entity, level);
    }

    @Override
    protected boolean keepsLoot(LivingEntity entity) {
        return Day50to59Handler.lateKeepsLoot(entity);
    }

    @Override
    public void onSleepAttempt(CanPlayerSleepEvent event) {
        PhaseCommon.denySleep(event, PhaseCommon.PhantomReset.TEN_PERCENT);
    }

    @Override
    public void onBlockBroken(ServerPlayer player, BlockPos pos, BlockState state) {
        Day50to59Handler.miningPenalty(player, 16.0F);
    }

    // ----------------------------------------------------------------------------------------------- ticks

    @Override
    protected void playerTick(ServerPlayer player, int day) {
        PhaseCommon.bedrockLevitation(player, true);
        PhaseCommon.soulSandSlowness(player, true);
        PhaseCommon.removeInvisibilityInBeginning(player);
        PhaseCommon.randomLevitation(player);
        trySpawnWildSnowGolem(player);
        SpecialMobs.trySpawnCripticShulker(player);
    }

    @Override
    protected void levelTick(ServerLevel level) {
        if (level.getGameTime() % 20L == 0L) {
            BeginningMobs.cleanupHoneyHeads(level);
        }
    }

    /** Overworld, 1/400 per player tick: a snow golem 16-40 blocks away (max 12 within 64 blocks). */
    private static void trySpawnWildSnowGolem(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || level.random.nextInt(400) != 0) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(64.0);
        if (level.getEntitiesOfClass(SnowGolem.class, area).size() >= 12) {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = 16.0 + level.random.nextDouble() * 24.0;
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos ground = new BlockPos(x, y - 1, z);
        BlockPos spawn = new BlockPos(x, y, z);
        if (level.getBlockState(ground).isSolid() && level.getBlockState(spawn).isAir() && level.getBlockState(spawn.above()).isAir()) {
            SnowGolem golem = new SnowGolem(EntityType.SNOW_GOLEM, level);
            golem.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            EventHooks.finalizeMobSpawn(golem, level, level.getCurrentDifficultyAt(spawn), MobSpawnType.NATURAL, null);
            level.addFreshEntity(golem);
        }
    }
}
