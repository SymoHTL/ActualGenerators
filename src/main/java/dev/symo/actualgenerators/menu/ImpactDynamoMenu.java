package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.generator.ImpactDynamoBlockEntity;
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

/** Salvage only: what comes out is what fell in, so there is nothing to put back. */
public class ImpactDynamoMenu extends MachineMenu<ImpactDynamoBlockEntity> {
    /** FE the last catch was worth, low half. */
    public static final int DATA_LAST_ENERGY_LO = BASE_DATA_SIZE;
    public static final int DATA_LAST_ENERGY_HI = BASE_DATA_SIZE + 1;
    /** How far that catch had fallen, after the cap. */
    public static final int DATA_LAST_DISTANCE = BASE_DATA_SIZE + 2;
    /** The furthest drop that still pays, so the screen can scale its gauge. */
    public static final int DATA_MAX_DISTANCE = BASE_DATA_SIZE + 3;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 4;

    // A row under the readout rather than a column beside it: the fall gauge owns the right of
    // the window, and a column there overlapped it.
    public static final int OUTPUT_SLOT_X = 12;
    public static final int OUTPUT_SLOT_Y = 54;
    public static final int OUTPUT_SLOT_SPACING = 18;

    /** Server side: reads live values straight off the machine. */
    public ImpactDynamoMenu(int containerId, Inventory playerInventory, ImpactDynamoBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public ImpactDynamoMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private ImpactDynamoMenu(int containerId,
                             Inventory playerInventory,
                             ImpactDynamoBlockEntity machine,
                             ContainerData data) {
        super(ModMenus.IMPACT_DYNAMO.get(), containerId, playerInventory, machine, data);
    }

    private static ImpactDynamoBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof ImpactDynamoBlockEntity dynamo) {
            return dynamo;
        }
        throw new IllegalStateException("no impact dynamo at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        for (int slot = 0; slot < ImpactDynamoBlockEntity.OUTPUT_SLOTS; slot++) {
            addSlot(new SalvageSlot(machine.outputHandler(), slot,
                    OUTPUT_SLOT_X + slot * OUTPUT_SLOT_SPACING, OUTPUT_SLOT_Y));
        }
    }

    @Override
    protected int machineSlotCount() {
        return ImpactDynamoBlockEntity.OUTPUT_SLOTS;
    }

    @Override
    public boolean hasGauge() {
        // the last fall is the level it fills.
        return true;
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.IMPACT_DYNAMO.get();
    }

    public int lastImpactEnergy() {
        return wide(DATA_LAST_ENERGY_LO);
    }

    public int lastFallDistance() {
        return data.get(DATA_LAST_DISTANCE);
    }

    public int maxFallDistance() {
        return data.get(DATA_MAX_DISTANCE);
    }

    /** Salvage may be taken out but never put back. */
    private static class SalvageSlot extends SlotItemHandler {
        SalvageSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<ImpactDynamoBlockEntity> {
        MachineData(ImpactDynamoBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_LAST_ENERGY_LO -> low(machine.lastImpactEnergy());
                case DATA_LAST_ENERGY_HI -> high(machine.lastImpactEnergy());
                case DATA_LAST_DISTANCE -> machine.lastFallDistance();
                case DATA_MAX_DISTANCE -> machine.maxFallDistance();
                default -> 0;
            };
        }
    }
}
