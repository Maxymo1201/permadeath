package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * D40+: inventory slots blocked with structure voids (Fabric handleInventoryRestriction).
 * <ul>
 *     <li>D40-59: slots 4, 13, 22, 31 (middle column) and 40 (offhand) unless the player carries the End Relic.</li>
 *     <li>D60: without relics the D40 slots plus 25 more; with the End Relic the 25 extra slots plus the
 *     offhand; with the Beginning Relic nothing is blocked.</li>
 * </ul>
 * Items found in a blocked slot are dropped (never deleted). As in the plugin (SlotBlockListener) the blockers
 * cannot leave the inventory: the hand swap (F) with a blocker is cancelled, a blocker taken with the cursor is
 * put back (the item that replaced it returns to the cursor) and blockers moved into a container are removed
 * when the container is closed.
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
        // A relic being moved (on the cursor or in the 2x2 grid) still counts: otherwise every slot it unlocks was
        // blocked for a tick and its items were pushed out of a full inventory onto the ground.
        List<ItemStack> held = new ArrayList<>(inventory.getContainerSize() + 5);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            held.add(inventory.getItem(i));
        }
        held.add(player.containerMenu.getCarried());
        held.addAll(player.inventoryMenu.getCraftSlots().getItems());
        for (ItemStack stack : held) {
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
        boolean blockerOnCursor = isBlocker(player.containerMenu.getCarried());
        if (blockerOnCursor) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        for (int slot : locked) {
            ItemStack stack = inventory.getItem(slot);
            if (!isBlocker(stack)) {
                inventory.setItem(slot, new ItemStack(Items.STRUCTURE_VOID));
                if (!stack.isEmpty()) {
                    if (blockerOnCursor && player.containerMenu.getCarried().isEmpty()) {
                        // The player clicked the blocker with an item: undo the swap.
                        player.containerMenu.setCarried(stack);
                    } else if (!undoContainerSwap(player, stack) && !inventory.add(stack)) {
                        player.drop(stack, false);
                    }
                }
                player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.BOLD), true);
            }
        }
    }

    /**
     * A number-key swap from an open container (or from the 2x2 crafting grid) put its item in the locked slot and
     * the blocker in its place: give the item back that slot instead of throwing it on the ground (lost over the void
     * in The Beginning).
     */
    private static boolean undoContainerSwap(ServerPlayer player, ItemStack stack) {
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != player.getInventory() && isBlocker(slot.getItem())) {
                slot.set(stack);
                return true;
            }
        }
        return false;
    }

    /** D40+: the off-hand swap (F) never moves a blocker. */
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (Permadeath.day() >= 40 && (isBlocker(event.getItemSwappedToMainHand()) || isBlocker(event.getItemSwappedToOffHand()))) {
            event.setCanceled(true);
        }
    }

    /** Blockers shift-clicked or number-key-swapped into a container are removed when it is closed. */
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (Slot slot : event.getContainer().slots) {
            if (slot.container != player.getInventory() && isBlocker(slot.getItem())) {
                slot.set(ItemStack.EMPTY);
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
