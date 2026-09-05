package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.SpawnerSiphonMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class SpawnerSiphonScreen extends GeneratorScreen<SpawnerSiphonMenu> {
    public SpawnerSiphonScreen(SpawnerSiphonMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        if (!menu.hasSpawner()) {
            renderReadout(graphics,
                    Component.translatable("gui.actualgenerators.no_spawner"),
                    Component.translatable("gui.actualgenerators.no_spawner.hint"));
            return;
        }
        renderReadout(graphics,
                Component.translatable("gui.actualgenerators.output", formatNumber(menu.energyPerTick())),
                Component.translatable("gui.actualgenerators.spawn_count", menu.spawnCount()),
                Component.translatable("gui.actualgenerators.spawn_delay", menu.averageDelay()));
    }

    @Override
    protected List<FormattedCharSequence> gaugeTooltip() {
        return List.of();
    }
}
