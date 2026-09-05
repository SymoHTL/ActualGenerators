package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.logistics.EnergyInjectorBlockEntity;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Block;

/**
 * A buffer with a network attached: how much it holds, how many pads it is paying for, and the
 * network line that picks or leaves a network, same as on a pad.
 */
public class EnergyInjectorMenu extends MachineMenu<EnergyInjectorBlockEntity> {
    public static final int DATA_LINKED = BASE_DATA_SIZE;
    public static final int DATA_PORTS = BASE_DATA_SIZE + 1;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 2;

    /** Well clear of the machine menu's own button ids. */
    public static final int BUTTON_NETWORK_PICK = 300;
    public static final int BUTTON_NETWORK_LEAVE = 301;

    /** The network line, in the readout area where a machine with slots would have none. */
    public static final MachineLayout.Box NETWORK_BUTTON = new MachineLayout.Box("network button", 12, 38, 116, 12);

    /** Server side: reads live values straight off the injector. */
    public EnergyInjectorMenu(int containerId, Inventory playerInventory, EnergyInjectorBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public EnergyInjectorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private EnergyInjectorMenu(int containerId,
                               Inventory playerInventory,
                               EnergyInjectorBlockEntity machine,
                               ContainerData data) {
        super(ModMenus.ENERGY_INJECTOR.get(), containerId, playerInventory, machine, data);
    }

    private static EnergyInjectorBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof EnergyInjectorBlockEntity injector) {
            return injector;
        }
        throw new IllegalStateException("no energy injector at " + pos);
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
        return ModBlocks.ENERGY_INJECTOR.get();
    }

    public boolean isLinked() {
        return data.get(DATA_LINKED) != 0;
    }

    /** Pads on the network this injector is paying for. */
    public int servedPorts() {
        return data.get(DATA_PORTS);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!mayUse(player)) {
            return false;
        }
        if (id == BUTTON_NETWORK_PICK) {
            if (player instanceof ServerPlayer serverPlayer && machine.getLevel() instanceof ServerLevel level) {
                NetworkPickerMenu.open(serverPlayer, level, machine.getBlockPos(), null, true);
            }
            return true;
        }
        if (id == BUTTON_NETWORK_LEAVE) {
            if (machine.getLevel() instanceof ServerLevel level) {
                LinkNetworkManager.get(level.getServer()).leaveInjector(machine);
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && mayUse(player);
    }

    /** A linked injector is its network's: the window is the owner's and the invited's. */
    private boolean mayUse(Player player) {
        return !machine.isLinked() || !(machine.getLevel() instanceof ServerLevel level)
                || LinkNetworkManager.get(level.getServer()).canAccess(machine.networkId(), player);
    }

    /** Live view of the injector, read by the server and mirrored to the client each tick. */
    private static class MachineData extends MachineMenu.MachineData<EnergyInjectorBlockEntity> {
        MachineData(EnergyInjectorBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_LINKED -> machine.isLinked() ? 1 : 0;
                case DATA_PORTS -> machine.servedPorts();
                default -> 0;
            };
        }
    }
}
