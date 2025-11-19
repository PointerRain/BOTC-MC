package golden.botc_mc.botc_mc.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record botcMapConfig(Identifier mapId, BlockPos fallbackSpawn) {
    private static final BlockPos DEFAULT_FALLBACK = new BlockPos(0, 65, 0);

    public static final Codec<botcMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("map_id").forGetter(botcMapConfig::mapId),
            BlockPos.CODEC.optionalFieldOf("fallback_spawn", DEFAULT_FALLBACK).forGetter(botcMapConfig::fallbackSpawn)
    ).apply(instance, botcMapConfig::new));

    public botcMapConfig {
        if (mapId == null) {
            throw new IllegalArgumentException("mapId cannot be null");
        }
        if (fallbackSpawn == null) {
            fallbackSpawn = DEFAULT_FALLBACK;
        }
    }
}
