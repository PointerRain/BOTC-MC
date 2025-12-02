package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;






































 import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable configuration snapshot for a BOTC game instance.
 * <p>
 * This record is used as the serializable game configuration that is stored inside a
 * map's game definition (datapack) and passed to the GameOpen flow.
 * <p>
 * The {@link #MAP_CODEC} and {@link #CODEC} provide Mojang/Codec serialization support so
 * the config can be automatically read from JSON or network payloads.
 *
 * @param mapId identifier of the map/datapack to load for this game run (namespace:path)
 * @param players target player count
 * @param timeLimitSecs max time limit for session
 * @param phaseDurations phase timing configuration
 * @param scriptId script identifier
 * @param script loaded script object
 */
public record botcConfig(Identifier mapId,
                         int players,
                         int timeLimitSecs,
                         botcPhaseDurations phaseDurations,
                         String scriptId,
                         Script script) {

    public botcConfig(Identifier mapId,
                      int players,
                      int timeLimitSecs,
                      botcPhaseDurations phaseDurations,
                      String scriptId) {
        this(mapId, players, timeLimitSecs, phaseDurations, scriptId, Script.fromId(scriptId));
    }

    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("map_id").forGetter(botcConfig::mapId),
            Codec.INT.fieldOf("players").orElse(8).forGetter(botcConfig::players),
            Codec.INT.fieldOf("timeLimitSecs").orElse(300).forGetter(botcConfig::timeLimitSecs),
            botcPhaseDurations.MAP_CODEC.fieldOf("phase_durations").orElse(botcPhaseDurations.defaults()).forGetter(botcConfig::phaseDurations),
            Codec.STRING.fieldOf("scriptId").orElse("trouble_brewing").forGetter(botcConfig::scriptId)
    ).apply(instance, botcConfig::new));

    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();

    public int timeLimitSecs() { return this.timeLimitSecs; }

    // utility factory to simplify programmatic construction from helpers
    public static botcConfig of(Identifier mapId, int players, int timeLimitSecs, botcPhaseDurations phaseDurations, String scriptId, Script script) {
        return new botcConfig(mapId, players, timeLimitSecs, phaseDurations, scriptId, script);
    }

    @NotNull @Override
    public String toString() {
        return "botcConfig(mapId=" + mapId +
                ", players=" + players +
                ", timeLimitSecs=" + timeLimitSecs +
                ", phaseDurations=" + phaseDurations +
                ", scriptId=" + scriptId +
                ", script=" + script() +
                ")";
    }
}
