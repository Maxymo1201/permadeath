package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.core.rules.TotemRules;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Totems of undying (mandatory table, {@link TotemRules}):
 * <pre>
 * D0-29   0% failure, 1 totem      D30-39  1%, 1 totem      D40-49  3%, 2 totems
 * D50-59  5%, 2 totems             D60+    7%, 3 totems
 * </pre>
 * Implemented on {@link LivingUseTotemEvent} (fired by vanilla for each hand holding a totem, before it is
 * consumed), so vanilla keeps the statistics, the advancement, the effects and the animation. The Fabric mod
 * re-implemented the lethal damage computation inside ALLOW_DAMAGE and failed 1% of the totems from D0.
 *
 * <ul>
 *     <li>Not enough totems (in hands + inventory) → every totem is consumed and the player dies.</li>
 *     <li>Survivor medal in the activating hand → no failure roll, the extra totems are still required.</li>
 *     <li>Failure → the required totems are consumed and the player dies.</li>
 * </ul>
 * Messages are the PermaDeathCore ones (TotemFail.* in its config.yml).
 */
public final class TotemSystem {
    public static final String MEDAL_KEY = "PermadeathSurvivorMedal";
    public static final String MEDAL_GIVEN_TAG = "permadeath_survivor_medal_given";
    public static final int MEDAL_UNLOCK_DAY = 55;

    private record Decision(long tick, boolean allowed) {
    }

    private static final Map<UUID, Decision> DECISIONS = new HashMap<>();

    private TotemSystem() {
    }

    public static void reset() {
        DECISIONS.clear();
    }

    public static void onUseTotem(LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Permadeath.isRunning()) {
            return;
        }
        int day = Permadeath.day();
        if (day < TotemRules.SYSTEM_START_DAY) {
            return; // plain vanilla before D30
        }
        long tick = player.serverLevel().getGameTime();
        Decision previous = DECISIONS.get(player.getUUID());
        if (previous != null && previous.tick() == tick) {
            if (!previous.allowed()) {
                event.setCanceled(true);
            }
            return;
        }
        boolean allowed = decide(player, event.getTotem(), event.getHandHolding(), day);
        DECISIONS.put(player.getUUID(), new Decision(tick, allowed));
        if (!allowed) {
            event.setCanceled(true);
        }
    }

    private static boolean decide(ServerPlayer player, ItemStack activating, InteractionHand hand, int day) {
        TotemRules.TotemRule rule = TotemRules.forDay(day);
        String name = player.getName().getString();
        int required = rule.requiredTotems();
        int available = countTotems(player);
        boolean medal = isSurvivorMedal(activating);

        if (available < required) {
            consumeExtras(player, hand, available);
            activating.shrink(activating.getCount());
            Texts.broadcast(player.server, "§7¡" + name + " no tenía suficientes tótems en el inventario!");
            return false;
        }
        if (medal) {
            consumeExtras(player, hand, required - 1);
            Texts.broadcast(player.server, "§7El jugador " + name + " ha usado su medalla de superviviente.");
            return true;
        }
        int roll = player.getRandom().nextInt(100);
        boolean fails = rule.fails(roll);
        // PermaDeathCore display: random 1..100, failure when random > 100 - failure%.
        int random = 100 - roll;
        int threshold = 100 - rule.failurePercent();
        String amount = required == 1 ? "un tótem" : TotemRules.amountWord(required) + " tótems";
        String shown;
        if (fails) {
            int toShow = threshold == random ? threshold - 1 : threshold;
            shown = toShow + " = " + threshold;
        } else {
            int raShow = random == threshold ? random - 1 : random;
            shown = raShow + " != " + threshold;
        }
        Texts.broadcast(player.server, "§7El jugador " + name + " ha consumido " + amount + " (Probabilidad: " + shown + ")");
        consumeExtras(player, hand, required - 1);
        if (fails) {
            activating.shrink(activating.getCount());
            Texts.broadcast(player.server, required == 1
                    ? "§7¡El tótem de §c" + name + " §7ha fallado!"
                    : "§7¡Los tótems de §c" + name + " §7han fallado!");
            return false;
        }
        return true;
    }

    /** Number of totems in the whole inventory (hands included); survivor medals count as totems. */
    public static int countTotems(ServerPlayer player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Removes {@code amount} totems other than the activating one: first the other hand (Fabric/plugin
     * order), then the inventory, never consuming a survivor medal as an extra while normal totems remain.
     */
    private static void consumeExtras(ServerPlayer player, InteractionHand activatingHand, int amount) {
        if (amount <= 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        int activatingSlot = activatingHand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
        int left = amount;
        InteractionHand other = activatingHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(other);
        if (otherStack.is(Items.TOTEM_OF_UNDYING) && !isSurvivorMedal(otherStack)) {
            int take = Math.min(left, otherStack.getCount());
            otherStack.shrink(take);
            left -= take;
        }
        for (boolean allowMedals : new boolean[]{false, true}) {
            for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
                if (i == activatingSlot) {
                    continue;
                }
                ItemStack stack = inventory.getItem(i);
                if (stack.is(Items.TOTEM_OF_UNDYING) && (allowMedals || !isSurvivorMedal(stack))) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ medal

    public static boolean isSurvivorMedal(ItemStack stack) {
        if (!stack.is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(MEDAL_KEY);
    }

    public static ItemStack createSurvivorMedal() {
        ItemStack medal = new ItemStack(Items.TOTEM_OF_UNDYING);
        medal.set(DataComponents.CUSTOM_NAME, medalName());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MEDAL_KEY, true);
        medal.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return medal;
    }

    private static Component medalName() {
        Style bracket = Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true).withItalic(false);
        Style skull = Style.EMPTY.withColor(ChatFormatting.RED).withBold(false).withItalic(false);
        Style title = Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false);
        Style obfuscated = Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(false).withItalic(false).withObfuscated(true);
        Style space = Style.EMPTY.withBold(false).withItalic(false);
        MutableComponent name = Component.literal("[").withStyle(bracket);
        name.append(Component.literal("☠").withStyle(skull))
                .append(Component.literal("]").withStyle(bracket))
                .append(Component.literal(" ").withStyle(space))
                .append(Component.literal("0").withStyle(obfuscated))
                .append(Component.literal(" Medalla de Superviviente ").withStyle(title))
                .append(Component.literal("0").withStyle(obfuscated))
                .append(Component.literal(" ").withStyle(space))
                .append(Component.literal("[").withStyle(bracket))
                .append(Component.literal("☠").withStyle(skull))
                .append(Component.literal("]").withStyle(bracket));
        return name;
    }

    /** From D55 every player receives one survivor medal (once per player, persistent tag). */
    public static void giveSurvivorMedalIfEligible(ServerPlayer player, int day) {
        if (day < MEDAL_UNLOCK_DAY || player.getTags().contains(MEDAL_GIVEN_TAG) || player.isSpectator()) {
            return;
        }
        ItemStack medal = createSurvivorMedal();
        if (!player.getInventory().add(medal)) {
            player.drop(medal, false);
        }
        player.addTag(MEDAL_GIVEN_TAG);
    }
}
