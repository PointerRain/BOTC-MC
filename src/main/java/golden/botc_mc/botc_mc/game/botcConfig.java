package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

/**
 * Immutable configuration snapshot for a BOTC game instance.
 * <p>
 * This record is used as the serializable game configuration that is stored inside a
 * map's game definition (datapack) and passed to the GameOpen flow. It currently
 * only contains a single field identifying the map template to load.
 * <p>
 * The {@link #MAP_CODEC} and {@link #CODEC} provide Mojang/Codec serialization support so
 * the config can be automatically read from JSON or network payloads.
 *
 * @param mapId identifier of the map/datapack to load for this game run (namespace:path)
 */
public record botcConfig(Identifier mapId) {
    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("map_id").forGetter(botcConfig::mapId)
    ).apply(instance, botcConfig::new));

    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();
}
