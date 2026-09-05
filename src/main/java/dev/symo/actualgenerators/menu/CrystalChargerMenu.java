package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import dev.symo.actualgenerators.storage.CrystalChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Crystals in, crystals out, and a button saying which way the energy is going. */
public class CrystalChargerMenu extends MachineMenu<CrystalChargerBlockEntity> {
    /** How far through the current batch, in thousandths. */
    public static final int DATA_PROGRESS_PERMILLE = BASE_DATA_SIZE;
    /** Which way round the machine is running. */
    public static final int DATA_DISCHARGING = BASE_DATA_SIZE + 1;
    public static final int DATA_SIZE = BASE_DATA_SIZE + 2;

    public static final int INPUT_SLOT_X = 44;
    public static final int INPUT_SLOT_Y = 34;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 34;

    private static final int MACHINE_SLOTS = 2;

    /** Server side: reads live values straight off the machine. */
    public CrystalChargerMenu(int containerId, Inventory playerInventory, CrystalChargerBlockEntity machine) {
        this(containerId, playerInventory, machine, new MachineData(machine));
    }

    /** Client side: values arrive through the container data sync. */
    public CrystalChargerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readMachine(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    private CrystalChargerMenu(int containerId,
                                 Inventory playerInventory,
                                 CrystalChargerBlockEntity machine,
                                 ContainerData data) {
        super(ModMenus.CRYSTAL_CHARGER.get(), containerId, playerInventory, machine, data);
    }

    private static CrystalChargerBlockEntity readMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof CrystalChargerBlockEntity charger) {
            return charger;
        }
        throw new IllegalStateException("no crystal charger at " + pos);
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
    public boolean hasRamp() {
        // It transfers rather than converts, so there is no speed to ramp.
        return false;
    }

    @Override
    public boolean hasModeButton() {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_MODE) {
            machine.toggleMode();
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    protected Block machineBlock() {
        return ModBlocks.CRYSTAL_CHARGER.get();
    }

    @Override
    public boolean hasProgressArrow() {
        return true;
    }

    /** 0 to 1 through the current batch. */
    @Override
    public double progress() {
        return data.get(DATA_PROGRESS_PERMILLE) / (double) PERMILLE;
    }

    /** True when it is emptying crystals rather than filling them. */
    public boolean isDischarging() {
        return data.get(DATA_DISCHARGING) != 0;
    }

    /** Finished crystals may be taken out but never put back. */
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
    private static class MachineData extends MachineMenu.MachineData<CrystalChargerBlockEntity> {
        MachineData(CrystalChargerBlockEntity machine) {
            super(machine, DATA_SIZE);
        }

        @Override
        protected int extra(int index) {
            return switch (index) {
                case DATA_PROGRESS_PERMILLE -> permille(machine.progress());
                case DATA_DISCHARGING -> machine.isDischarging() ? 1 : 0;
                default -> 0;
            };
        }
    }
}
