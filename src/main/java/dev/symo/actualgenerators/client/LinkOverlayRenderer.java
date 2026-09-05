package dev.symo.actualgenerators.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.config.ClientConfig;
import dev.symo.actualgenerators.item.LinkingToolItem;
import dev.symo.actualgenerators.logistics.EnergyInjectorBlockEntity;
import dev.symo.actualgenerators.logistics.LinkPortBlock;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.PortFace;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.DoubleFunction;

/**
 * What a held linking tool shows: a box round every pad and injector in view with the name of its
 * network and the pad's label; the centre of each network and a line from it to each pad; and, for
 * every distinct range among the pads, the sphere a pad has to stand inside, drawn as a globe.
 * Each of the three is a switch in the client config, flipped by crouching with the tool
 * ({@link LinkOverlayOptionsScreen}), and a fourth says whether pads, centres and lines show
 * through walls. The globe never does: a sphere drawn over everything from the inside is a
 * screenful of lines, so the world hides it, and inside a base it is out of the way.
 *
 * <p>One thing is drawn tool or no tool: a block the network overview was asked to find
 * ({@link #highlight}) flashes red through walls until its time is up.
 *
 * <p>Everything drawn comes off the block entities the client already has loaded, which register
 * themselves as they load, so this never scans a chunk.
 *
 * <p>Through walls takes more than a see-through render type. NeoForge fires the translucent
 * stage from inside the section-layer pass, before that layer has cleared its state, so the depth
 * test is still on when this runs; and {@code RenderStateShard.NO_DEPTH_TEST} is a shard that
 * does nothing in setup (only the other depth shards touch the GL state). The depth test has to
 * be switched off here, by hand, round the see-through batches, and switched back on after. The
 * depth-tested batches end before that, and their text goes through a buffer source of its own,
 * because the shared one is ended with the test off.
 */
@EventBusSubscriber(modid = ActualGenerators.MODID, value = Dist.CLIENT)
public final class LinkOverlayRenderer {
    /** How far out pads are drawn. Past this a label is unreadable anyway. */
    private static final double DRAW_DISTANCE = 64;
    private static final int UNLINKED = 0xFF9A9A9A;
    private static final int PAD_LABEL = 0xFFF0E68C;
    private static final int SEGMENTS = 64;
    /** How long a block found from the overview flashes, and how fast. */
    private static final long HIGHLIGHT_MILLIS = 7_500;
    private static final long FLASH_MILLIS = 250;
    /**
     * What the world may hide, on buffer sources of their own. The shared source cannot carry
     * either: it ends with the depth test off, and its one shared buffer holds a single line type
     * at a time, so asking it for the see-through lines ended the solid ones mid-sphere
     * ("Not building!" on the first frame).
     */
    private static final MultiBufferSource.BufferSource SOLID_LINES = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
    private static final MultiBufferSource.BufferSource SOLID_TEXT = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    /** Blocks the overview asked to be found, each with the moment it stops flashing. */
    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

    private LinkOverlayRenderer() {
    }

    private record Highlight(GlobalPos at, long until) {
    }

    /** Flashes the block red through walls for a while, so a pad or injector picked off the overview can be found. */
    public static void highlight(GlobalPos at) {
        HIGHLIGHTS.removeIf(highlight -> highlight.at.equals(at));
        HIGHLIGHTS.add(new Highlight(at, Util.getMillis() + HIGHLIGHT_MILLIS));
    }

    /** Whether the block is still being flashed. */
    public static boolean isHighlighted(GlobalPos at) {
        long now = Util.getMillis();
        for (Highlight highlight : HIGHLIGHTS) {
            if (highlight.until > now && highlight.at.equals(at)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a flash is in its lit phase right now, so a screen's marker blinks in step with the block. */
    public static boolean flashOn() {
        return (Util.getMillis() / FLASH_MILLIS) % 2 == 0;
    }

    /** A network as seen from here: its name, its colour, its centre, and where its pads in view are. */
    private static final class Seen {
        final String name;
        final int colour;
        final Vec3 center;
        final boolean centerIsHere;
        final List<Vec3> pads = new ArrayList<>();
        /** Every distinct reach among the pads seen: the radius of the sphere each has to stand inside. */
        final Set<Integer> ranges = new TreeSet<>();

        Seen(PortFace face, Level level) {
            name = face.networkName();
            colour = Chrome.networkColour(face.networkColour());
            center = face.networkCenter();
            centerIsHere = level.dimension().equals(face.networkHome());
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        long now = Util.getMillis();
        HIGHLIGHTS.removeIf(highlight -> highlight.until <= now);
        boolean tool = holdingTool(player);
        boolean showPads = tool && ClientConfig.get(ClientConfig.OVERLAY_PADS);
        boolean showLines = tool && ClientConfig.get(ClientConfig.OVERLAY_LINES);
        boolean showReach = tool && ClientConfig.get(ClientConfig.OVERLAY_REACH);
        boolean throughWalls = ClientConfig.get(ClientConfig.OVERLAY_THROUGH_WALLS);
        boolean overlay = showPads || showLines || showReach;
        if (!overlay && HIGHLIGHTS.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer solid = SOLID_LINES.getBuffer(RenderType.lines());
        VertexConsumer lines = throughWalls ? buffers.getBuffer(ModRenderTypes.LINES_SEE_THROUGH) : solid;
        MultiBufferSource.BufferSource text = throughWalls ? buffers : SOLID_TEXT;
        Font.DisplayMode mode = throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        Map<UUID, Seen> networks = new HashMap<>();
        List<Runnable> labels = new ArrayList<>();
        List<Runnable> reachLabels = new ArrayList<>();
        Font font = minecraft.font;

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (overlay) {
            drawOverlay(level, cam, camera, pose, solid, lines, text, mode, font, networks, labels, reachLabels,
                    showPads, showLines, showReach);
        }
        // The overview's finds: a red box, through walls, on for a quarter second and off for the next.
        if (!HIGHLIGHTS.isEmpty() && flashOn()) {
            VertexConsumer through = buffers.getBuffer(ModRenderTypes.LINES_SEE_THROUGH);
            for (Highlight highlight : HIGHLIGHTS) {
                if (highlight.at.dimension().equals(level.dimension())) {
                    LevelRenderer.renderLineBox(pose, through, new AABB(highlight.at.pos()).inflate(0.03), 1.0F, 0.2F, 0.2F, 1.0F);
                }
            }
        }
        pose.popPose();

        // Hidden by the world: the reach always, the rest when the player asked for that.
        reachLabels.forEach(Runnable::run);
        if (!throughWalls) {
            labels.forEach(Runnable::run);
        }
        SOLID_LINES.endBatch();
        SOLID_TEXT.endBatch();
        // Through walls: the highlights always, the overlay's lines and names when asked.
        RenderSystem.disableDepthTest();
        buffers.endBatch(ModRenderTypes.LINES_SEE_THROUGH);
        if (throughWalls) {
            labels.forEach(Runnable::run);
            buffers.endBatch();
        }
        RenderSystem.enableDepthTest();
    }

    /** The held tool's overlay: pads and injectors with names and labels, centres with lines, reach globes. */
    private static void drawOverlay(Level level, Vec3 cam, Camera camera, PoseStack pose, VertexConsumer solid,
                                    VertexConsumer lines, MultiBufferSource text, Font.DisplayMode mode, Font font,
                                    Map<UUID, Seen> networks, List<Runnable> labels, List<Runnable> reachLabels,
                                    boolean showPads, boolean showLines, boolean showReach) {
        for (LinkPortBlockEntity port : LinkPortBlockEntity.clientPorts()) {
            if (port.getLevel() != level || !port.getBlockPos().closerToCenterThan(cam, DRAW_DISTANCE)) {
                continue;
            }
            for (PortFace face : port.faces()) {
                AABB plate = LinkPortBlock.shape(face.direction()).bounds().move(port.getBlockPos()).inflate(0.01);
                UUID id = face.networkId();
                int colour = id == null ? UNLINKED : Chrome.networkColour(face.networkColour());
                Vec3 at = plate.getCenter();
                // Names and labels sit just off the plate, on the side a player looks at it from.
                Vec3 off = Vec3.atLowerCornerOf(face.direction().getNormal()).scale(0.3);
                if (showPads) {
                    LevelRenderer.renderLineBox(pose, lines, plate, red(colour), green(colour), blue(colour), 1.0F);
                    String padLabel = face.label();
                    if (!padLabel.isEmpty()) {
                        Vec3 labelAt = at.subtract(off).add(0, id == null ? 0 : -0.25, 0);
                        labels.add(() -> label(pose, text, font, camera, cam, labelAt, Component.literal(padLabel), PAD_LABEL, mode));
                    }
                }
                if (id == null) {
                    continue;
                }
                Seen seen = networks.computeIfAbsent(id, key -> new Seen(face, level));
                seen.pads.add(at);
                if (!face.isUnbound()) {
                    seen.ranges.add(face.range());
                }
                if (showPads) {
                    Vec3 labelAt = at.subtract(off);
                    labels.add(() -> label(pose, text, font, camera, cam, labelAt, Component.literal(seen.name), colour, mode));
                }
            }
        }
        // Injectors are drawn and named, but they are not pads: they take no part in the centre.
        if (showPads) {
            for (EnergyInjectorBlockEntity injector : EnergyInjectorBlockEntity.clientInjectors()) {
                if (injector.getLevel() != level || !injector.getBlockPos().closerToCenterThan(cam, DRAW_DISTANCE)) {
                    continue;
                }
                boolean linked = injector.isLinked();
                int colour = linked ? Chrome.networkColour(injector.networkColour()) : UNLINKED;
                AABB box = new AABB(injector.getBlockPos()).inflate(0.01);
                LevelRenderer.renderLineBox(pose, lines, box, red(colour), green(colour), blue(colour), 1.0F);
                if (linked) {
                    Vec3 labelAt = box.getCenter().add(0, 0.8, 0);
                    labels.add(() -> label(pose, text, font, camera, cam, labelAt,
                            Component.literal(injector.networkName()), colour, mode));
                }
            }
        }
        for (Seen seen : networks.values()) {
            if (!seen.centerIsHere) {
                continue;
            }
            int colour = seen.colour;
            Vec3 center = seen.center;
            if (showLines) {
                LevelRenderer.renderLineBox(pose, lines, AABB.ofSize(center, 0.3, 0.3, 0.3),
                        red(colour), green(colour), blue(colour), 1.0F);
                for (Vec3 pad : seen.pads) {
                    line(pose, lines, center, pad, colour);
                }
                Vec3 labelAt = center.add(0, 0.4, 0);
                labels.add(() -> label(pose, text, font, camera, cam, labelAt,
                        Component.translatable("gui.actualgenerators.link.overlay.center", seen.name), colour, mode));
            }
            if (showReach) {
                for (int range : seen.ranges) {
                    sphere(pose, solid, center, range, colour);
                    Vec3 reachAt = center.add(0, range, 0);
                    reachLabels.add(() -> label(pose, SOLID_TEXT, font, camera, cam, reachAt,
                            Component.translatable("gui.actualgenerators.link.overlay.reach", seen.name, range), colour,
                            Font.DisplayMode.NORMAL));
                }
            }
        }
    }

    private static boolean holdingTool(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof LinkingToolItem
                || player.getOffhandItem().getItem() instanceof LinkingToolItem;
    }

    private static void line(PoseStack pose, VertexConsumer buffer, Vec3 from, Vec3 to, int colour) {
        PoseStack.Pose last = pose.last();
        Vec3 along = to.subtract(from).normalize();
        buffer.addVertex(last, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red(colour), green(colour), blue(colour), 1.0F)
                .setNormal(last, (float) along.x, (float) along.y, (float) along.z);
        buffer.addVertex(last, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red(colour), green(colour), blue(colour), 1.0F)
                .setNormal(last, (float) along.x, (float) along.y, (float) along.z);
    }

    /**
     * The reach sphere as a globe: meridians every thirty degrees and parallels at nought, thirty
     * and sixty, so it reads as a sphere from inside as well as from outside.
     */
    private static void sphere(PoseStack pose, VertexConsumer buffer, Vec3 center, double radius, int colour) {
        for (int step = 0; step < 6; step++) {
            double yaw = step * Math.PI / 6;
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);
            ring(pose, buffer, colour, angle -> center.add(
                    Math.cos(angle) * radius * cos, Math.sin(angle) * radius, Math.cos(angle) * radius * sin));
        }
        for (int step = -2; step <= 2; step++) {
            double pitch = step * Math.PI / 6;
            double around = Math.cos(pitch) * radius;
            double up = Math.sin(pitch) * radius;
            ring(pose, buffer, colour, angle -> center.add(Math.cos(angle) * around, up, Math.sin(angle) * around));
        }
    }

    /** One closed ring of the globe, from a function of the angle round it. */
    private static void ring(PoseStack pose, VertexConsumer buffer, int colour, DoubleFunction<Vec3> point) {
        Vec3 previous = point.apply(0);
        for (int step = 1; step <= SEGMENTS; step++) {
            Vec3 next = point.apply(step * (Math.PI * 2 / SEGMENTS));
            line(pose, buffer, previous, next, colour);
            previous = next;
        }
    }

    /** A name tag's way of putting text in the world: faces the camera; through walls or not as asked. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Font font, Camera camera, Vec3 cam,
                              Vec3 at, Component text, int colour, Font.DisplayMode mode) {
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(camera.rotation());
        pose.scale(0.02F, -0.02F, 0.02F);
        Matrix4f matrix = pose.last().pose();
        float x = -font.width(text) / 2.0F;
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(text, x, 0, colour, false, matrix, buffers, mode, background, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static float red(int colour) {
        return ((colour >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int colour) {
        return ((colour >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int colour) {
        return (colour & 0xFF) / 255.0F;
    }
}
