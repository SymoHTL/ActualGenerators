package dev.symo.actualgenerators.menu;

import java.util.Locale;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import java.util.List;

/**
 * Where the fixed parts of a machine window sit.
 *
 * <p>Screens are client-only, so a test running on a server cannot ask one where it drew things.
 * The coordinates therefore live here, on the menu side, and the screens read them. That is the
 * whole point of this class: one place says where the energy bar, the overclock bar, the buttons
 * and the gauge are, so a slot laid on top of one of them is a clash a test can find rather than
 * something a player has to notice.
 *
 * <p>Boxes are the area actually drawn, frames included, in window coordinates.
 */
public final class MachineLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 194;

    /** A rectangle in window coordinates, named so a clash can say what clashed with what. */
    public record Box(String name, int x, int y, int width, int height) {
        /** The same box with a border of {@code amount} pixels on every side. */
        public Box grow(int amount) {
            return new Box(name, x - amount, y - amount, width + amount * 2, height + amount * 2);
        }

        /** Touching edge to edge is fine — adjacent slot frames share their border pixel. */
        public boolean overlaps(Box other) {
            return x < other.x + other.width && other.x < x + width
                    && y < other.y + other.height && other.y < y + height;
        }

        public boolean fitsInWindow() {
            return fitsInWindow(WIDTH, HEIGHT);
        }

        /** For a window that is not the standard machine size — the port's is taller. */
        public boolean fitsInWindow(int windowWidth, int windowHeight) {
            return x >= 0 && y >= 0 && x + width <= windowWidth && y + height <= windowHeight;
        }
    }

    /** The coloured part of each gauge; each is drawn inside a frame one pixel wider all round. */
    public static final Box ENERGY_FILL = new Box("energy bar", 152, 18, 12, 53);
    public static final Box OVERCLOCK_FILL = new Box("overclock bar", 84, 78, 46, 16);
    public static final Box GAUGE_FILL = new Box("gauge", 130, 18, 16, 53);
    /** A fluid tank the same shape as the gauge, to its left, for the machines that hold one. */
    public static final Box TANK_FILL = new Box("tank", 108, 18, 16, 53);

    /** The left-to-right progress arrow, where a machine that has one draws it. */
    public static final Box PROGRESS_ARROW = new Box("progress arrow", 69, 36, 24, 13);

    /** Buttons draw their own border, so these are already the full extent. */
    public static final Box REDSTONE_BUTTON = new Box("redstone button", 151, 77, 18, 18);
    public static final Box CONFIG_BUTTON = new Box("side config button", 133, 77, 18, 18);

    /**
     * The bottom row of a multiblock controller's window, left of the redstone button: the preview
     * button and the three size steppers before the box stands. It covers the ramp bar, so a
     * controller shows one or the other.
     */
    public static final Box MULTIBLOCK_ROW = new Box("multiblock row", 8, 77, 124, 18);
    public static final Box PREVIEW_BUTTON = new Box("preview button", 8, 77, 18, 18);

    /** One size stepper: a letter, the number, and two arrows. */
    public static Box stepper(MultiblockControllerBlockEntity.Extent extent) {
        return new Box(extent.name().toLowerCase(Locale.ROOT) + " stepper", 28 + extent.ordinal() * 35, 77, 33, 18);
    }
    public static final Box MODE_BUTTON = new Box("mode button", 115, 77, 18, 18);

    /** Everything every machine window draws, whatever machine it belongs to. */
    public static List<Box> chrome() {
        return List.of(ENERGY_FILL.grow(1), REDSTONE_BUTTON, CONFIG_BUTTON);
    }

    /** The progress arrow, drawn only by machines with an operation to be part way through. */
    public static Box arrow() {
        return PROGRESS_ARROW;
    }

    /** The mode button, drawn only by machines that have two ways to run. */
    public static Box mode() {
        return MODE_BUTTON;
    }

    /** The ramp bar, drawn only by machines that have a speed to ramp. */
    public static Box ramp() {
        return OVERCLOCK_FILL.grow(1);
    }

    /** The tall gauge, drawn only by generator windows that have a level worth showing. */
    public static Box gauge() {
        return GAUGE_FILL.grow(1);
    }

    /** The tank, drawn only by machines that hold a fluid. */
    public static Box tank() {
        return TANK_FILL.grow(1);
    }

    /** The frame a slot draws, which is one pixel bigger than the 16x16 slot on every side. */
    public static Box slotFrame(String name, int x, int y) {
        return new Box(name, x - 1, y - 1, 18, 18);
    }

    private MachineLayout() {
    }
}
