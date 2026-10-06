package com.serthekiller.permadeath.mixin;

import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Shulker riding another entity (D50 cave spider stack) is not a solid block for other entities and does not
 * push its own vehicle when it opens (Fabric ShulkerMixin isCollidable / moveEntities). The "ShulkerRojo"
 * teleport cancellation of the same Fabric mixin uses EntityTeleportEvent.EnderEntity instead.
 */
@Mixin(Shulker.class)
public abstract class ShulkerMixin {
    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void permadeath$notSolidWhileRiding(CallbackInfoReturnable<Boolean> cir) {
        if (((Shulker) (Object) this).isPassenger()) {
            cir.setReturnValue(false);
        }
    }

    /** Cosmetic part (pushing while opening): optional, so a renamed target can never prevent the server from starting. */
    @Inject(method = "onPeekAmountChange", at = @At("HEAD"), cancellable = true, require = 0)
    private void permadeath$noPushWhileRiding(CallbackInfo ci) {
        if (((Shulker) (Object) this).isPassenger()) {
            ci.cancel();
        }
    }
}
