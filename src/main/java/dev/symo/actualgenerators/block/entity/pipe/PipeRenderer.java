package dev.symo.actualgenerators.block.entity.pipe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.symo.actualgenerators.block.entity.pipe.config.EConnectionType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public class PipeRenderer implements BlockEntityRenderer<ItemPipeBlockEntity> {

    // models
    //            pipe_core.json
    //            pipe_horizontal_both.json
    //            pipe_horizontal_extract.json
    //            pipe_horizontal_insert.json
    //            pipe_horizontal_cable.json
    //            pipe_vertical_both.json
    //            pipe_vertical_extract.json
    //            pipe_vertical_insert.json
    //            pipe_vertical_cable.json
    private static final ResourceLocation[] MODELS = new ResourceLocation[]{
            new ResourceLocation("actualgenerators", "block/pipe/pipe_core"), // 0
            new ResourceLocation("actualgenerators", "block/pipe/pipe_horizontal_both"), // 1
            new ResourceLocation("actualgenerators", "block/pipe/pipe_horizontal_extract"), // 2
            new ResourceLocation("actualgenerators", "block/pipe/pipe_horizontal_insert"), // 3
            new ResourceLocation("actualgenerators", "block/pipe/pipe_horizontal_cable"), // 4
    };

    private final BlockRenderDispatcher blockRenderer;
    private final ModelManager modelManager;

    public PipeRenderer(BlockEntityRendererProvider.Context context) {
        this.modelManager = context.getBlockRenderDispatcher().getBlockModelShaper().getModelManager();
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ItemPipeBlockEntity pipe, float partialTicks, PoseStack ms, MultiBufferSource buffers, int combinedLightIn, int combinedOverlayIn) {
        var state = pipe.getBlockState();
        var pos = pipe.getBlockPos();
        var buffer = buffers.getBuffer(RenderType.cutout());
        var level = pipe.getLevel();

        Direction[] directions = Direction.values();

        // Render core
        ms.pushPose();
        renderModel(modelManager.getModel(MODELS[0]), level, pos, state, ms, buffer, combinedLightIn, combinedOverlayIn);

        for (Direction direction : directions) {
            var connectionType = pipe.connectionTypes[direction.get3DDataValue()];
            if (connectionType == null)
                continue;
            if (connectionType == EConnectionType.NONE)
                continue;
            switch (connectionType) {
                case CABLE -> {
                    ms.pushPose();
                    applyRotation(ms, direction);
                    renderModel(modelManager.getModel(MODELS[4]), level, pos, state, ms, buffer, combinedLightIn, combinedOverlayIn);
                }
                case INPUT -> {
                    ms.pushPose();
                    applyRotation(ms, direction);
                    renderModel(modelManager.getModel(MODELS[3]), level, pos, state, ms, buffer, combinedLightIn, combinedOverlayIn);
                }
                case OUTPUT -> {
                    ms.pushPose();
                    applyRotation(ms, direction);
                    renderModel(modelManager.getModel(MODELS[2]), level, pos, state, ms, buffer, combinedLightIn, combinedOverlayIn);
                }
                case BOTH -> {
                    ms.pushPose();
                    applyRotation(ms, direction);
                    renderModel(modelManager.getModel(MODELS[1]), level, pos, state, ms, buffer, combinedLightIn, combinedOverlayIn);
                }
            }
        }
    }

    private void renderModel(BakedModel baked, Level level, BlockPos pos, BlockState state, PoseStack stack, VertexConsumer buffer, int lightIn, int overlayIn) {
        blockRenderer.getModelRenderer().tesselateWithAO(
                level,
                baked,
                state,
                pos,
                stack,
                buffer,
                false,
                RandomSource.create(),
                state.getSeed(pos),
                overlayIn
        );
        stack.popPose();
    }


    private void applyRotation(PoseStack poseStack, Direction direction) {
        switch (direction) {
            case UP -> poseStack.mulPose(new Quaternionf(1, 0, 0, 90));
            case DOWN -> poseStack.mulPose(new Quaternionf(1, 0, 0, -90));
            case SOUTH -> poseStack.mulPose(new Quaternionf(0, 1, 0, 180));
            case WEST -> poseStack.mulPose(new Quaternionf(0, 1, 0, -90));
            case EAST -> poseStack.mulPose(new Quaternionf(0, 1, 0, 90));
        }
    }


    @Override
    public boolean shouldRenderOffScreen(ItemPipeBlockEntity blockEntity) {
        return true;
    }

}
