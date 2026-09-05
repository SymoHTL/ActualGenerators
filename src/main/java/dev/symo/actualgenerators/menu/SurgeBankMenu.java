package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import dev.symo.actualgenerators.storage.SurgeBankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;

/** A battery with nothing to put in it: upgrades, energy, and what it is bleeding away. */
public class SurgeBankMenu extends MachineMenu<SurgeBankBlockEntity> {
    /** FE lost per second at the current charge. A long, so four slots. */
    public static final int DATA_LEAK = BASE_DATA_SIZE;
    /** Whether it is actually bleeding, rather than merely capable of it. */
    public static final int DATA_LEAKING = BASE_DATA_SIZE + 4;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 5;

    /** Server side: reads live values straight off the bank. */
    public SurgeBankMenu(int containerId, Inventory playerInventory, SurgeBankBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public SurgeBankMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private SurgeBankMenu(int containerId,
                          Inventory playerInventory,
                          SurgeBankBlockEntity machine,
                          ContainerData data) {
        super(ModMenus.SURGE_BANK.get(), containerId, playerInventory, machine, data);
    }

    private static SurgeBankBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof SurgeBankBlockEntity bank) {
            return bank;
        }
        throw new IllegalStateException("no surge bank at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        // Nothing goes in and nothing comes out; it holds power, not items.
    }

    @Override
    protected int machineSlotCount() {
        return 0;
    }

    @Override
    public boolean hasRamp() {
        // It converts nothing, so there is no speed to ramp and no bar that could ever move.
        return false;
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.SURGE_BANK.get();
    }

    /** FE it loses per second at the charge it is holding. */
    public long leakPerSecond() {
        return huge(DATA_LEAK);
    }

    public boolean isLeaking() {
        return data.get(DATA_LEAKING) != 0;
    }

    /** How full it is, 0 to 1. */
    public double chargeFraction() {
        long capacity = energyCapacity();
        return capacity <= 0 ? 0.0 : Math.clamp(energyStored() / (double) capacity, 0.0, 1.0);
    }

    /** Live view of the bank, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<SurgeBankBlockEntity> {
        MachineData(SurgeBankBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            if (index >= DATA_LEAK && index < DATA_LEAK + 4) {
                return pieceOf(machine.leakPerSecond(), DATA_LEAK, index);
            }
            return index == DATA_LEAKING && machine.isLeaking() ? 1 : 0;
        }
    }
}
