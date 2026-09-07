package dev.symo.actualgenerators.machine.multiblock;

/** What a hatch in a multiblock's shell lets through. One block per kind. */
public enum HatchKind {
    /** Items in and out of the controller's slots. */
    ITEM,
    /** The controller's FE buffer. */
    ENERGY,
    /** A redstone signal for the controller's redstone mode, read where the hatch is. */
    REDSTONE,
    /** The controller's tank, for the machines that hold a fluid. */
    FLUID
}
