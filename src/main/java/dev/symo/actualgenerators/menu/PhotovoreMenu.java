package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.generator.PhotovoreBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;

/** No slots: what it eats is in the world, so the screen reports on the room instead. */
public class PhotovoreMenu extends MachineMenu<PhotovoreBlockEntity> {
    /** How far through the current meal, in thousandths. */
    public static final int DATA_PROGRESS_PERMILLE = BASE_DATA_SIZE;
    /** Light sources it currently knows about. */
    public static final int DATA_FOOD = BASE_DATA_SIZE + 1;
    /** FE the next meal would earn, low half. */
    public static final int DATA_MEAL_ENERGY_LO = BASE_DATA_SIZE + 2;
    public static final int DATA_MEAL_ENERGY_HI = BASE_DATA_SIZE + 3;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 4;

    /** Server side: reads live values straight off the machine. */
    public PhotovoreMenu(int containerId, Inventory playerInventory, PhotovoreBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public PhotovoreMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private PhotovoreMenu(int containerId,
                          Inventory playerInventory,
                          PhotovoreBlockEntity machine,
                          ContainerData data) {
        super(ModMenus.PHOTOVORE.get(), containerId, playerInventory, machine, data);
    }

    private static PhotovoreBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof PhotovoreBlockEntity photovore) {
            return photovore;
        }
        throw new IllegalStateException("no photovore at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        // Its input is the lighting around it.
    }

    @Override
    protected int machineSlotCount() {
        return 0;
    }

    @Override
    public boolean hasGauge() {
        // the meal it is chewing on is the level it fills.
        return true;
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.PHOTOVORE.get();
    }

    /** 0 to 1 through the current meal. */
    public double progress() {
        return data.get(DATA_PROGRESS_PERMILLE) / (double) PERMILLE;
    }

    public int foodInRange() {
        return data.get(DATA_FOOD);
    }

    public int mealEnergy() {
        return wide(DATA_MEAL_ENERGY_LO);
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<PhotovoreBlockEntity> {
        MachineData(PhotovoreBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_PROGRESS_PERMILLE -> permille(
                        machine.progress() / (double) machine.ticksForOperation(machine.baseGrazeTicks()));
                case DATA_FOOD -> machine.foodInRange();
                case DATA_MEAL_ENERGY_LO -> low(machine.nextMealEnergy());
                case DATA_MEAL_ENERGY_HI -> high(machine.nextMealEnergy());
                default -> 0;
            };
        }
    }
}
