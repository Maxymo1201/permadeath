package com.serthekiller.permadeath.mixin;

import com.serthekiller.permadeath.mobs.PigmanClasses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Riders that must not steer their mount (Fabric MixinMob): the Shulker on top of the D50 cave spider stack
 * and the pigman riding a "ghast pig". Without it the passenger Mob takes control of the vehicle's movement.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void permadeath$noControllingPassenger(CallbackInfoReturnable<LivingEntity> cir) {
        Mob self = (Mob) (Object) this;
        if (self instanceof CaveSpider && self.getFirstPassenger() instanceof Shulker
                || self instanceof Ghast && self.getTags().contains(PigmanClasses.GHAST_MOUNT_TAG)) {
            cir.setReturnValue(null);
        }
    }
}
