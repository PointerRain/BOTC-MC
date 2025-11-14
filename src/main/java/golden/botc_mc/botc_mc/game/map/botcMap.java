package golden.botc_mc.botc_mc.game.map;

import xyz.nucleoid.map_templates.MapTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public class botcMap {
    public BlockPos spawn;
    private final MapTemplate template;
    private final botcMapConfig config;

    public botcMap() {
        this.template = null;
        this.config = null;
    }

    public botcMap(MapTemplate template, botcMapConfig config) {
        this.template = template;
        this.config = config;
    }

    // Return a ChunkGenerator for the world; stubbed as null for now so the project compiles.
    public ChunkGenerator asGenerator(Object server) { return null; }
}

