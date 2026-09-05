package dev.symo.actualgenerators.client;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;

/**
 * The few things common code asks the client to do. Common code calls in here only behind an
 * {@code isClientSide} check, so this class is never loaded on a dedicated server.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    /** The held tool's overlay switches. */
    public static void openOverlayOptions() {
        Minecraft.getInstance().setScreen(new LinkOverlayOptionsScreen());
    }

    /** The config card's question on a pad: this one, or this one and its neighbours. */
    public static void openPadApply(BlockPos pos, Direction face) {
        Minecraft.getInstance().setScreen(new PadApplyScreen(pos, face));
    }

    /** Exported text lands on the player's clipboard. */
    public static void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
