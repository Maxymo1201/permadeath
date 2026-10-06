package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;

/**
 * D40+: inventory slots blocked with structure voids (Fabric handleInventoryRestriction).
 * <ul>
 *     <li>D40-59: slots 4, 13, 22, 31 (middle column) and 40 (offhand) unless the player carries the End Relic.</li>
 *     <li>D60: without relics the D40 slots plus 25 more; with the End Relic the 25 extra slots plus the
 *     offhand; with the Beginning Relic nothing is blocked.</li>
 * </ul>
 * Items found in a blocked slot are dropped (never deleted).
 */
public final class LockedSlots {
    private static final Set<Integer> D40 = toSet(DayRules.LOCKED_SLOTS_D40);
    private static final Set<Integer> D60_NO_RELIC;
    private static final Set<Integer> D60_END_RELIC;

    static {
        Set<Integer> all = new HashSet<>(D40);
        all.addAll(toSet(DayRules.EXTRA_LOCKED_SLOTS_D60));
        D60_NO_RELIC = Set.copyOf(all);
        Set<Integer> endRelic = new HashSet<>(toSet(DayRules.EXTRA_LOCKED_SLOTS_D60));
        endRelic.add(40);
        D60_END_RELIC = Set.copyOf(endRelic);
    }

    private LockedSlots() {
    }

    private static Set<Integer> toSet(int[] values) {
        Set<Integer> set = new HashSet<>();
        for (int v : values) {
            set.add(v);
        }
        return set;
    }

    public static boolean isBlocker(ItemStack stack) {
        return stack.is(Items.STRUCTURE_VOID);
    }

    public static void apply(ServerPlayer player, int day) {
        if (day < 40) {
            return;
        }
        Inventory inventory = player.getInventory();
        boolean endRelic = false;
        boolean beginningRelic = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.END_RELIC.get())) {
                endRelic = true;
            } else if (stack.is(ModItems.BEGINNING_RELIC.get())) {
                beginningRelic = true;
            }
        }
        Set<Integer> locked;
        String message;
        if (day >= 60) {
            if (beginningRelic) {
                locked = Set.of();
            } else if (endRelic) {
                locked = D60_END_RELIC;
            } else {
                locked = D60_NO_RELIC;
            }
            message = endRelic ? "§c¡Necesitas la Reliquia del Comienzo para usar este slot!" : "§c¡Necesitas la Reliquia del Fin para usar este slot!";
        } else {
            locked = endRelic ? Set.of() : D40;
            message = "§c¡Necesitas la Reliquia del Fin para usar este slot!";
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isBlocker(inventory.getItem(i)) && !locked.contains(i)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        for (int slot : locked) {
            ItemStack stack = inventory.getItem(slot);
            if (!isBlocker(stack)) {
                inventory.setItem(slot, new ItemStack(Items.STRUCTURE_VOID));
                if (!stack.isEmpty()) {
                    player.drop(stack, false);
                }
                player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.BOLD), true);
            }
        }
    }

    /** Removes every blocker (phase end / day rollback). */
    public static void clear(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isBlocker(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
