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
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * D60: no loot in the vanilla structure chests (Fabric Day60to69Handler LootTableEvents.REPLACE of the built-in
 * {@code minecraft:chests/*} tables).
 *
 * <p>The chests of The Beginning keep their loot on D60. Fabric (LootTableMixin) and the plugin also emptied them,
 * which left every Ytic city and island chest empty on the final day while only the pre-filled containers of the
 * templates kept items; the user asked for those chests to always have their loot.</p>
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
        if (BeginningDimension.is(context.getLevel())) {
            return generatedLoot;
        }
        if (isVanillaChestTable(context.getQueriedLootTableId())) {
            generatedLoot.clear();
        }
        return generatedLoot;
    }

    static boolean isVanillaChestTable(ResourceLocation id) {
        return id != null && "minecraft".equals(id.getNamespace()) && id.getPath().startsWith("chests/");
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
