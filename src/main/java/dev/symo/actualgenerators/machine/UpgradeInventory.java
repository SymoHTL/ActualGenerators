package dev.symo.actualgenerators.machine;

import dev.symo.actualgenerators.item.UpgradeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The upgrade slots of a machine: one slot per {@link UpgradeType}, indexed by its ordinal.
 *
 * <p>Binding a slot to a type is what lets a machine show only the upgrades it can actually use,
 * and lets each slot cap itself at the number the tuning will count. Dropping a stack of 64 speed
 * upgrades into a machine that counts four leaves sixty doing nothing, so the slot takes four and
 * hands the rest back.
 *
 * <p>Slots exist for every type even on machines that refuse most of them. Keeping the layout
 * fixed means a saved machine reads back the same way after its accepted types change, and the
 * refused slots simply never appear in the menu.
 */
public class UpgradeInventory extends ItemStackHandler {
    public static final int SLOTS = UpgradeType.all().length;

    private final Predicate<UpgradeType> accepts;
    private final ToIntFunction<UpgradeType> maxInstalled;
    private final Runnable onChanged;

    public UpgradeInventory(Predicate<UpgradeType> accepts,
                            ToIntFunction<UpgradeType> maxInstalled,
                            Runnable onChanged) {
        super(SLOTS);
        this.accepts = accepts;
        this.maxInstalled = maxInstalled;
        this.onChanged = onChanged;
    }

    /** The single upgrade type a slot holds. */
    public static UpgradeType typeOf(int slot) {
        return UpgradeType.byOrdinal(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        return stack.getItem() instanceof UpgradeItem upgrade
                && upgrade.type() == typeOf(slot)
                && accepts.test(upgrade.type());
    }

    /**
     * Caps the slot at the number of upgrades the tuning will actually count, so the surplus is
     * refused rather than sitting there doing nothing.
     *
     * <p>This has to be {@code getSlotLimit} rather than {@code getStackLimit}: a player clicking
     * a stack into the slot goes through {@link net.neoforged.neoforge.items.SlotItemHandler},
     * which sizes the click from {@code getSlotLimit} and then writes the stack in directly —
     * {@code insertItem}, and therefore {@code getStackLimit}, is never consulted on that path.
     */
    @Override
    public int getSlotLimit(int slot) {
        return Math.max(0, maxInstalled.applyAsInt(typeOf(slot)));
    }

    /** Last line of defence: the raw setter is public and bypasses every other limit. */
    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        int ceiling = getSlotLimit(slot);
        if (stack.getCount() > ceiling) {
            stack = stack.copyWithCount(ceiling);
        }
        super.setStackInSlot(slot, stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    /** How many upgrades of this type are installed. */
    public int count(UpgradeType type) {
        ItemStack stack = getStackInSlot(type.ordinal());
        return stack.getItem() instanceof UpgradeItem upgrade && upgrade.type() == type ? stack.getCount() : 0;
    }

    public boolean isEmpty() {
        for (int slot = 0; slot < getSlots(); slot++) {
            if (!getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
