package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.generator.HydrostaticGeneratorBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;

/** A generator with nothing to put in it: upgrades, energy, and a readout of the shaft above. */
public class HydrostaticGeneratorMenu extends MachineMenu<HydrostaticGeneratorBlockEntity> {
    /** Water blocks currently standing above the generator. */
    public static final int DATA_COLUMN = BASE_DATA_SIZE;
    /** The depth past which more water pays nothing. */
    public static final int DATA_MAX_COLUMN = BASE_DATA_SIZE + 1;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 2;

    /** Server side: reads live values straight off the machine. */
    public HydrostaticGeneratorMenu(int containerId,
                                    Inventory playerInventory,
                                    HydrostaticGeneratorBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public HydrostaticGeneratorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private HydrostaticGeneratorMenu(int containerId,
                                     Inventory playerInventory,
                                     HydrostaticGeneratorBlockEntity machine,
                                     ContainerData data) {
        super(ModMenus.HYDROSTATIC_GENERATOR.get(), containerId, playerInventory, machine, data);
    }

    private static HydrostaticGeneratorBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof HydrostaticGeneratorBlockEntity generator) {
            return generator;
        }
        throw new IllegalStateException("no hydrostatic generator at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        // Nothing goes in and nothing comes out; the world is the input.
    }

    @Override
    protected int machineSlotCount() {
        return 0;
    }

    @Override
    public boolean hasGauge() {
        // the water column is the level it fills.
        return true;
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.HYDROSTATIC_GENERATOR.get();
    }

    public int column() {
        return data.get(DATA_COLUMN);
    }

    public int maxColumn() {
        return data.get(DATA_MAX_COLUMN);
    }

    /** FE/t at the current depth. */
    public int energyPerTick() {
        return energyRate();
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<HydrostaticGeneratorBlockEntity> {
        MachineData(HydrostaticGeneratorBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_COLUMN -> machine.columnHeight();
                case DATA_MAX_COLUMN -> machine.maxColumn();
                default -> 0;
            };
        }
    }
}
