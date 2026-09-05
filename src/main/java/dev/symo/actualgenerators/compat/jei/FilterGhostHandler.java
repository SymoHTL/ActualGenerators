package dev.symo.actualgenerators.compat.jei;

import dev.symo.actualgenerators.client.FilterScreen;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.menu.FilterMenu;
import dev.symo.actualgenerators.menu.MachineLayout;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dragging an ingredient out of JEI straight into a filter.
 *
 * <p>Typing a name into JEI and dragging it over is how everyone who plays with JEI expects to
 * fill a filter. On the list page, dropped on Add it becomes a new entry and dropped on a row's
 * picture it replaces that entry's picture and keeps its switches; on an entry page, the picture
 * slot takes it. An item lands as an item entry, a fluid as a fluid entry.
 */
public class FilterGhostHandler implements IGhostIngredientHandler<FilterScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(FilterScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        FilterContents.Entry entry = asEntry(ingredient.getIngredient());
        List<Target<I>> targets = new ArrayList<>();
        if (entry.isEmpty()) {
            return targets;
        }
        FilterMenu menu = screen.getMenu();
        if (screen.page() != FilterScreen.Page.LIST) {
            targets.add(new FilterTarget<>(screen, FilterMenu.ENTRY_SLOT, screen.editingIndex(), entry));
            return targets;
        }
        targets.add(new FilterTarget<>(screen, FilterMenu.ADD_BUTTON, -1, entry));
        for (int row = 0; row < FilterMenu.ROWS; row++) {
            int index = screen.firstRow() + row;
            if (menu.entry(index).isEmpty()) {
                break;
            }
            targets.add(new FilterTarget<>(screen, FilterMenu.rowIcon(row), index, entry));
        }
        return targets;
    }

    private static FilterContents.Entry asEntry(Object value) {
        if (value instanceof ItemStack stack) {
            return FilterContents.Entry.ofItem(stack);
        }
        if (value instanceof FluidStack fluid) {
            return FilterContents.Entry.ofFluid(fluid);
        }
        return FilterContents.Entry.EMPTY;
    }

    @Override
    public void onComplete() {
    }

    /** One place in the window waiting to be dropped on: Add, a row, or the page's slot. */
    private record FilterTarget<I>(FilterScreen screen, MachineLayout.Box box, int index,
                                   FilterContents.Entry entry) implements Target<I> {
        @Override
        public Rect2i getArea() {
            return new Rect2i(screen.getGuiLeft() + box.x(), screen.getGuiTop() + box.y(), box.width(), box.height());
        }

        @Override
        public void accept(I ingredient) {
            // Nothing is on the cursor for a drag out of JEI, so the screen sends it over itself,
            // keeping the switches of the entry it lands on.
            screen.acceptPicture(index, entry);
        }
    }
}
