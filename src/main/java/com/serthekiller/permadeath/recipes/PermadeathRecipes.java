package com.serthekiller.permadeath.recipes;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The four "special" crafting recipes of the mod (stack-count based, so they cannot be JSON shaped recipes).
 * Matching is identical to Fabric; the extra stack consumption (Fabric ResultSlotMixin) is done by
 * {@link CraftingCost} on ItemCraftedEvent.
 */
public final class PermadeathRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, PermadeathMod.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HyperApple>> HYPER_APPLE =
            SERIALIZERS.register("hyper_apple", () -> new SimpleCraftingRecipeSerializer<>(HyperApple::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SuperApple>> SUPER_APPLE =
            SERIALIZERS.register("super_apple", () -> new SimpleCraftingRecipeSerializer<>(SuperApple::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BeginningRelic>> BEGINNING_RELIC =
            SERIALIZERS.register("beginning_relic", () -> new SimpleCraftingRecipeSerializer<>(BeginningRelic::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LifeOrb>> LIFE_ORB =
            SERIALIZERS.register("life_orb", () -> new SimpleCraftingRecipeSerializer<>(LifeOrb::new));

    private PermadeathRecipes() {
    }

    private static boolean full3x3(CraftingInput input) {
        return input.width() == 3 && input.height() == 3;
    }

    /** 8 gold blocks (8 each) around 1 golden apple (64 from D60). */
    public static final class HyperApple extends CustomRecipe {
        public HyperApple(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (!full3x3(input)) {
                return false;
            }
            int day = Permadeath.day();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = input.getItem(i);
                if (i == 4) {
                    if (!stack.is(Items.GOLDEN_APPLE) || stack.getCount() != (day >= 60 ? 64 : 1)) {
                        return false;
                    }
                } else if (!stack.is(Items.GOLD_BLOCK) || stack.getCount() != 8) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return new ItemStack(ModItems.HYPER_GOLDEN_APPLE_PLUS.get());
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width >= 3 && height >= 3;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return HYPER_APPLE.get();
        }
    }

    /** 8 gold ingots (8 each) around 1 golden apple. */
    public static final class SuperApple extends CustomRecipe {
        public SuperApple(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (!full3x3(input)) {
                return false;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack stack = input.getItem(i);
                if (i == 4) {
                    if (!stack.is(Items.GOLDEN_APPLE) || stack.getCount() != 1) {
                        return false;
                    }
                } else if (!stack.is(Items.GOLD_INGOT) || stack.getCount() != 8) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return new ItemStack(ModItems.SUPER_GOLDEN_APPLE_PLUS.get());
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width >= 3 && height >= 3;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SUPER_APPLE.get();
        }
    }

    /** End Relic in the centre, 32+ diamond blocks on the edges, shulker shells in the corners. */
    public static final class BeginningRelic extends CustomRecipe {
        public BeginningRelic(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.size() != 9) {
                return false;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack stack = input.getItem(i);
                if (i == 4) {
                    if (!stack.is(ModItems.END_RELIC.get())) {
                        return false;
                    }
                } else if (i == 0 || i == 2 || i == 6 || i == 8) {
                    if (!stack.is(Items.SHULKER_SHELL)) {
                        return false;
                    }
                } else if (!stack.is(Items.DIAMOND_BLOCK) || stack.getCount() < 32) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return new ItemStack(ModItems.BEGINNING_RELIC.get());
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width >= 3 && height >= 3;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return BEGINNING_RELIC.get();
        }
    }

    /** Heart of the Sea in the centre and 64 of eight different materials around it. */
    public static final class LifeOrb extends CustomRecipe {
        public LifeOrb(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.size() != 9) {
                return false;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack stack = input.getItem(i);
                if (i != 4 && stack.getCount() < 64) {
                    return false;
                }
                boolean ok = switch (i) {
                    case 0 -> stack.is(Items.DIAMOND);
                    case 1 -> stack.is(Items.GOLD_INGOT);
                    case 2 -> stack.is(Items.BONE_BLOCK);
                    case 3 -> stack.is(Items.BLAZE_ROD);
                    case 4 -> stack.is(Items.HEART_OF_THE_SEA);
                    case 5 -> stack.is(Items.END_STONE);
                    case 6 -> stack.is(Items.NETHER_BRICKS);
                    case 7 -> stack.is(Items.OBSIDIAN);
                    default -> stack.is(Items.LAPIS_BLOCK);
                };
                if (!ok) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return new ItemStack(ModItems.LIFE_ORB.get());
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width >= 3 && height >= 3;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return LIFE_ORB.get();
        }
    }
}
