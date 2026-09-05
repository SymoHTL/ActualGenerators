package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.processing.ResonanceCrusherBlockEntity;
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

public class ResonanceCrusherMenu extends MachineMenu<ResonanceCrusherBlockEntity> {
    /** How far through the current operation, in thousandths. */
    public static final int DATA_PROGRESS_PERMILLE = BASE_DATA_SIZE;
    /** The frequency of whatever is loaded, or zero when it is nothing the crusher can crush. */
    public static final int DATA_FREQUENCY = BASE_DATA_SIZE + 1;
    /** 1 once the crusher knows that frequency, 0 while it is still working it out. */
    public static final int DATA_TUNED = BASE_DATA_SIZE + 2;
    /** How many items the current operation is taking on. */
    public static final int DATA_BATCH = BASE_DATA_SIZE + 3;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 4;

    public static final int INPUT_SLOT_X = 44;
    public static final int INPUT_SLOT_Y = 34;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 34;

    private static final int MACHINE_SLOTS = 2;

    /** Server side: reads live values straight off the machine. */
    public ResonanceCrusherMenu(int containerId,
                                Inventory playerInventory,
                                ResonanceCrusherBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public ResonanceCrusherMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private ResonanceCrusherMenu(int containerId,
                                 Inventory playerInventory,
                                 ResonanceCrusherBlockEntity machine,
                                 ContainerData data) {
        super(ModMenus.RESONANCE_CRUSHER.get(), containerId, playerInventory, machine, data);
    }

    private static ResonanceCrusherBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof ResonanceCrusherBlockEntity crusher) {
            return crusher;
        }
        throw new IllegalStateException("no resonance crusher at " + pos);
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
        return ModBlocks.RESONANCE_CRUSHER.get();
    }

    @Override
    public boolean hasProgressArrow() {
        return true;
    }

    @Override
    public double progress() {
        return data.get(DATA_PROGRESS_PERMILLE) / (double) PERMILLE;
    }

    public int frequency() {
        return data.get(DATA_FREQUENCY);
    }

    public boolean isTuned() {
        return data.get(DATA_TUNED) != 0;
    }

    public int batchSize() {
        return data.get(DATA_BATCH);
    }

    /** FE/t the crusher is drawing, as a positive number — the readout says what it costs. */
    public int energyPerTick() {
        return Math.abs(energyRate());
    }

    /** Results may be taken out but never put back. */
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
    private static class MachineData extends MachineMenu.MachineData<ResonanceCrusherBlockEntity> {
        MachineData(ResonanceCrusherBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_PROGRESS_PERMILLE -> permille(machine.progressFraction());
                case DATA_FREQUENCY -> machine.frequency();
                case DATA_TUNED -> machine.isTuned() ? 1 : 0;
                case DATA_BATCH -> machine.currentBatch();
                default -> 0;
            };
        }
    }
}
