package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.generator.CorrosionCellBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CorrosionCellMenu extends MachineMenu<CorrosionCellBlockEntity> {
    /** How far through the current oxidation step, in thousandths. */
    public static final int DATA_PROGRESS_PERMILLE = BASE_DATA_SIZE;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 1;

    public static final int INPUT_SLOT_X = 44;
    public static final int INPUT_SLOT_Y = 34;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 34;

    private static final int MACHINE_SLOTS = 2;

    /** Server side: reads live values straight off the machine. */
    public CorrosionCellMenu(int containerId, Inventory playerInventory, CorrosionCellBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public CorrosionCellMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private CorrosionCellMenu(int containerId,
                              Inventory playerInventory,
                              CorrosionCellBlockEntity machine,
                              ContainerData data) {
        super(ModMenus.CORROSION_CELL.get(), containerId, playerInventory, machine, data);
    }

    private static CorrosionCellBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof CorrosionCellBlockEntity cell) {
            return cell;
        }
        throw new IllegalStateException("no corrosion cell at " + pos);
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
        return ModBlocks.CORROSION_CELL.get();
    }

    @Override
    public boolean hasProgressArrow() {
        return true;
    }

    /** 0 to 1 through the current oxidation step. */
    @Override
    public double progress() {
        return data.get(DATA_PROGRESS_PERMILLE) / (double) PERMILLE;
    }

    /** Results may be taken out but never put back. */
    private static class ResultSlot extends SlotItemHandler {
        ResultSlot(net.neoforged.neoforge.items.IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
            return false;
        }
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<CorrosionCellBlockEntity> {
        MachineData(CorrosionCellBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return index == DATA_PROGRESS_PERMILLE
                    ? permille(machine.progress() / (double) machine.ticksForOperation(machine.baseTicksPerStage()))
                    : 0;
        }
    }
}
