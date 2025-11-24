package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

/**
 * Immutable configuration snapshot for a game instance.
 * @param mapId identifier of the map/datapack to load for this game run
 */
public record botcConfig(Identifier mapId) {
    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("map_id").forGetter(botcConfig::mapId)
    ).apply(instance, botcConfig::new));

    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();
}
