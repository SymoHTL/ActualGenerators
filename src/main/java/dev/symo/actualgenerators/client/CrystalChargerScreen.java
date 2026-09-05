package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.CrystalChargerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CrystalChargerScreen extends MachineScreen<CrystalChargerMenu> {
    /** Orange for energy leaving the buffer, blue for energy coming back into it. */
    private static final int CHARGING_COLOUR = 0xFFD8813E;
    private static final int DISCHARGING_COLOUR = 0xFF4FD8E8;

    public CrystalChargerScreen(CrystalChargerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected boolean isGenerator() {
        // In discharge mode it is one, and that is the mode where a yield figure would matter.
        return menu.isDischarging();
    }

    @Override
    protected int modeColour() {
        return menu.isDischarging() ? DISCHARGING_COLOUR : CHARGING_COLOUR;
    }

    @Override
    protected Component modeTooltip() {
        return Component.translatable(menu.isDischarging()
                ? "gui.actualgenerators.mode.discharging"
                : "gui.actualgenerators.mode.charging");
    }
}
