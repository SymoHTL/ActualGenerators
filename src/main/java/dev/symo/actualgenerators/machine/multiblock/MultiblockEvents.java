package dev.symo.actualgenerators.machine.multiblock;

import dev.symo.actualgenerators.ActualGenerators;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The two world events a controller cannot hear from its own block.
 *
 * <p>A structure may straddle a chunk border. While the far chunk is unloaded nothing in it can
 * change, and a controller that cannot see all of its shell keeps the state it had; when that
 * chunk comes back, this asks every controller whose box reaches into it to look again. That is
 * the whole of it — no chunk scan, no tick.
 */
@EventBusSubscriber(modid = ActualGenerators.MODID)
public final class MultiblockEvents {

    private MultiblockEvents() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MultiblockControllerBlockEntity.chunkLoaded(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        MultiblockControllerBlockEntity.levelUnloaded(event.getLevel());
    }
}
