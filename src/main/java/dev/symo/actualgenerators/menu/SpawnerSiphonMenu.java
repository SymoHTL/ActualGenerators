package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.generator.SpawnerSiphonBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;

/** No slots: its fuel is the spawner next door. */
public class SpawnerSiphonMenu extends MachineMenu<SpawnerSiphonBlockEntity> {
    /** Mobs per attempt, as the spawner itself reports it. */
    public static final int DATA_SPAWN_COUNT = BASE_DATA_SIZE;
    /** Ticks between attempts, averaged over the spawner's own range. */
    public static final int DATA_AVERAGE_DELAY = BASE_DATA_SIZE + 1;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 2;

    /** Server side: reads live values straight off the machine. */
    public SpawnerSiphonMenu(int containerId, Inventory playerInventory, SpawnerSiphonBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public SpawnerSiphonMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private SpawnerSiphonMenu(int containerId,
                              Inventory playerInventory,
                              SpawnerSiphonBlockEntity machine,
                              ContainerData data) {
        super(ModMenus.SPAWNER_SIPHON.get(), containerId, playerInventory, machine, data);
    }

    private static SpawnerSiphonBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof SpawnerSiphonBlockEntity siphon) {
            return siphon;
        }
        throw new IllegalStateException("no spawner siphon at " + pos);
    }

    @Override
    protected void addMachineSlots() {
        // Its input is the spawner it is bolted to.
    }

    @Override
    protected int machineSlotCount() {
        return 0;
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.SPAWNER_SIPHON.get();
    }

    public int energyPerTick() {
        return energyRate();
    }

    public int spawnCount() {
        return data.get(DATA_SPAWN_COUNT);
    }

    public int averageDelay() {
        return data.get(DATA_AVERAGE_DELAY);
    }

    public boolean hasSpawner() {
        return spawnCount() > 0 && averageDelay() > 0;
    }

    /** Live view of the machine, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<SpawnerSiphonBlockEntity> {
        MachineData(SpawnerSiphonBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_SPAWN_COUNT -> machine.spawnCount();
                case DATA_AVERAGE_DELAY -> machine.averageDelay();
                default -> 0;
            };
        }
    }
}
