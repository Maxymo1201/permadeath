package com.serthekiller.permadeath.mobs;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;

/**
 * AI goals are not saved with the entity. The Fabric mod re-created some of them only through a Mixin on
 * Mob#tick (giants) and lost the others after a restart (Ender Ghast teleport, pillager machine gun, impossible
 * witch, galactic golem). They are restored here when the entity is loaded, independently of the current day.
 */
public final class GoalRestorer {
    private GoalRestorer() {
    }

    public static void restore(Entity entity) {
        if (entity instanceof Giant giant && giant.getTags().contains(SpecialMobs.GIANT_TAG)) {
            SpecialMobs.injectGiantAI(giant);
        } else if (entity instanceof Ghast ghast && (EnderMobs.isEnderGhast(ghast) || EnderMobs.isDefinitiveGhast(ghast))) {
            EnderMobs.addTeleportGoal(ghast);
        } else if (entity instanceof IronGolem golem && golem.getTags().contains(ExplodingAnimals.GALACTIC_GOLEM_TAG)) {
            golem.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(golem, Player.class, true));
        } else if (entity instanceof Pillager pillager && hasAny(pillager, SkeletonClasses.Tier.values())) {
            MobGoals.ensureMachineGun(pillager);
        } else if (entity instanceof Witch witch && hasAny(witch, SkeletonClasses.Tier.D40, SkeletonClasses.Tier.D50, SkeletonClasses.Tier.D60)) {
            MobGoals.ensureImpossibleWitch(witch);
        }
    }

    private static boolean hasAny(net.minecraft.world.entity.Mob mob, SkeletonClasses.Tier... tiers) {
        for (SkeletonClasses.Tier tier : tiers) {
            if (MobTracking.isProcessed(mob, tier.insertKey())) {
                return true;
            }
        }
        return false;
    }
}
