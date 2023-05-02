package dev.symo.actualgenerators.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import dev.symo.actualgenerators.block.entity.pipe.config.EMode;
import dev.symo.actualgenerators.block.entity.pipe.config.PipeIO;
import dev.symo.actualgenerators.block.entity.pipe.config.PipeInput;
import dev.symo.actualgenerators.block.entity.pipe.config.PipeOutput;
import dev.symo.actualgenerators.net.ModMessages;
import dev.symo.actualgenerators.net.packet.SetPriorityC2SPacket;
import dev.symo.actualgenerators.net.packet.SwitchChannelC2SPacket;
import dev.symo.actualgenerators.net.packet.SwitchPipeModeC2SPacket;
import dev.symo.actualgenerators.net.packet.SwitchRedstoneModeC2SPacket;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ItemPipeBlockScreen extends AbstractContainerScreen<ItemPipeBlockMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ActualGenerators.MOD_ID, "textures/gui/item_pipe_cfg_screen.png");
    private final List<AbstractWidget> outputWidgets = new ArrayList<>();
    private final List<AbstractWidget> inputWidgets = new ArrayList<>();
    private ItemPipeBlockEntity pipeBlockEntity;
    private Direction direction;
    private PipeOutput output;
    private PipeInput input;
    private EMode mode = EMode.DISABLED;
    private int xOffset;
    private int yOffset;

    public ItemPipeBlockScreen(ItemPipeBlockMenu menu, Inventory inv, Component component) {
        super(menu, inv, component);
        this.imageHeight = 114 + 6 * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        menu.eventHandler.add(this::pipeChanged);
        pipeBlockEntity = menu.pipeBlockEntity;
        direction = menu.direction;
        output = pipeBlockEntity.getOutput(direction);
        input = pipeBlockEntity.getInput(direction);

        if (output != null) mode = output.Mode;
        else if (input != null) mode = input.Mode;

        xOffset = (width - imageWidth) / 2;
        yOffset = (height - imageHeight) / 2;

        addModeSwitcher(xOffset, yOffset);
        updateWidgets(xOffset, yOffset);
    }

    @Override
    public void onClose() {
        super.onClose();
        menu.eventHandler.remove(this::pipeChanged);
    }

    private void pipeChanged() {
        output = pipeBlockEntity.getOutput(direction);
        input = pipeBlockEntity.getInput(direction);
        updateWidgets(xOffset, yOffset);
    }

    private void updateWidgets(int xOffset, int yOffset) {
        for (AbstractWidget widget : outputWidgets)
            removeWidget(widget);
        for (AbstractWidget widget : inputWidgets)
            removeWidget(widget);

        outputWidgets.clear();
        inputWidgets.clear();
        if (mode == EMode.DISABLED) {
            return;
        }

        if (output != null && mode != EMode.EXTRACT) {
            addWidgets(outputWidgets, output, xOffset + 87, yOffset);
        }
        if (input != null && mode != EMode.INSERT) {
            addWidgets(inputWidgets, input, xOffset, yOffset);
        }
    }

    private void addWidgets(List<AbstractWidget> widgets, PipeIO io, int xOffset, int yOffset) {
        widgets.add(addChannelSwitcher(io, xOffset, yOffset));
        widgets.add(addLeftPriorityButton(io, xOffset, yOffset));
        widgets.add(addRightPriorityButton(io, xOffset, yOffset));
        widgets.add(addRedstoneSwitcher(io, xOffset, yOffset));
    }

    // blit param explanation:
    // blit(PoseStack, x, y, u, v, width, height)
    // x and y are the coordinates of the top left corner of the image
    // u and v are the coordinates of the top left corner of the image in the texture file
    // width and height are the width and height of the image in the texture file

    @Override
    protected void renderBg(PoseStack stack, float partialTick, int MouseX, int MouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        blit(stack, x, y, 0, 0, imageWidth, 125);
        blit(stack, x, y + 125, 0, 126, this.imageWidth, 96);


        //blit(stack, x + 105, y + 33, 176, 0, 18, 18);
    }
    private void addModeSwitcher(int xOffset, int yOffset) {
        var button = Button.builder(Component.literal(mode.toString()), b -> {
                    mode = mode.next();
                    b.setMessage(Component.literal(mode.toString()));
                    b.setTooltip(Tooltip.create(Component.literal("Mode: " + mode.toString())));

                    ModMessages.sendToServer(new SwitchPipeModeC2SPacket(mode, direction, pipeBlockEntity.getBlockPos()));

                    updateWidgets(xOffset, yOffset);
                }).bounds(xOffset + xOffset / 2, 20 + yOffset, 58, 19)
                .tooltip(Tooltip.create(Component.literal("Mode: " + mode.toString())))
                .build();
        addRenderableWidget(button);
    }

    private AbstractWidget addChannelSwitcher(PipeIO io, int xOffset, int yOffset) {
        var button = Button.builder(Component.literal(io.Channel.toString()), b -> {
                    io.Channel = io.Channel.next();
                    ModMessages.sendToServer(new SwitchChannelC2SPacket(io.Channel, direction, pipeBlockEntity.getBlockPos(), io instanceof PipeInput));

                    b.setMessage(Component.literal(io.Channel.toString()));
                    b.setTooltip(Tooltip.create(Component.literal("Channel: " + io.Channel.toString())));
                }).bounds(7 + xOffset, 70 + yOffset, 58, 19)
                .tooltip(Tooltip.create(Component.literal("Channel: " + io.Channel.toString())))
                .build();
        addRenderableWidget(button);
        return button;
    }

    private AbstractWidget addRedstoneSwitcher(PipeIO io, int xOffset, int yOffset) {
        var button = Button.builder(Component.literal(io.RedstoneMode.toString()), b -> {
                    io.RedstoneMode = io.RedstoneMode.next();
                    ModMessages.sendToServer(new SwitchRedstoneModeC2SPacket(io.RedstoneMode,
                            direction, pipeBlockEntity.getBlockPos(), io instanceof PipeInput));

                    b.setMessage(Component.literal(io.RedstoneMode.toString()));
                    b.setTooltip(Tooltip.create(Component.literal("Redstone Mode: " + io.RedstoneMode.toString())));
                }).bounds(7 + xOffset, 100 + yOffset, 17, 17)
                .tooltip(Tooltip.create(Component.literal("Redstone Mode: " + io.RedstoneMode.toString())))
                .build();
        addRenderableWidget(button);
        return button;
    }

    private AbstractWidget addLeftPriorityButton(PipeIO io, int xOffset, int yOffset) {
        var button = Button.builder(Component.empty(), b -> {
            io.Priority = Math.max(-100, io.Priority - 1);
            ModMessages.sendToServer(new SetPriorityC2SPacket(io.Priority, direction, pipeBlockEntity.getBlockPos(), io instanceof PipeInput));

        }).bounds(25 + xOffset, 102 + yOffset, 9, 14).build();
        addRenderableWidget(button);
        return button;
    }

    private AbstractWidget addRightPriorityButton(PipeIO io, int xOffset, int yOffset) {
        var button = Button.builder(Component.empty(), b -> {
            io.Priority = Math.min(100, io.Priority + 1);
            ModMessages.sendToServer(new SetPriorityC2SPacket(io.Priority, direction, pipeBlockEntity.getBlockPos(), io instanceof PipeInput));

        }).bounds(45 + xOffset, 102 + yOffset, 9, 14).build();
        addRenderableWidget(button);
        return button;
    }

    @Override
    public void render(@NotNull PoseStack poseStack, int mouseX, int mouseY, float delta) {
        renderBackground(poseStack);
        switch (mode) {
            case EXTRACT -> font.draw(poseStack, "Extract", 7 + xOffset, 50 + yOffset, 0x404040);
            case INSERT -> font.draw(poseStack, "Insert", 7 + xOffset + 87, 50 + yOffset, 0x404040);
            case EXTRACT_INSERT -> {
                font.draw(poseStack, "Extract", 7 + xOffset, 50 + yOffset, 0x404040);
                font.draw(poseStack, "Insert", 7 + xOffset + 87, 50 + yOffset, 0x404040);
            }
        }
        super.render(poseStack, mouseX, mouseY, delta);
        renderTooltip(poseStack, mouseX, mouseY);
    }
}
