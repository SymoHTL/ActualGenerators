package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.generator.EnchantmentCombustorBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class EnchantmentCombustorMenu extends MachineMenu<EnchantmentCombustorBlockEntity> {
    /** How far through the current burn, in thousandths. */
    public static final int DATA_PROGRESS_PERMILLE = BASE_DATA_SIZE;
    /** Enchantment levels left on the loaded item. */
    public static final int DATA_LEVELS = BASE_DATA_SIZE + 1;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 2;

    public static final int INPUT_SLOT_X = 44;
    public static final int INPUT_SLOT_Y = 34;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 34;

    private static final int MACHINE_SLOTS = 2;

    /** Server side: reads live values straight off the machine. */
    public EnchantmentCombustorMenu(int containerId,
                                    Inventory playerInventory,
                                    EnchantmentCombustorBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public EnchantmentCombustorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private EnchantmentCombustorMenu(int containerId,
                                     Inventory playerInventory,
                                     EnchantmentCombustorBlockEntity machine,
                                     ContainerData data) {
        super(ModMenus.ENCHANTMENT_COMBUSTOR.get(), containerId, playerInventory, machine, data);
    }

    private static EnchantmentCombustorBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos)
                instanceof EnchantmentCombustorBlockEntity combustor) {
            return combustor;
        }
        throw new IllegalStateException("no enchantment combustor at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        addSlot(new SlotItemHandler(machine.inputHandler(), 0, INPUT_SLOT_X, INPUT_SLOT_Y));
        addSlot(new ResultSlot(machine.outputHandler(), 0, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
    }

    @Override
    protected int machineSlotCount() {
        return MACHINE_SLOTS;
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.ENCHANTMENT_COMBUSTOR.get();
    }

    @Override
    public boolean hasProgressArrow() {
        return true;
    }

    /** 0 to 1 through the current burn. */
    @Override
    public double progress() {
        return data.get(DATA_PROGRESS_PERMILLE) / (double) PERMILLE;
    }

    public int loadedLevels() {
        return data.get(DATA_LEVELS);
    }

    public int energyPerTick() {
        return energyRate();
    }

    /** Stripped gear may be taken out but never put back. */
    private static class ResultSlot extends SlotItemHandler {
        ResultSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<EnchantmentCombustorBlockEntity> {
        MachineData(EnchantmentCombustorBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_PROGRESS_PERMILLE -> permille(
                        machine.progress() / (double) machine.ticksForOperation(machine.baseTicksPerItem()));
                case DATA_LEVELS -> machine.loadedLevels();
                default -> 0;
            };
        }
    }
}
