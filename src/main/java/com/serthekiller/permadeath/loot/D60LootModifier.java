package com.serthekiller.permadeath.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.progression.Permadeath;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * D60: no loot in chests (Fabric Day60to69Handler LootTableEvents.REPLACE of the built-in {@code chests/*}
 * tables, and LootTableMixin cancelling every container fill inside The Beginning).
 * <ul>
 *     <li>Built-in chest tables ({@code minecraft:} and {@code permadeath:} namespaces, path {@code chests/})
 *     generate nothing from D60.</li>
 *     <li>In The Beginning from D60 every container fill (loot without block, tool or damage context) is empty.</li>
 * </ul>
 * Registered in {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}.
 */
public final class D60LootModifier extends LootModifier {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, PermadeathMod.MOD_ID);

    public static final MapCodec<D60LootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, D60LootModifier::new));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<D60LootModifier>> D60_EMPTY_LOOT =
            SERIALIZERS.register("d60_empty_loot", () -> CODEC);

    public static final int FROM_DAY = 60;

    public D60LootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!Permadeath.isRunning() || Permadeath.day() < FROM_DAY) {
            return generatedLoot;
        }
        ResourceLocation id = context.getQueriedLootTableId();
        if (isBuiltInChestTable(id) || (BeginningDimension.is(context.getLevel()) && isContainerFill(context))) {
            generatedLoot.clear();
        }
        return generatedLoot;
    }

    static boolean isBuiltInChestTable(ResourceLocation id) {
        return id != null && id.getPath().startsWith("chests/")
                && ("minecraft".equals(id.getNamespace()) || PermadeathMod.MOD_ID.equals(id.getNamespace()));
    }

    private static boolean isContainerFill(LootContext context) {
        return !context.hasParam(LootContextParams.BLOCK_STATE)
                && !context.hasParam(LootContextParams.TOOL)
                && !context.hasParam(LootContextParams.DAMAGE_SOURCE);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
