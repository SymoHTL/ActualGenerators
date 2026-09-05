package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.registry.ModRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod has to be playable without the creative menu.
 *
 * <p>This is the guardrail for that: every item the mod registers must be obtainable — crafted, or
 * made by one of the mod's own machines. It fails the moment somebody adds a block and forgets its
 * recipe, which is exactly the sort of thing nobody notices until a player asks why the new machine
 * is not in JEI.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class CraftingTests {
    private static final String EMPTY = "empty";

    private CraftingTests() {
    }

    @GameTest(template = EMPTY)
    public static void everythingTheModAddsCanBeMade(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();

        for (var holder : ModItems.ITEMS.getEntries()) {
            Item item = holder.get();
            if (!isObtainable(helper, item)) {
                missing.add(holder.getId().toString());
            }
        }

        helper.assertTrue(missing.isEmpty(), "no recipe makes " + missing);
        helper.succeed();
    }

    /**
     * Crafting or crushing. A dust has no crafting recipe by design — crushing an ore is where it
     * comes from — so a machine of the mod's own counts as a way to get something.
     */
    private static boolean isObtainable(GameTestHelper helper, Item item) {
        for (RecipeHolder<?> recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            RecipeType<?> type = recipe.value().getType();
            if ((type == RecipeType.CRAFTING || type == ModRecipes.CRUSHING.get())
                    && recipe.value().getResultItem(helper.getLevel().registryAccess()).is(item)) {
                return true;
            }
        }
        return false;
    }
}
