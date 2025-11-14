package golden.botc_mc.botc_mc.game.map;

import xyz.nucleoid.map_templates.MapTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

public class botcMapGenerator {

    private final botcMapConfig config;

    public botcMapGenerator(botcMapConfig config) {
        this.config = config;
    }

    public botcMap build() {
        MapTemplate template = MapTemplate.createEmpty();
        botcMap map = new botcMap(template, this.config);

        this.buildSpawn(template);
        map.spawn = new BlockPos(0,65,0);

        return map;
    }

    private void buildSpawn(MapTemplate builder) {
        BlockPos min = new BlockPos(-5, 64, -5);
        BlockPos max = new BlockPos(5, 64, 5);

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            // botcMapConfig now exposes BlockState via spawnBlockState()
            builder.setBlockState(pos, this.config.spawnBlockState());
        }
    }
}

