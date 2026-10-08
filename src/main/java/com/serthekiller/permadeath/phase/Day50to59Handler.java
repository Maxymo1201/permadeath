package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.mechanics.MushroomSpawn;
import com.serthekiller.permadeath.mobs.BeginningMobs;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.ExplodingAnimals;
import com.serthekiller.permadeath.mobs.HostileMobConverter;
import com.serthekiller.permadeath.mobs.MobGoals;
import com.serthekiller.permadeath.mobs.MobReplacements;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.PigmanClasses;
import com.serthekiller.permadeath.mobs.SkeletonClasses;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/**
 * Fase 6 (D50-59): quantum/ender creepers, The Beginning opens, giant zombies, Wither Emperor, galactic cats,
 * explosive polar bears, Nether mob rain, mining without netherite tools hurts, x5 drowning (global), ...
 */
public final class Day50to59Handler extends LatePhaseHandler {
    @Override
    public String name() {
        return "Fase 6: Días 50-59 ";
    }

    @Override
    protected SkeletonClasses.Tier tier() {
        return SkeletonClasses.Tier.D50;
    }

    @Override
    protected int mobPassInterval() {
        return 60;
    }

    @Override
    protected String enderCreeperName() {
        return EnderMobs.ENDER_CREEPER_NAME;
    }

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        super.onPhaseStart(overworld);
        Texts.broadcast(overworld.getServer(), "§e=== Fase 6: Días 50-59 ===\n");
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
        return lateJoin(entity, level, loadedFromDisk);
    }

    /** Shared by D50 and D60: Zombie Gigante, Wither Emperador and pillager → evoker rolls (once per mob). */
    static boolean lateJoin(LivingEntity entity, ServerLevel level, boolean loadedFromDisk) {
        if (SpecialMobs.shouldBecomeGiant(entity, level)) {
            SpecialMobs.spawnGiantReplacing(entity, level);
            return true;
        }
        if (SpecialMobs.tryMakeEmperor(entity, level)) {
            return true;
        }
        return !loadedFromDisk && MobReplacements.pillagerToEvoker(entity, level);
    }

    @Override
    protected void handleCreeper(Creeper creeper, ServerLevel level) {
        if (MobTracking.tryClaim(creeper, "creeper_variant_d50")) {
            if (level.random.nextInt(100) < 20) {
                creeper.setCustomName(Component.literal(EnderMobs.ENDER_CREEPER_NAME));
                creeper.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
            } else {
                creeper.setCustomName(Component.literal(EnderMobs.QUANTUM_CREEPER_NAME));
            }
            if (level.dimension() == Level.END || level.dimension() == Level.NETHER || level.dimension() == BeginningDimension.LEVEL_KEY) {
                creeper.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
                if (level.dimension() == BeginningDimension.LEVEL_KEY) {
                    creeper.setCustomName(Component.literal(EnderMobs.ENDER_QUANTUM_CREEPER_NAME));
                }
            }
        }
    }

    @Override
    protected boolean handleBeginning(LivingEntity living, ServerLevel level) {
        return beginningJoin(living, level);
    }

    @Override
    protected void handleClassPigman(LivingEntity living, ServerLevel level) {
        PigmanClasses.handleClassPigman(living, level, true);
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
            golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 0, false, true));
            golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 1, false, true));
        } else if (entity instanceof EnderMan enderman) {
            enderman.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 1, false, true));
        } else if (entity instanceof Pillager pillager) {
            pillager.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobUtil.INFINITE, 0, false, false));
            pillager.setItemSlot(EquipmentSlot.MAINHAND, MobUtil.enchanted(level, new ItemStack(Items.CROSSBOW), ench(Enchantments.QUICK_CHARGE, 1)));
            MobGoals.ensureMachineGun(pillager);
        } else if (entity instanceof ZombifiedPiglin zp) {
            if (zp.getTags().contains(SpecialMobs.CARLOS_PIGMAN) || zp.getTags().contains(SpecialMobs.PROCESSED_STACK)) {
                return;
            }
            if (MobTracking.tryClaim(zp, "pigman_outcome_d50_59")) {
                MobUtil.equipArmor(zp, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                        new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
                MobUtil.name(zp, "§6Pigman Full Diamante");
            }
        } else if (entity instanceof Piglin piglin) {
            MobUtil.equipArmor(piglin, new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.GOLDEN_CHESTPLATE),
                    new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.GOLDEN_BOOTS));
            MobUtil.name(piglin, "§6Piglin Full Oro");
        } else if (entity instanceof Ravager ravager) {
            lateRavager(ravager, level);
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
        } else if (entity instanceof Vex vex) {
            vex.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 2, false, true));
        } else if (entity instanceof Slime) {
            MobUtil.setMaxHealth(entity, 200.0);
        } else if (entity instanceof Shulker shulker) {
            shulker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 5, false, true));
        } else if (entity instanceof Pufferfish pufferfish) {
            pufferfish.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 5, false, true));
            MobUtil.name(pufferfish, "§6Pufferfish invulnerable");
        }
    }

    /**
     * D50+ ravagers: in the Overworld every ravager outside a stack becomes a gold Ultra Ravager (plugin); the
     * others keep the Fabric Strength II + Resistance I.
     */
    static void lateRavager(Ravager ravager, ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD && !ravager.getTags().contains(SpecialMobs.PROCESSED_STACK)) {
            if (!SpecialMobs.isGoldUltraRavager(ravager)) {
                SpecialMobs.makeGoldUltraRavager(ravager);
            }
            return;
        }
        ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 1, false, true));
        ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 0, false, true));
    }

    @Override
    protected void afterCommonJoin(LivingEntity entity, ServerLevel level) {
        if (entity instanceof Enemy) {
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobUtil.INFINITE, 0, false, true, false));
        }
    }

    @Override
    protected boolean replacePhantomWithGhasts(Phantom phantom, ServerLevel level) {
        phantomGhasts(phantom, level);
        return false;
    }

    /**
     * D50+: with {@code nextInt(101) <= 1} (D50-59) or {@code <= 25} (D60) four Ender Ghasts spawn with the phantom
     * (plugin). Fabric used 1/100 and 26/100 and removed the phantom.
     */
    static void phantomGhasts(Phantom phantom, ServerLevel level) {
        if (level.random.nextInt(101) > DayRules.phantomGhastRoll(Permadeath.day())) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            EnderMobs.spawnEnderGhast(level, phantom.getX(), phantom.getY() + 3.0, phantom.getZ());
        }
    }

    // ----------------------------------------------------------------------------------------------- damage

    @Override
    protected boolean handleDamage(LivingEntity entity, DamageSource source) {
        return lateDamage(entity, source);
    }

    /** D50/D60 dodges and immunities. */
    static boolean lateDamage(LivingEntity entity, DamageSource source) {
        if (EnderMobs.isEnderCreeper(entity) && EnderMobs.isDodgeableEnderCreeper(source)) {
            EnderMobs.dodgeWithTeleport(entity);
            return true;
        }
        if (entity instanceof Pufferfish && !(source.getDirectEntity() instanceof Player)) {
            return true;
        }
        if (EnderMobs.isDefinitiveGhast(entity)) {
            if (source.getDirectEntity() instanceof Projectile) {
                if (entity.getRandom().nextFloat() < 0.3F) {
                    EnderMobs.dodgeWithTeleport(entity);
                    return true;
                }
            } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                EnderMobs.dodgeWithTeleport(entity);
                return true;
            }
        } else if (EnderMobs.isEnderGhast(entity) && EnderMobs.isDodgeableD50(source)) {
            EnderMobs.dodgeWithTeleport(entity);
            return true;
        }
        return entity instanceof WitherSkeleton && entity.getTags().contains(SpecialMobs.EMPEROR_TAG)
                && !(source.getDirectEntity() instanceof Player);
    }

    // ----------------------------------------------------------------------------------------------- death

    @Override
    public void onDeath(LivingEntity entity, DamageSource source, ServerLevel level) {
        super.onDeath(entity, source, level);
        lateDeath(entity, level);
    }

    static void lateDeath(LivingEntity entity, ServerLevel level) {
        SpecialMobs.handleGiantDrops(entity, level);
        SpecialMobs.handleEmperorDrops(entity, level);
        if (entity.getTags().contains(ExplodingAnimals.GALACTIC_CAT_TAG)) {
            ExplodingAnimals.startGalacticCurse(level, entity.getX(), entity.getY(), entity.getZ());
        }
        BeginningMobs.onVexDeath(entity);
    }

    @Override
    protected boolean keepsLoot(LivingEntity entity) {
        return lateKeepsLoot(entity);
    }

    static boolean lateKeepsLoot(LivingEntity entity) {
        if (entity instanceof WitherSkeleton && entity.getTags().contains(SpecialMobs.EMPEROR_TAG)) {
            return true;
        }
        return entity instanceof ZombifiedPiglin && entity.getRandom().nextInt(100) < 33;
    }

    @Override
    public void onSleepAttempt(CanPlayerSleepEvent event) {
        PhaseCommon.denySleep(event, PhaseCommon.PhantomReset.TEN_PERCENT);
    }

    /** Mining without a netherite tool hurts: 1 HP at D50, 16 HP at D60 (invulnerability frames reset). */
    static void miningPenalty(ServerPlayer player, float damage) {
        ItemStack tool = player.getMainHandItem();
        boolean netherite = tool.is(Items.NETHERITE_PICKAXE) || tool.is(Items.NETHERITE_SHOVEL) || tool.is(Items.NETHERITE_AXE)
                || tool.is(Items.NETHERITE_HOE) || tool.is(Items.NETHERITE_SWORD);
        if (!netherite) {
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), damage);
        }
    }

    @Override
    public void onBlockBroken(ServerPlayer player, BlockPos pos, BlockState state) {
        miningPenalty(player, 1.0F);
    }

    // ----------------------------------------------------------------------------------------------- ticks

    @Override
    protected void playerTick(ServerPlayer player, int day) {
        PhaseCommon.bedrockLevitation(player, true);
        PhaseCommon.soulSandSlowness(player, false);
        PhaseCommon.removeInvisibilityInBeginning(player);
        PhaseCommon.randomLevitation(player);
        netherMobRain(player);
    }

    @Override
    protected void levelTick(ServerLevel level) {
        if (level.getGameTime() % 20L == 0L) {
            BeginningMobs.cleanupHoneyHeads(level);
        }
    }

    /** D50: above y=117 in the Nether, 1/25 per tick a random hostile mob falls from y=147 within 12 blocks. */
    private static void netherMobRain(ServerPlayer player) {
        if (player.level().dimension() != Level.NETHER || !(player.level() instanceof ServerLevel nether) || player.getY() < 117.0) {
            return;
        }
        RandomSource random = nether.getRandom();
        if (random.nextInt(25) != 0) {
            return;
        }
        double x = player.getX() + (random.nextDouble() * 2.0 - 1.0) * 12.0;
        double z = player.getZ() + (random.nextDouble() * 2.0 - 1.0) * 12.0;
        double y = 147.0;
        if (!nether.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
            return;
        }
        LivingEntity mob = switch (random.nextInt(12)) {
            case 0 -> new Zombie(EntityType.ZOMBIE, nether);
            case 1 -> new Skeleton(EntityType.SKELETON, nether);
            case 2 -> new Creeper(EntityType.CREEPER, nether);
            case 3 -> new EnderMan(EntityType.ENDERMAN, nether);
            case 4 -> new Blaze(EntityType.BLAZE, nether);
            case 5 -> new WitherSkeleton(EntityType.WITHER_SKELETON, nether);
            case 6 -> new Husk(EntityType.HUSK, nether);
            case 7 -> new Stray(EntityType.STRAY, nether);
            case 8 -> new MagmaCube(EntityType.MAGMA_CUBE, nether);
            case 9 -> new Ghast(EntityType.GHAST, nether);
            case 10 -> new Piglin(EntityType.PIGLIN, nether);
            default -> new ZombifiedPiglin(EntityType.ZOMBIFIED_PIGLIN, nether);
        };
        mob.setPos(x, y, z);
        HostileMobConverter.convertToHostile(mob);
        nether.addFreshEntity(mob);
        nether.sendParticles(ParticleTypes.FLAME, x, y, z, 3, 0.3, 0.1, 0.3, 0.01);
    }
}
