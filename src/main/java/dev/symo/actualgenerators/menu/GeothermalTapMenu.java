package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.registry.ModFluids;
import dev.symo.actualgenerators.generator.GeothermalTapBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * A generator with nothing to put in it: the pocket in a gauge, the corium in a tank beside it,
 * and the depth in a line. Before the box stands, the preview row along the bottom; once it
 * does, the warm-up ramp in the same place.
 */
public class GeothermalTapMenu extends MultiblockControllerMenu<GeothermalTapBlockEntity> {
    /** How much of the chunk's pocket is left, in thousandths. */
    public static final int DATA_POCKET_PERMILLE = MULTIBLOCK_DATA_SIZE;
    /** How much of the rating this depth is worth, in thousandths. */
    public static final int DATA_DEPTH_PERMILLE = MULTIBLOCK_DATA_SIZE + 1;
    public static final int DATA_CORIUM_LO = MULTIBLOCK_DATA_SIZE + 2;
    public static final int DATA_CORIUM_HI = MULTIBLOCK_DATA_SIZE + 3;
    public static final int DATA_TANK_LO = MULTIBLOCK_DATA_SIZE + 4;
    public static final int DATA_TANK_HI = MULTIBLOCK_DATA_SIZE + 5;
    public static final int DATA_SIZE = MULTIBLOCK_DATA_SIZE + 6;

    /** Server side: reads live values straight off the machine. */
    public GeothermalTapMenu(int containerId, Inventory playerInventory, GeothermalTapBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public GeothermalTapMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
        withHatch(readHatch(playerInventory, extraData));
    }

    private GeothermalTapMenu(int containerId,
                              Inventory playerInventory,
                              GeothermalTapBlockEntity machine,
                              ContainerData data) {
        super(ModMenus.GEOTHERMAL_TAP.get(), containerId, playerInventory, machine, data);
    }

    private static GeothermalTapBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof GeothermalTapBlockEntity tap) {
            return tap;
        }
        throw new IllegalStateException("no geothermal tap at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        // Nothing goes in; the heat is under the box.
    }

    @Override
    protected int machineSlotCount() {
        return 0;
    }

    @Override
    public boolean hasGauge() {
        return true;
    }

    @Override
    public boolean hasTank() {
        return true;
    }

    /** Nothing warms up before there is a bore; until then the preview row stands where the ramp goes. */
    @Override
    public boolean hasRamp() {
        return machine.isFormed();
    }

    @Override
    public List<MachineLayout.Box> extraChrome() {
        return machine.isFormed() ? List.of() : super.extraChrome();
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.GEOTHERMAL_TAP.get();
    }

    public int pocketPermille() {
        return data.get(DATA_POCKET_PERMILLE);
    }

    public int depthPermille() {
        return data.get(DATA_DEPTH_PERMILLE);
    }

    /** Millibuckets of corium in the tank. */
    public int corium() {
        return wide(DATA_CORIUM_LO);
    }

    @Override
    public int tankCapacity() {
        return wide(DATA_TANK_LO);
    }

    /**
     * The corium as a stack, for drawing, from the synced amount: the client's copy of the block
     * entity is refreshed on a chunk load and hardly ever after, so its tank read whatever it
     * held then.
     */
    @Override
    public FluidStack tankFluid() {
        int amount = corium();
        return amount <= 0 ? FluidStack.EMPTY : new FluidStack(ModFluids.CORIUM.get(), amount);
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<GeothermalTapBlockEntity> {
        MachineData(GeothermalTapBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            int preview = previewData(machine, index);
            if (preview >= 0) {
                return preview;
            }
            return switch (index) {
                case DATA_POCKET_PERMILLE -> machine.pocketCapacity() <= 0
                        ? 0
                        : (int) (machine.pocketRemaining() * PERMILLE / machine.pocketCapacity());
                case DATA_DEPTH_PERMILLE -> permille(machine.depthFactor());
                case DATA_CORIUM_LO -> low(machine.corium().getAmount());
                case DATA_CORIUM_HI -> high(machine.corium().getAmount());
                case DATA_TANK_LO -> low(machine.tankCapacity());
                case DATA_TANK_HI -> high(machine.tankCapacity());
                default -> 0;
            };
        }
    }
}
