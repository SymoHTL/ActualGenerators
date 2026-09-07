package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.HatchSignal;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A redstone hatch's window: one row per thing it can do, the chosen one lit, and the level it is
 * giving off. Nothing to hold, so no inventory under it.
 */
public class RedstoneHatchMenu extends AbstractContainerMenu {
    public static final int DATA_MODE = 0;
    public static final int DATA_LEVEL = 1;
    public static final int DATA_SIZE = 2;

    public static final int WIDTH = MachineLayout.WIDTH;
    public static final int ROW_HEIGHT = 17;
    private static final int ROW_Y = 20;
    public static final int HEIGHT = ROW_Y + HatchSignal.values().length * ROW_HEIGHT + 6;

    public static final MachineLayout.Box TITLE = new MachineLayout.Box("title", 8, 6, 160, 9);

    /** A mode's row: a button the width of the window, and its id is the mode's ordinal. */
    public static MachineLayout.Box row(int mode) {
        return new MachineLayout.Box("mode " + mode, 8, ROW_Y + mode * ROW_HEIGHT, 160, ROW_HEIGHT - 1);
    }

    /** Everything the window draws, for the layout test. */
    public static List<MachineLayout.Box> chrome() {
        List<MachineLayout.Box> boxes = new ArrayList<>(List.of(TITLE));
        for (int mode = 0; mode < HatchSignal.values().length; mode++) {
            boxes.add(row(mode));
        }
        return boxes;
    }

    private final HatchBlockEntity hatch;
    private final ContainerData data;

    /** Server side: reads the hatch itself. */
    public RedstoneHatchMenu(int containerId, Inventory playerInventory, HatchBlockEntity hatch) {
        this(containerId, hatch, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_MODE -> hatch.mode().ordinal();
                    case DATA_LEVEL -> hatch.emitted();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        });
    }

    /** Client side: the hatch is found again from the position in the opening packet. */
    public RedstoneHatchMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, readHatch(playerInventory, buffer.readBlockPos()), new SimpleContainerData(DATA_SIZE));
    }

    private RedstoneHatchMenu(int containerId, HatchBlockEntity hatch, ContainerData data) {
        super(ModMenus.REDSTONE_HATCH.get(), containerId);
        this.hatch = hatch;
        this.data = data;
        addDataSlots(data);
    }

    private static HatchBlockEntity readHatch(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof HatchBlockEntity hatch) {
            return hatch;
        }
        throw new IllegalStateException("no hatch at " + pos);
    }

    public HatchSignal mode() {
        return HatchSignal.byOrdinal(data.get(DATA_MODE));
    }

    /** What the hatch is giving off, nought to fifteen. */
    public int level() {
        return data.get(DATA_LEVEL);
    }

    /** Whether the hatch is part of a standing box, as far as the client's copy of it knows. */
    public boolean attached() {
        return hatch.controller() != null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= HatchSignal.values().length) {
            return false;
        }
        hatch.setMode(HatchSignal.byOrdinal(id));
        MultiblockControllerBlockEntity controller = hatch.controller();
        if (controller != null) {
            controller.refreshSignals();
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.canInteractWithBlock(hatch.getBlockPos(), 4.0);
    }
}
