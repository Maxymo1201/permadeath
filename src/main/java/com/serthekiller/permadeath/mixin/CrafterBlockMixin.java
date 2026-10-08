package com.serthekiller.permadeath.mixin;

import com.serthekiller.permadeath.recipes.CraftingCost;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * The vanilla Crafter never fires ItemCraftedEvent, so it skipped the extra cost of the special recipes and took one
 * item per slot; its recipe cache ignores stack counts, so it then kept crafting with what was left (a Life Orb or a
 * Hyper Golden Apple+ for one item of each material). Those recipes can only be crafted by hand, as in Fabric and
 * the plugin, where the extra cost is applied.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
    private static void permadeath$noSpecialRecipes(Level level, CraftingInput input,
            CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        Optional<RecipeHolder<CraftingRecipe>> result = cir.getReturnValue();
        if (result.isPresent() && CraftingCost.hasExtraCost(result.get().value())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
