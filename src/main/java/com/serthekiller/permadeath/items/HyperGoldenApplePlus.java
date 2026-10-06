package com.serthekiller.permadeath.items;

import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.mechanics.PlayerHealth;
import com.serthekiller.permadeath.progression.Permadeath;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Hyper Golden Apple +: +2 permanent hearts per apple (modifier "permadeath:permanent_health_boost").
 * Not edible before D40, max 1 before D60 and 2 from D60. The counter is stored in the world (Fabric:
 * data/hyper_apple_consumed.json, migrated) instead of a static map, and the bonus is re-applied on respawn by
 * an event handler instead of a listener registered every time an apple was eaten.
 */
public class HyperGoldenApplePlus extends Item {
    public HyperGoldenApplePlus(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && Permadeath.isRunning()) {
            int day = Permadeath.day();
            int consumed = Permadeath.state().hyperApplesConsumed.getOrDefault(player.getUUID(), 0);
            if (day < 40) {
                player.sendSystemMessage(Component.literal("Esta manzana es demasiado poderosa para ser consumida antes del día 40.")
                        .withStyle(ChatFormatting.RED));
                return stack;
            }
            if (consumed >= DayRules.maxHyperApples(day)) {
                player.sendSystemMessage(Component.literal(day < 60
                        ? "Ya has alcanzado el límite de 1 manzana antes del día 60."
                        : "Ya has consumido el máximo permitido (2 manzanas).").withStyle(ChatFormatting.RED));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.0F);
                return stack;
            }
            Permadeath.state().hyperApplesConsumed.put(player.getUUID(), consumed + 1);
            Permadeath.state().markChanged();
            PlayerHealth.applyHyperAppleBonus(player);
            player.setHealth(player.getHealth() + 4.0F);
            player.sendSystemMessage(Component.literal("!Han aumentado tus contenedores!)").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
