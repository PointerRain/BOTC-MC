package golden.botc_mc.botc_mc.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class MapConfig {
    private static final BlockPos DEFAULT_FALLBACK = new BlockPos(0, 65, 0);

    public static final Codec<MapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("map_id").forGetter(MapConfig::mapId),
            BlockPos.CODEC.optionalFieldOf("fallback_spawn", DEFAULT_FALLBACK).forGetter(MapConfig::fallbackSpawn)
    ).apply(instance, MapConfig::new));

    private final Identifier mapId;
    private final BlockPos fallbackSpawn;

    public MapConfig(Identifier mapId, BlockPos fallbackSpawn) {
        this.mapId = mapId;
        this.fallbackSpawn = fallbackSpawn;
        if (mapId == null) {
            throw new IllegalArgumentException("mapId cannot be null");
        }
        if (fallbackSpawn == null) {
            fallbackSpawn = DEFAULT_FALLBACK;
        }
    }

    public Identifier mapId() {
        return mapId;
    }

    public BlockPos fallbackSpawn() {
        return fallbackSpawn;
    }
}
