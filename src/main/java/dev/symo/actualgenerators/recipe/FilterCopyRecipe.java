package dev.symo.actualgenerators.recipe;

import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Filters in a crafting grid: one set filter on its own comes out blank, and a set filter with a
 * blank one comes out as two copies of the set one.
 */
public class FilterCopyRecipe extends CustomRecipe {

    public FilterCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return result(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = result(input);
        return result == null ? ItemStack.EMPTY : result;
    }

    /** What the grid makes, or null when it is not a filter recipe at all. */
    public static @Nullable ItemStack result(CraftingInput input) {
        ItemStack configured = ItemStack.EMPTY;
        int filters = 0;
        int blanks = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof FilterItem)) {
                return null;
            }
            filters++;
            if (FilterItem.isConfigured(stack)) {
                if (!configured.isEmpty()) {
                    return null;
                }
                configured = stack;
            } else {
                blanks++;
            }
        }
        if (configured.isEmpty()) {
            return null;
        }
        if (filters == 1) {
            return new ItemStack(ModItems.FILTER.get());
        }
        return filters == 2 && blanks == 1 ? configured.copyWithCount(2) : null;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FILTER_COPY.get();
    }
}
