package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.RelativeSide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity.Extent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The window of a multiblock controller: no side configuration, because the controller's own
 * faces move nothing and the hatches in the shell do; the structure as the client's copy of the
 * block entity knows it; and, while none stands, the preview row along the bottom. The preview's
 * three sizes are the controller's and travel as container data; a stepper click is a menu
 * button carrying the extent and the step, and the server clamps and keeps it.
 *
 * <p>Opened from a hatch, the window also carries that hatch, and the side panel every machine
 * has becomes the hatch's: its six faces relative to the way it was placed, faces that look into
 * the structure greyed out, and the IN and OUT toggles. The same panel, the same buttons, read
 * from the hatch and written to it; one window for the whole structure, Mekanism's way.
 */
public abstract class MultiblockControllerMenu<T extends MultiblockControllerBlockEntity> extends MachineMenu<T> {
    /** The preview's width, height and depth, at {@code DATA_PREVIEW + extent.ordinal()}. */
    public static final int DATA_PREVIEW = BASE_DATA_SIZE;
    /** Where a controller's own data starts: after the three sizes (a constant, so it can label a switch case). */
    public static final int MULTIBLOCK_DATA_SIZE = BASE_DATA_SIZE + 3;

    /** A stepper click is {@code BUTTON_PREVIEW_START + extent.ordinal() * PREVIEW_SPAN + step + PREVIEW_HALF}. */
    public static final int BUTTON_PREVIEW_START = 300;
    private static final int PREVIEW_SPAN = 64;
    private static final int PREVIEW_HALF = PREVIEW_SPAN / 2;

    /** The hatch's six face modes, then its auto flags (bit 0 pull, bit 1 push), then the faces that look into the structure. */
    private static final int HATCH_DATA_SIZE = RelativeSide.all().length + 2;
    private static final int HATCH_AUTO = RelativeSide.all().length;
    private static final int HATCH_BLOCKED = HATCH_AUTO + 1;

    /** The hatch this window was opened from, whose faces the side panel shows; null when opened at the controller. */
    private @Nullable HatchBlockEntity hatch;
    /** The hatch's values as they arrived on the client. */
    private final int[] hatchSync = new int[HATCH_DATA_SIZE];
    private final boolean clientSide;
    private final ContainerData hatchData = new ContainerData() {
        @Override
        public int get(int index) {
            if (clientSide || hatch == null) {
                return hatchSync[index];
            }
            if (index < HATCH_AUTO) {
                return hatch.mode(RelativeSide.byOrdinal(index)).ordinal();
            }
            return index == HATCH_AUTO
                    ? (hatch.autoPull() ? 1 : 0) | (hatch.autoPush() ? 2 : 0)
                    : hatch.blockedMask();
        }

        @Override
        public void set(int index, int value) {
            hatchSync[index] = value;
        }

        @Override
        public int getCount() {
            return HATCH_DATA_SIZE;
        }
    };

    protected MultiblockControllerMenu(MenuType<?> type,
                                       int containerId,
                                       Inventory playerInventory,
                                       T machine,
                                       ContainerData data) {
        super(type, containerId, playerInventory, machine, data);
        clientSide = playerInventory.player.level().isClientSide;
        addDataSlots(hatchData);
    }

    /** The hatch this window shows the faces of, or null. */
    public @Nullable HatchBlockEntity hatch() {
        return hatch;
    }

    /** Tells the window which hatch it was opened from; the server hands over the block entity, the client the one it read. */
    public void withHatch(@Nullable HatchBlockEntity hatch) {
        this.hatch = hatch;
    }

    // The side panel is the hatch's while there is one: the same panel every machine has, the
    // same buttons, read from the hatch and written to it.

    @Override
    public boolean hasSideConfig() {
        return hatch != null;
    }

    @Override
    public boolean sidePanelOpenAtStart() {
        return hatch != null;
    }

    @Override
    public boolean supportsKind(TransferKind kind) {
        return hatch != null && kind == hatch.transferKind();
    }

    @Override
    public IoMode sideMode(TransferKind kind, RelativeSide side) {
        return hatch != null ? IoMode.byOrdinal(hatchData.get(side.ordinal())) : super.sideMode(kind, side);
    }

    @Override
    public boolean autoEnabled(TransferKind kind, boolean push) {
        return hatch != null ? (hatchData.get(HATCH_AUTO) & (push ? 2 : 1)) != 0 : super.autoEnabled(kind, push);
    }

    @Override
    public boolean sideBlocked(RelativeSide side) {
        return hatch != null && (hatchData.get(HATCH_BLOCKED) & (1 << side.ordinal())) != 0;
    }

    /**
     * The hatch a client window was opened from, read off the opening packet after the
     * controller's position; null when the window was opened at the controller itself.
     */
    protected static @Nullable HatchBlockEntity readHatch(Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        if (!buffer.isReadable()) {
            return null;
        }
        return playerInventory.player.level().getBlockEntity(buffer.readBlockPos()) instanceof HatchBlockEntity hatch ? hatch : null;
    }

    /** The preview and its three sizes. A window that shows something else there once the structure stands overrides. */
    @Override
    public List<MachineLayout.Box> extraChrome() {
        return List.of(MachineLayout.MULTIBLOCK_ROW);
    }

    public boolean formed() {
        return machine.isFormed();
    }

    /** The structure as the client's copy of the block entity knows it; null while nothing is formed. */
    public @Nullable MultiblockControllerBlockEntity.Structure structure() {
        return machine.structure();
    }

    public int minSize(Extent extent) {
        return machine.minSize(extent);
    }

    public int maxSize(Extent extent) {
        return machine.maxSize(extent);
    }

    public int sizeStep(Extent extent) {
        return machine.sizeStep(extent);
    }

    /** The size the preview shows for this extent, as synced. */
    public int previewSize(Extent extent) {
        return data.get(DATA_PREVIEW + extent.ordinal());
    }

    /** The button that steps an extent's preview size by {@code step}, up or down. */
    public static int previewButton(Extent extent, int step) {
        return BUTTON_PREVIEW_START + extent.ordinal() * PREVIEW_SPAN
                + Math.clamp(step, -PREVIEW_HALF, PREVIEW_HALF - 1) + PREVIEW_HALF;
    }

    /** For a controller's {@code MachineData.extra}: the preview sizes, or -1 when the index is not one of them. */
    protected static int previewData(MultiblockControllerBlockEntity machine, int index) {
        int extent = index - DATA_PREVIEW;
        return extent >= 0 && extent < Extent.values().length ? machine.previewSize(Extent.values()[extent]) : -1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (hatch != null) {
            int sideButton = id - BUTTON_SIDES_START;
            if (sideButton >= 0 && sideButton < TransferKind.material().length * RelativeSide.all().length) {
                TransferKind kind = TransferKind.byOrdinal(sideButton / RelativeSide.all().length);
                RelativeSide side = RelativeSide.byOrdinal(sideButton % RelativeSide.all().length);
                if (kind != hatch.transferKind() || hatch.blocked(side)) {
                    return false;
                }
                hatch.cycleSide(side);
                return true;
            }
            int autoButton = id - BUTTON_AUTO_START;
            if (autoButton >= 0 && autoButton < TransferKind.material().length * 2) {
                if (TransferKind.byOrdinal(autoButton / 2) != hatch.transferKind()) {
                    return false;
                }
                hatch.toggleAuto(autoButton % 2 == 1);
                return true;
            }
        }
        int button = id - BUTTON_PREVIEW_START;
        if (button >= 0 && button < Extent.values().length * PREVIEW_SPAN) {
            Extent extent = Extent.values()[button / PREVIEW_SPAN];
            machine.setPreviewSize(extent, machine.previewSize(extent) + button % PREVIEW_SPAN - PREVIEW_HALF);
            return true;
        }
        return super.clickMenuButton(player, id);
    }
}
