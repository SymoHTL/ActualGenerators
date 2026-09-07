package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.generator.AnnihilationFurnaceBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The furnace's window: four input slots to the right of the arrow, the structure on the left,
 * and one row along the bottom that is the preview and its three sizes while the box is not
 * standing, and the efficiency — heat, load and the bar they make — while it is.
 */
public class AnnihilationFurnaceMenu extends MultiblockControllerMenu<AnnihilationFurnaceBlockEntity> {
    /** How far through the running operation, in thousandths. */
    public static final int DATA_PROGRESS_PERMILLE = MULTIBLOCK_DATA_SIZE;
    public static final int DATA_HEAT_PERMILLE = MULTIBLOCK_DATA_SIZE + 1;
    public static final int DATA_LOAD_PERMILLE = MULTIBLOCK_DATA_SIZE + 2;
    public static final int DATA_EFFICIENCY_PERMILLE = MULTIBLOCK_DATA_SIZE + 3;
    public static final int DATA_HELD_LO = MULTIBLOCK_DATA_SIZE + 4;
    public static final int DATA_HELD_HI = MULTIBLOCK_DATA_SIZE + 5;
    public static final int DATA_SIZE = MULTIBLOCK_DATA_SIZE + 6;

    public static final int SLOT_X = 98;
    public static final int SLOT_Y = 26;

    /**
     * The efficiency bar sits where the ramp bar sits on every other machine; the heat and load
     * lines stand in the multiblock row once the box does.
     */
    public static final MachineLayout.Box EFFICIENCY_BAR = MachineLayout.OVERCLOCK_FILL;

    /** Server side: reads live values straight off the machine. */
    public AnnihilationFurnaceMenu(int containerId, Inventory playerInventory, AnnihilationFurnaceBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public AnnihilationFurnaceMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
        withHatch(readHatch(playerInventory, extraData));
    }

    private AnnihilationFurnaceMenu(int containerId,
                                    Inventory playerInventory,
                                    AnnihilationFurnaceBlockEntity machine,
                                    ContainerData data) {
        super(ModMenus.ANNIHILATION_FURNACE.get(), containerId, playerInventory, machine, data);
    }

    private static AnnihilationFurnaceBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof AnnihilationFurnaceBlockEntity furnace) {
            return furnace;
        }
        throw new IllegalStateException("no annihilation furnace at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        for (int slot = 0; slot < AnnihilationFurnaceBlockEntity.INPUT_SLOTS; slot++) {
            addSlot(new SlotItemHandler(machine.inputHandler(), slot,
                    SLOT_X + (slot % 2) * 18, SLOT_Y + (slot / 2) * 18));
        }
    }

    @Override
    protected int machineSlotCount() {
        return AnnihilationFurnaceBlockEntity.INPUT_SLOTS;
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.ANNIHILATION_FURNACE.get();
    }

    @Override
    public boolean hasProgressArrow() {
        return true;
    }

    @Override
    public boolean hasRamp() {
        // Nothing to ramp: no upgrades, no tier, and the box does not warm up. The efficiency bar
        // stands where the ramp would.
        return false;
    }

    @Override
    public double progress() {
        return data.get(DATA_PROGRESS_PERMILLE) / (double) PERMILLE;
    }

    /** Items per operation, from the box. */
    public int batch() {
        return machine.maxBatch();
    }

    public int heatPermille() {
        return data.get(DATA_HEAT_PERMILLE);
    }

    public int loadPermille() {
        return data.get(DATA_LOAD_PERMILLE);
    }

    public int efficiencyPermille() {
        return data.get(DATA_EFFICIENCY_PERMILLE);
    }

    /** Items in the slots. Past the batch, every one costs efficiency. */
    public int held() {
        return wide(DATA_HELD_LO);
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<AnnihilationFurnaceBlockEntity> {
        MachineData(AnnihilationFurnaceBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            int preview = previewData(machine, index);
            if (preview >= 0) {
                return preview;
            }
            return switch (index) {
                case DATA_PROGRESS_PERMILLE -> permille(machine.progressFraction());
                case DATA_HEAT_PERMILLE -> machine.heatPermille();
                case DATA_LOAD_PERMILLE -> machine.loadPermille();
                case DATA_EFFICIENCY_PERMILLE -> machine.efficiencyPermille();
                case DATA_HELD_LO -> low(machine.held());
                case DATA_HELD_HI -> high(machine.held());
                default -> 0;
            };
        }
    }
}
