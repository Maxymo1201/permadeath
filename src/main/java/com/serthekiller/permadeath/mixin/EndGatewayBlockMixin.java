package com.serthekiller.permadeath.mixin;

import com.serthekiller.permadeath.beginning.BeginningPortal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * End gateways of the Overworld and The Beginning (Fabric EndGatewayMixin). NeoForge has no event that can
 * change a portal destination, so this is one of the few mixins of the port. The decision is made by
 * {@link BeginningPortal#gatewayOverride}.
 */
@Mixin(EndGatewayBlock.class)
public abstract class EndGatewayBlockMixin {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void permadeath$gatewayDestination(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<DimensionTransition> cir) {
        BeginningPortal.GatewayOverride override = BeginningPortal.gatewayOverride(level, entity);
        if (override.overridden()) {
            cir.setReturnValue(override.transition());
        }
    }
}
