package dev.symo.actualgenerators.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side choices: what a held Linking Tool draws. Flipped from the tool itself (crouch and
 * use it) and saved here, so they stick between sessions and never touch the server.
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** A box and the network name on every pad and injector in view. */
    public static final ModConfigSpec.BooleanValue OVERLAY_PADS;
    /** Each network's centre and a line from it to every pad. */
    public static final ModConfigSpec.BooleanValue OVERLAY_LINES;
    /** The reach sphere round each network's centre. Always hidden by the world. */
    public static final ModConfigSpec.BooleanValue OVERLAY_REACH;
    /** Pads, centres and lines drawn through walls. */
    public static final ModConfigSpec.BooleanValue OVERLAY_THROUGH_WALLS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("overlay");
        OVERLAY_PADS = BUILDER.comment("Draw a box and the network name on every pad and injector in view.")
                .define("pads", true);
        OVERLAY_LINES = BUILDER.comment("Draw each network's centre and a line from it to every pad.")
                .define("lines", true);
        OVERLAY_REACH = BUILDER.comment("Draw the reach sphere round each network's centre. The world always hides it.")
                .define("reach", true);
        OVERLAY_THROUGH_WALLS = BUILDER.comment("Draw pads, centres and lines through walls.")
                .define("throughWalls", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private ClientConfig() {
    }

    /** The value, or on before the config has loaded (the overlay defaults to showing everything). */
    public static boolean get(ModConfigSpec.BooleanValue value) {
        return !SPEC.isLoaded() || value.get();
    }

    public static void set(ModConfigSpec.BooleanValue value, boolean on) {
        if (SPEC.isLoaded()) {
            value.set(on);
            SPEC.save();
        }
    }
}
