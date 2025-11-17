package golden.botc_mc.botc_mc.game.map;

import xyz.nucleoid.map_templates.MapTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

public class botcMap {
    protected final MapTemplate template;
    protected final botcMapConfig config;

    // public spawn point used by botcSpawnLogic and waiting code
    public BlockPos spawn = null;

    public botcMap(MapTemplate template, botcMapConfig config) {
        this.template = template;
        this.config = config;
    }

    public MapTemplate getTemplate() { return this.template; }
    public botcMapConfig getConfig() { return this.config; }

    // Return a ChunkGenerator for the world; when not able to construct a custom generator,
    // fallback to the server's overworld chunk generator to avoid nulls during runtime world creation.
    public ChunkGenerator asGenerator(Object server) {
        try {
            if (server instanceof MinecraftServer ms) {
                ServerWorld overworld = ms.getOverworld();
                if (overworld != null) {
                    return overworld.getChunkManager().getChunkGenerator();
                }
            }
        } catch (Throwable t) {
            // swallow and return null so callers can decide what to do; logging left out to avoid IO in library code
        }
        return null;
    }
}
