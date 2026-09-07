package dev.symo.actualgenerators.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.machine.multiblock.HatchBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The hologram of a structure that is not built yet, and the spots a held hatch may go.
 *
 * <p>For every controller with a preview switched on ({@link MultiblockPreview}), a ghost of every
 * block the structure still needs, drawn with the block's own model at half strength: Machine
 * Casing where casing goes, the machine's own casing where that goes; an orange slab on a floor
 * that wants a fluid; and red, through walls, on anything standing where the structure wants
 * something else, because what is in the way is usually behind the wall you are looking at.
 * What is already right is not drawn, so a structure being built empties its own picture as it
 * goes. The whole outline and a label sit over it.
 *
 * <p>With a hatch in either hand, every place a hatch may go wears a green frame: the shell of
 * every preview and of every standing structure the client knows, built or not, since a hatch
 * used on a casing takes its place ({@link dev.symo.actualgenerators.machine.multiblock.HatchBlockItem}).
 *
 * <p>The controller says where the structure stands and what goes where ({@link
 * MultiblockControllerBlockEntity#bounds}, {@link MultiblockControllerBlockEntity#requirementAt}),
 * the same answer the tests build from and the guide draws.
 */
@EventBusSubscriber(modid = ActualGenerators.MODID, value = Dist.CLIENT)
public final class MultiblockPreviewRenderer {
    private static final int FLOOR = 0xE8A24C;
    private static final float WRONG_RED = 0.88F;
    private static final float WRONG_GREEN = 0.25F;
    private static final float WRONG_BLUE = 0.25F;
    private static final float ALPHA = 0.35F;
    /** How solid a ghost block is, 0 to 255. */
    private static final int GHOST_ALPHA = 150;
    /** A hair inside the block, so tiles do not fight the block's own faces. */
    private static final double INSET = 0.03;
    /** A hair outside it, so a frame round a block that stands shows on its surface. */
    private static final double OUTSET = 0.01;

    private MultiblockPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        boolean hatchInHand = isHatch(player.getMainHandItem()) || isHatch(player.getOffhandItem());
        if (MultiblockPreview.all().isEmpty() && !hatchInHand) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        List<BlockPos> wrong = new ArrayList<>();
        List<BlockPos> hatchSpots = new ArrayList<>();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Iterator<Map.Entry<BlockPos, MultiblockPreview.Size>> shown = MultiblockPreview.all().entrySet().iterator();
        while (shown.hasNext()) {
            Map.Entry<BlockPos, MultiblockPreview.Size> entry = shown.next();
            BlockPos at = entry.getKey();
            if (!(level.getBlockEntity(at) instanceof MultiblockControllerBlockEntity controller) || controller.isFormed()) {
                // Built, or broken: either way the picture has done its job.
                shown.remove();
                continue;
            }
            draw(level, controller, entry.getValue(), pose, buffers, camera, cam, wrong, hatchInHand ? hatchSpots : null);
        }
        if (hatchInHand) {
            for (MultiblockControllerBlockEntity controller : MultiblockControllerBlockEntity.clientControllers()) {
                MultiblockControllerBlockEntity.Structure box = controller.structure();
                if (box == null || controller.getLevel() != level || controller.isRemoved()) {
                    continue;
                }
                collectHatchSpots(controller, box.min(), box.max(), hatchSpots);
            }
        }
        if (!hatchSpots.isEmpty()) {
            // Fetched after the ghosts and tiles: the shared buffer source ends one render type's
            // buffer when another is asked for, so a lines buffer taken first is "Not building!".
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (BlockPos spot : hatchSpots) {
                LevelRenderer.renderLineBox(pose, lines,
                        spot.getX() - OUTSET, spot.getY() - OUTSET, spot.getZ() - OUTSET,
                        spot.getX() + 1 + OUTSET, spot.getY() + 1 + OUTSET, spot.getZ() + 1 + OUTSET,
                        0.35F, 0.92F, 0.40F, 0.9F);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.debugFilledBox());
        buffers.endBatch(RenderType.lines());
        buffers.endBatch();

        if (!wrong.isEmpty()) {
            // What is in the way, over everything: the depth test off by hand round the batch,
            // because this stage runs with it on and the shard alone changes nothing.
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            VertexConsumer fill = buffers.getBuffer(ModRenderTypes.FILLED_SEE_THROUGH);
            for (BlockPos pos : wrong) {
                LevelRenderer.addChainedFilledBoxVertices(pose, fill,
                        pos.getX() + INSET, pos.getY() + INSET, pos.getZ() + INSET,
                        pos.getX() + 1 - INSET, pos.getY() + 1 - INSET, pos.getZ() + 1 - INSET,
                        WRONG_RED, WRONG_GREEN, WRONG_BLUE, ALPHA);
            }
            pose.popPose();
            RenderSystem.disableDepthTest();
            buffers.endBatch(ModRenderTypes.FILLED_SEE_THROUGH);
            RenderSystem.enableDepthTest();
        }
    }

    private static boolean isHatch(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof HatchBlock;
    }

    /** Every place in the structure a hatch may stand: the shell, the controller's own spot aside. */
    private static void collectHatchSpots(MultiblockControllerBlockEntity controller, BlockPos min, BlockPos max, List<BlockPos> into) {
        BlockPos at = controller.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!pos.equals(at) && controller.requirementAt(min, max, pos) == MultiblockControllerBlockEntity.Requirement.SHELL) {
                into.add(pos.immutable());
            }
        }
    }

    private static void draw(Level level, MultiblockControllerBlockEntity controller, MultiblockPreview.Size size,
                             PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 cam,
                             List<BlockPos> wrong, @Nullable List<BlockPos> hatchSpots) {
        BlockPos at = controller.getBlockPos();
        BlockPos[] corners = controller.bounds(size.width(), size.height(), size.depth());
        BlockPos min = corners[0];
        BlockPos max = corners[1];
        Block special = controller.specialCasing();

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.equals(at)) {
                continue;
            }
            BlockState there = level.getBlockState(pos);
            switch (controller.requirementAt(min, max, pos)) {
                case FREE -> {
                }
                case SHELL -> {
                    if (hatchSpots != null) {
                        hatchSpots.add(pos.immutable());
                    }
                    if (MultiblockControllerBlockEntity.isShell(there)) {
                        continue;
                    }
                    if (there.isAir()) {
                        ghost(pose, buffers, level, pos, ModBlocks.MACHINE_CASING.get().defaultBlockState());
                    } else {
                        wrong.add(pos.immutable());
                    }
                }
                case SPECIAL -> {
                    if (special == null || there.is(special)) {
                        continue;
                    }
                    if (there.isAir()) {
                        ghost(pose, buffers, level, pos, special.defaultBlockState());
                    } else {
                        wrong.add(pos.immutable());
                    }
                }
                case INTERIOR -> {
                    boolean floor = pos.getY() == min.getY() + 1;
                    if (!controller.interiorAccepts(pos, there, floor)) {
                        wrong.add(pos.immutable());
                    } else if (floor && there.isAir() && controller.floorTakesFluid()) {
                        tile(pose, buffers, pos, FLOOR, 0.2);
                    }
                }
            }
        }

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        AABB outline = AABB.encapsulatingFullBlocks(min, max).inflate(0.01);
        LevelRenderer.renderLineBox(pose, lines, outline, 1.0F, 1.0F, 1.0F, 0.8F);
        Vec3 labelAt = new Vec3(outline.getCenter().x, outline.maxY + 0.5, outline.getCenter().z);
        LinkOverlayRenderer.label(pose, buffers, Minecraft.getInstance().font, camera, cam, labelAt,
                controller.previewLabel(size.width(), size.height(), size.depth()),
                0xFFFFFFFF, net.minecraft.client.gui.Font.DisplayMode.NORMAL);
    }

    /** The block's own model, see-through, where it is wanted. */
    private static void ghost(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos pos, BlockState state) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        VertexConsumer ghosts = new Ghost(buffers.getBuffer(Sheets.translucentCullBlockSheet()), GHOST_ALPHA);
        pose.pushPose();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        dispatcher.getModelRenderer().renderModel(pose.last(), ghosts, state, dispatcher.getBlockModel(state),
                1.0F, 1.0F, 1.0F, LevelRenderer.getLightColor(level, pos), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    /** One translucent block, or the bottom slice of one for the floor. */
    private static void tile(PoseStack pose, MultiBufferSource buffers, BlockPos pos, int colour, double height) {
        DebugRenderer.renderFilledBox(pose, buffers,
                pos.getX() + INSET, pos.getY() + INSET, pos.getZ() + INSET,
                pos.getX() + 1 - INSET, pos.getY() + height - INSET, pos.getZ() + 1 - INSET,
                ((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F, (colour & 0xFF) / 255.0F, ALPHA);
    }

    /** Hands every vertex on as it is, but for the alpha, which the block renderer hard-codes to full. */
    private record Ghost(VertexConsumer target, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int ignored) {
            target.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            target.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            target.setNormal(x, y, z);
            return this;
        }
    }
}
