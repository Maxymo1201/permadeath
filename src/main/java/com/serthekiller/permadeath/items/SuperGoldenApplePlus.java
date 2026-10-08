package com.serthekiller.permadeath.items;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Super Golden Apple +: golden apple effects plus Health Boost I for 5 minutes (not refreshed while active, as in the plugin). */
public class SuperGoldenApplePlus extends Item {
    public SuperGoldenApplePlus(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && !entity.hasEffect(MobEffects.HEALTH_BOOST)) {
            entity.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 6000, 0, false, true, true));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
