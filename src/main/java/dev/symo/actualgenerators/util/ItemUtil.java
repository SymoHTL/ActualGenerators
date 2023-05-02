package dev.symo.actualgenerators.util;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class ItemUtil {

    public static void moveFirstItemStack(IItemHandler source, IItemHandler destination) {
        for (int i = 0; i < source.getSlots(); i++) {
            ItemStack stack = source.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            for (int j = 0; j < destination.getSlots(); j++) {
                ItemStack extractedStack = source.extractItem(i, stack.getCount(), true); // Extract with simulate flag set to true
                ItemStack remaining = destination.insertItem(j, extractedStack, true); // Insert with simulate flag set to true

                int successfullyInserted = extractedStack.getCount() - remaining.getCount();
                if (successfullyInserted > 0) {
                    ItemStack actuallyExtractedStack = source.extractItem(i, successfullyInserted, false); // Actually extract the items
                    destination.insertItem(j, actuallyExtractedStack, false); // Actually insert the items
                }

                if (stack.isEmpty()) {
                    break;
                }
            }
        }
    }

    public static void moveItemStack(ItemStack stack, IItemHandler source, IItemHandler destination) {
        ItemStack stackToInsert = stack.copy();

        for (int i = 0; i < source.getSlots(); i++) {
            ItemStack sourceStack = source.getStackInSlot(i);
            if (!ItemStack.isSame(stack, sourceStack) || !ItemStack.tagMatches(stack, sourceStack)) {
                continue;
            }

            for (int j = 0; j < destination.getSlots(); j++) {
                ItemStack extractedStack = source.extractItem(i, stackToInsert.getCount(), true); // Extract with simulate flag set to true
                ItemStack remaining = destination.insertItem(j, extractedStack, true); // Insert with simulate flag set to true

                int successfullyInserted = extractedStack.getCount() - remaining.getCount();
                if (successfullyInserted > 0) {
                    ItemStack actuallyExtractedStack = source.extractItem(i, successfullyInserted, false); // Actually extract the items
                    destination.insertItem(j, actuallyExtractedStack, false); // Actually insert the items
                    stackToInsert.shrink(successfullyInserted);
                }

                if (stackToInsert.isEmpty()) {
                    break;
                }
            }

            if (stackToInsert.isEmpty()) {
                break;
            }
        }
    }


    public static int moveItemsAmount(IItemHandler source, IItemHandler destination, int amount) {
        int remainingAmount = amount;

        for (int i = 0; i < source.getSlots() && remainingAmount > 0; i++) {
            ItemStack stack = source.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack stackToInsert = stack.copy();
            stackToInsert.setCount(Math.min(stackToInsert.getCount(), remainingAmount));
            int initialStackToInsertCount = stackToInsert.getCount();

            for (int j = 0; j < destination.getSlots() && !stackToInsert.isEmpty(); j++) {
                ItemStack extractedStack = source.extractItem(i, stackToInsert.getCount(), true); // Extract with simulate flag set to true
                ItemStack remaining = destination.insertItem(j, extractedStack, true); // Insert with simulate flag set to true

                int successfullyInserted = extractedStack.getCount() - remaining.getCount();
                if (successfullyInserted > 0) {
                    ItemStack actuallyExtractedStack = source.extractItem(i, successfullyInserted, false); // Actually extract the items
                    destination.insertItem(j, actuallyExtractedStack, false); // Actually insert the items
                    remainingAmount -= successfullyInserted;
                    stackToInsert.shrink(successfullyInserted);
                }
            }

            if (stackToInsert.getCount() != initialStackToInsertCount) {
                break;
            }
        }

        return remainingAmount;
    }

}
