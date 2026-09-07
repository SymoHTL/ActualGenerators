package dev.symo.actualgenerators.client;

import net.minecraft.util.Mth;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.MultiblockCasingBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * A formed shell block that joins up with the formed shell blocks round it, so a standing
 * structure reads as one machine rather than a heap of identical cubes.
 *
 * <p>Every face is nine flat quads: a rim two pixels wide along each edge, four corners, and the
 * middle. The middle is always the block's own {@code frame} texture, so a hatch keeps its door
 * and a casing its own face. Where a formed shell block stands past an edge, that edge's rim and
 * corners are drawn from the {@code panel} texture, the plain plate that runs on into the next
 * block; where nothing formed stands there, from the frame, the block's own rim. A block with
 * nothing formed round it is therefore drawn exactly as its frame texture. Which neighbours are
 * joined is read off the blocks round it when the chunk is built ({@link Baked#getModelData}),
 * the way connected-texture mods do it, so there is no block state to carry and no block entity
 * to ask.
 *
 * <p>{@code variants} names a handful of tiles the middle is drawn from instead of the frame, one
 * picked per block by a hash of its position (about half the blocks get the plain panel, the rest
 * one of the detail tiles), so a wall is a wall and not a grid of one motif: connected-texture
 * mods' random tiles, without the resource pack.
 *
 * <p>Three more things a model may say. {@code front} names a face that is the block's face
 * rather than a casing's: it is built the same way from the {@code front} texture, so a
 * controller keeps its front and joins the wall with everything else. {@code top} puts one
 * texture across the top and the bottom, laid by {@code top_map}: {@code nw}, {@code ne},
 * {@code se} or {@code sw} say which corner of the block the texture's top-left corner lands
 * on, {@code swap} runs its rows the other way; the texture is laid from the block's own
 * corners, so a ring on the tap's plate bends round the plate's centre from above and from
 * below alike. And on a hatch, every face wears a marker for what it lets through, read off the
 * hatch's faces: input, output, or the two split along the diagonal.
 *
 * <p>Model JSON: {@code {"loader": "actualgenerators:connected_casing", "textures": {"panel":
 * ..., "frame": ..., "particle": ..., "top": ..., "front": ..., "variant_1": ...}, "top_map":
 * "nw", "front": "north", "variants": 4}}, written by datagen.
 */
public final class ConnectedCasingModel implements IUnbakedGeometry<ConnectedCasingModel> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "connected_casing");
    public static final IGeometryLoader<ConnectedCasingModel> LOADER = ConnectedCasingModel::read;

    /** One bit per direction: a formed shell block stands there. */
    private static final ModelProperty<Integer> JOINED = new ModelProperty<>();
    /** Two bits per direction: the {@link IoMode} of a hatch's face, for its marker. */
    private static final ModelProperty<Integer> MARKERS = new ModelProperty<>();
    /** Which middle tile this block wears, from its position. */
    private static final ModelProperty<Integer> MIDDLE = new ModelProperty<>();
    private static final float RIM = 2.0F;
    private static final float[] BANDS = {0.0F, RIM, 16.0F - RIM, 16.0F};
    /** Per face, the neighbours past the texture's u-min, u-max, v-min and v-max edges. */
    private static final Direction[][] EDGES = new Direction[Direction.values().length][];
    private static final Material[] MARKER_MATERIALS = {
            null,
            marker("input"),
            marker("output"),
            marker("both"),
    };

    static {
        EDGES[Direction.DOWN.ordinal()] = new Direction[]{Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH};
        EDGES[Direction.UP.ordinal()] = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
        EDGES[Direction.NORTH.ordinal()] = new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
        EDGES[Direction.SOUTH.ordinal()] = new Direction[]{Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
        EDGES[Direction.WEST.ordinal()] = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN};
        EDGES[Direction.EAST.ordinal()] = new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN};
    }

    private static Material marker(String mode) {
        return new Material(TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "block/hatch_marker_" + mode));
    }

    /** How the top texture is laid from the block's corners. */
    enum TopMap {
        NW, NE, SE, SW, SWAP;

        /** Texture coordinates, 0 to 1, for a point of the top face at ({@code x}, {@code z}) in the block. */
        float[] map(float x, float z) {
            return switch (this) {
                case NW -> new float[]{x, z};
                case NE -> new float[]{1 - x, z};
                case SE -> new float[]{1 - x, 1 - z};
                case SW -> new float[]{x, 1 - z};
                case SWAP -> new float[]{z, x};
            };
        }
    }

    /** Of a hundred blocks, how many get the plain panel and then each detail tile in turn. */
    private static final int[] VARIANT_SHARES = {55, 17, 12, 10, 6};

    private final @Nullable TopMap topMap;
    private final @Nullable Direction front;
    private final int variants;

    private ConnectedCasingModel(@Nullable TopMap topMap, @Nullable Direction front, int variants) {
        this.topMap = topMap;
        this.front = front;
        this.variants = variants;
    }

    private static ConnectedCasingModel read(JsonObject json, JsonDeserializationContext context) {
        TopMap topMap = json.has("top_map") ? TopMap.valueOf(GsonHelper.getAsString(json, "top_map").toUpperCase(Locale.ROOT)) : null;
        Direction front = json.has("front") ? Direction.byName(GsonHelper.getAsString(json, "front")) : null;
        return new ConnectedCasingModel(topMap, front, GsonHelper.getAsInt(json, "variants", 0));
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context,
                           ModelBaker baker,
                           Function<Material, TextureAtlasSprite> sprites,
                           ModelState state,
                           ItemOverrides overrides) {
        TextureAtlasSprite frame = sprites.apply(context.getMaterial("frame"));
        TextureAtlasSprite panel = sprites.apply(context.getMaterial("panel"));
        TextureAtlasSprite top = context.hasMaterial("top") ? sprites.apply(context.getMaterial("top")) : null;
        TextureAtlasSprite frontSprite = front != null && context.hasMaterial("front") ? sprites.apply(context.getMaterial("front")) : null;
        TextureAtlasSprite[] markers = new TextureAtlasSprite[MARKER_MATERIALS.length];
        for (int i = 1; i < MARKER_MATERIALS.length; i++) {
            markers[i] = sprites.apply(MARKER_MATERIALS[i]);
        }
        // The middle tiles: the plain panel first, then the detail tiles.
        TextureAtlasSprite[] middles = new TextureAtlasSprite[variants > 0 ? variants + 1 : 0];
        if (variants > 0) {
            middles[0] = panel;
            for (int i = 1; i <= variants; i++) {
                middles[i] = sprites.apply(context.getMaterial("variant_" + i));
            }
        }
        return new Baked(frame, panel, middles, top, topMap == null ? TopMap.NW : topMap, frontSprite == null ? null : front, frontSprite,
                markers, context.useAmbientOcclusion());
    }

    static final class Baked implements IDynamicBakedModel {
        private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
        private static final ChunkRenderTypeSet SOLID_AND_CUTOUT = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout());

        private final TextureAtlasSprite frame;
        private final boolean ambientOcclusion;
        /** How many middle tiles there are to pick from; zero when the middle is the block's own. */
        private final int middles;
        /** Per middle tile, per face and per set of joined edges (four bits), the quads: baked once. */
        private final List<List<BakedQuad>> quads = new ArrayList<>(Direction.values().length * 16);
        /** Per face and per mode, the marker quad; empty where there is nothing to mark. */
        private final List<List<BakedQuad>> markerQuads = new ArrayList<>(Direction.values().length * 4);

        Baked(TextureAtlasSprite frame, TextureAtlasSprite panel, TextureAtlasSprite[] middleTiles,
              @Nullable TextureAtlasSprite top, TopMap topMap,
              @Nullable Direction front, @Nullable TextureAtlasSprite frontSprite,
              TextureAtlasSprite[] markers, boolean ambientOcclusion) {
            this.frame = frame;
            this.ambientOcclusion = ambientOcclusion;
            this.middles = middleTiles.length;
            FaceBakery bakery = new FaceBakery();
            for (int middle = 0; middle < Math.max(middles, 1); middle++) {
                for (Direction face : Direction.values()) {
                    TextureAtlasSprite own = face == front && frontSprite != null ? frontSprite : frame;
                    TextureAtlasSprite centre = middles == 0 ? own : middleTiles[middle];
                    List<BakedQuad> laid = top != null && face.getAxis() == Direction.Axis.Y ? laid(bakery, face, top, topMap) : null;
                    for (int joined = 0; joined < 16; joined++) {
                        quads.add(laid != null ? laid : bake(bakery, face, joined, panel, own, centre));
                    }
                }
            }
            for (Direction face : Direction.values()) {
                for (int mode = 0; mode < 4; mode++) {
                    markerQuads.add(markers[mode] == null ? List.of() : List.of(markerQuad(bakery, face, markers[mode])));
                }
            }
        }

        private static List<BakedQuad> bake(FaceBakery bakery, Direction face, int joined,
                                            TextureAtlasSprite panel, TextureAtlasSprite own, TextureAtlasSprite centre) {
            List<BakedQuad> list = new ArrayList<>(9);
            for (int bu = 0; bu < 3; bu++) {
                for (int bv = 0; bv < 3; bv++) {
                    boolean rimU = bu == 0 ? (joined & 1) == 0 : bu == 2 && (joined & 2) == 0;
                    boolean rimV = bv == 0 ? (joined & 4) == 0 : bv == 2 && (joined & 8) == 0;
                    TextureAtlasSprite sprite = rimU || rimV ? own : bu == 1 && bv == 1 ? centre : panel;
                    float u0 = BANDS[bu];
                    float u1 = BANDS[bu + 1];
                    float v0 = BANDS[bv];
                    float v1 = BANDS[bv + 1];
                    Vector3f[] box = box(face, u0, u1, v0, v1);
                    BlockElementFace element = new BlockElementFace(face, -1, "", new BlockFaceUV(new float[]{u0, v0, u1, v1}, 0));
                    list.add(bakery.bakeQuad(box[0], box[1], element, sprite, face, BlockModelRotation.X0_Y0, null, true));
                }
            }
            return List.copyOf(list);
        }

        /** Which middle tile a block at this position gets: the plain panel for about half, a detail tile for the rest. */
        private int middleFor(BlockPos pos) {
            if (middles <= 1) {
                return 0;
            }
            long packed = pos.asLong();
            int roll = Math.floorMod(Mth.murmurHash3Mixer((int) (packed ^ (packed >>> 32))), 100);
            int tile = 0;
            for (int share : VARIANT_SHARES) {
                roll -= share;
                if (roll < 0 || tile == middles - 1) {
                    return tile;
                }
                tile++;
            }
            return 0;
        }

        /** One quad across a top or bottom face, its texture laid from the block's corners as {@code map} says. */
        private static List<BakedQuad> laid(FaceBakery bakery, Direction face, TextureAtlasSprite top, TopMap map) {
            Vector3f[] box = box(face, 0, 16, 0, 16);
            BlockElementFace element = new BlockElementFace(face, -1, "", new BlockFaceUV(new float[]{0, 0, 16, 16}, 0));
            BakedQuad quad = bakery.bakeQuad(box[0], box[1], element, top, face, BlockModelRotation.X0_Y0, null, true);
            int[] vertices = quad.getVertices().clone();
            int stride = vertices.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                int at = vertex * stride;
                float x = Float.intBitsToFloat(vertices[at]);
                float z = Float.intBitsToFloat(vertices[at + 2]);
                float[] uv = map.map(x, z);
                vertices[at + 4] = Float.floatToRawIntBits(top.getU(uv[0]));
                vertices[at + 5] = Float.floatToRawIntBits(top.getV(uv[1]));
            }
            return List.of(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), top, quad.isShade(), quad.hasAmbientOcclusion()));
        }

        /** The marker: a see-through decal a hair outside the face, drawn on the cutout layer. */
        private static BakedQuad markerQuad(FaceBakery bakery, Direction face, TextureAtlasSprite marker) {
            Vector3f[] box = box(face, 0, 16, 0, 16);
            Vector3f out = face.step().mul(0.02F);
            box[0].add(out);
            box[1].add(out);
            BlockElementFace element = new BlockElementFace(face, -1, "", new BlockFaceUV(new float[]{0, 0, 16, 16}, 0));
            return bakery.bakeQuad(box[0], box[1], element, marker, face, BlockModelRotation.X0_Y0, null, true);
        }

        /** The flat box on this face whose default UVs are exactly this region of the texture (vanilla's uvsByFace, inverted). */
        private static Vector3f[] box(Direction face, float u0, float u1, float v0, float v1) {
            return switch (face) {
                case DOWN -> corners(u0, 0, 16 - v1, u1, 0, 16 - v0);
                case UP -> corners(u0, 16, v0, u1, 16, v1);
                case NORTH -> corners(16 - u1, 16 - v1, 0, 16 - u0, 16 - v0, 0);
                case SOUTH -> corners(u0, 16 - v1, 16, u1, 16 - v0, 16);
                case WEST -> corners(0, 16 - v1, u0, 0, 16 - v0, u1);
                case EAST -> corners(16, 16 - v1, 16 - u1, 16, 16 - v0, 16 - u0);
            };
        }

        private static Vector3f[] corners(float x0, float y0, float z0, float x1, float y1, float z1) {
            return new Vector3f[]{new Vector3f(x0, y0, z0), new Vector3f(x1, y1, z1)};
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            int joined = 0;
            for (Direction direction : Direction.values()) {
                BlockState beside = level.getBlockState(pos.relative(direction));
                if (beside.hasProperty(MultiblockCasingBlock.FORMED) && beside.getValue(MultiblockCasingBlock.FORMED)) {
                    joined |= 1 << direction.ordinal();
                }
            }
            int markers = 0;
            if (level.getBlockEntity(pos) instanceof HatchBlockEntity hatch) {
                for (Direction direction : Direction.values()) {
                    markers |= hatch.mode(direction).ordinal() << (direction.ordinal() * 2);
                }
            }
            return data.derive().with(JOINED, joined).with(MARKERS, markers).with(MIDDLE, middleFor(pos)).build();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            Integer markers = data.get(MARKERS);
            return markers == null || markers == 0 ? SOLID : SOLID_AND_CUTOUT;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData data, @Nullable RenderType type) {
            if (side == null) {
                return List.of();
            }
            if (type == RenderType.cutout()) {
                Integer markers = data.get(MARKERS);
                int mode = markers == null ? 0 : (markers >> (side.ordinal() * 2)) & 3;
                return markerQuads.get(side.ordinal() * 4 + mode);
            }
            Integer joined = data.get(JOINED);
            Integer middle = data.get(MIDDLE);
            int tile = middle == null || middles == 0 ? 0 : Math.min(middle, middles - 1);
            return quads.get((tile * Direction.values().length + side.ordinal()) * 16 + edges(side, joined == null ? 0 : joined));
        }

        /** The four edge bits of a face out of the six direction bits. */
        private static int edges(Direction face, int joined) {
            Direction[] edges = EDGES[face.ordinal()];
            int bits = 0;
            for (int edge = 0; edge < edges.length; edge++) {
                if ((joined & (1 << edges[edge].ordinal())) != 0) {
                    bits |= 1 << edge;
                }
            }
            return bits;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ambientOcclusion;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return frame;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }
    }
}
