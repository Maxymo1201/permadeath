package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.mechanics.MushroomSpawn;
import com.serthekiller.permadeath.mobs.EnderMobs;
import com.serthekiller.permadeath.mobs.MobGoals;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.PigmanClasses;
import com.serthekiller.permadeath.mobs.SkeletonClasses;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.util.MobUtil;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

import static com.serthekiller.permadeath.util.MobUtil.ench;

/**
 * Fase 5 (D40-49): The Beginning portal (milestone D40), -8 max HP, 5 locked inventory slots, PvP, zombies →
 * vindicators, wolves → cats (supernova), spiders → cave spiders, farm animals → ravagers, pigman jockeys,
 * Ultra Ravager stacks, impossible witches, mushroom spawns, new recipes (datapack reload).
 */
public final class Day40to49Handler extends LatePhaseHandler {
    @Override
    public String name() {
        return "Fase 5: Días 40-49";
    }

    @Override
    protected SkeletonClasses.Tier tier() {
        return SkeletonClasses.Tier.D40;
    }

    @Override
    protected int mobPassInterval() {
        // Fabric ran this pass every tick; every second is enough and keeps the server responsive.
        return 20;
    }

    @Override
    protected String enderCreeperName() {
        return EnderMobs.ENDER_CREEPER_NAME;
    }

    @Override
    public void onPhaseStart(ServerLevel overworld) {
        super.onPhaseStart(overworld);
        Texts.broadcast(overworld.getServer(), "§e=== Fase 5: Días 40-49 ===\n");
        MushroomSpawn.enable();
    }

    @Override
    public void onPhaseEnd(ServerLevel overworld) {
        super.onPhaseEnd(overworld);
        MushroomSpawn.disable();
    }

    @Override
    protected void handleClassPigman(LivingEntity living, ServerLevel level) {
        PigmanClasses.handleClassPigman(living, level, false);
    }

    @Override
    protected void insertEffect(LivingEntity entity, ServerLevel level) {
        if (entity instanceof IronGolem golem) {
            golem.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 3, false, true));
            golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 0, false, true));
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
            if (MobTracking.tryClaim(zp, "pigman_outcome_d40_49")) {
                MobUtil.equipArmor(zp, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                        new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
                MobUtil.name(zp, "§6Pigman Full Diamante");
            }
        } else if (entity instanceof Piglin piglin) {
            // The Ultra Ravager stack now comes from the pigman classes (plugin), not from 21 % of the piglins.
            if (MobTracking.tryClaim(piglin, "piglin_outcome_d40_49")) {
                MobUtil.equipArmor(piglin, new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.GOLDEN_CHESTPLATE),
                        new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.GOLDEN_BOOTS));
                MobUtil.name(piglin, "§6Piglin Full Oro");
            }
        } else if (entity instanceof Ravager ravager) {
            ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobUtil.INFINITE, 1, false, true));
            ravager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 0, false, true));
        } else if (entity instanceof Creeper creeper) {
            creeper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobUtil.INFINITE, 1, false, true));
            creeper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, true));
        } else if (entity instanceof Witch witch) {
            impossibleWitch(witch);
        }
    }

    static void impossibleWitch(Witch witch) {
        AttributeInstance maxHealth = witch.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() <= 26.0) {
            maxHealth.setBaseValue(52.0);
            witch.setHealth(52.0F);
        }
        if (!witch.hasCustomName()) {
            MobUtil.name(witch, "§6Bruja Imposible");
        }
        witch.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobUtil.INFINITE, 1, false, true));
        MobGoals.ensureImpossibleWitch(witch);
    }

    @Override
    protected boolean handleDamage(LivingEntity entity, DamageSource source) {
        if (EnderMobs.isEnderCreeper(entity) && EnderMobs.isDodgeableEnderCreeper(source)) {
            EnderMobs.dodgeWithTeleport(entity);
            return true;
        }
        if (entity instanceof Creeper creeper && entity.level().dimension() == Level.END && EnderMobs.isDodgeableD40(source)) {
            EnderMobs.dodgeWithTeleport(creeper);
            return true;
        }
        if (EnderMobs.isEnderGhast(entity) && EnderMobs.isDodgeableD40(source)) {
            EnderMobs.dodgeWithTeleport(entity);
            return true;
        }
        return false;
    }

    @Override
    public void onSleepAttempt(CanPlayerSleepEvent event) {
        PhaseCommon.denySleep(event, PhaseCommon.PhantomReset.ALWAYS_WITH_MESSAGE);
    }

    @Override
    protected void playerTick(ServerPlayer player, int day) {
        PhaseCommon.bedrockLevitation(player, false);
    }
}
