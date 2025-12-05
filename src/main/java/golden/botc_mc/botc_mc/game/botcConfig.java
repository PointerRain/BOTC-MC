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

    /**
     * Codec for modern camelCase phase duration JSON structure.
     */
    private static final MapCodec<botcPhaseDurations> PHASES = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("dayDiscussionSecs").orElse(120).forGetter(botcPhaseDurations::dayDiscussionSecs),
            Codec.INT.fieldOf("nominationSecs").orElse(45).forGetter(botcPhaseDurations::nominationSecs),
            Codec.INT.fieldOf("executionSecs").orElse(20).forGetter(botcPhaseDurations::executionSecs),
            Codec.INT.fieldOf("nightSecs").orElse(60).forGetter(botcPhaseDurations::nightSecs)
    ).apply(instance, botcPhaseDurations::new));

    /**
     * Map codec used for reading game configs from datapacks. This codec expects modern camelCase keys.
     */
    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // map id (modern camelCase)
            Identifier.CODEC.fieldOf("mapId").orElse(Identifier.of("botc-mc:test")).forGetter(botcConfig::mapId),
            // players (flat int)
            Codec.INT.fieldOf("players").orElse(8).forGetter(botcConfig::players),
            // overall time limit in seconds
            Codec.INT.fieldOf("timeLimitSecs").orElse(300).forGetter(botcConfig::timeLimitSecs),
            // modern camelCase phase durations
            PHASES.fieldOf("phaseDurations").orElse(botcPhaseDurations.defaults()).forGetter(botcConfig::phaseDurations),
            // scriptId remains as originally used by scripts system
            Codec.STRING.fieldOf("scriptId").orElse("botc-mc:scripts/trouble_brewing").forGetter(botcConfig::scriptId)
    ).apply(instance, (mapId, players, timeLimitSecs, phases, scriptId) -> new botcConfig(mapId, players, timeLimitSecs, phases, scriptId, Script.fromId(scriptId))));

    /**
     * Standard codec wrapper for network/registry integration.
     */
    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();

    /**
     * Accessor with explicit Javadoc to satisfy documentation checks.
     *
     * @return configured overall time limit in seconds
     */
    public int timeLimitSecs() { return this.timeLimitSecs; }

    /**
     * Utility factory to simplify programmatic construction from helpers.
     *
     * @param mapId identifier of the map to use
     * @param players player count target
     * @param timeLimitSecs overall session time limit in seconds
     * @param phaseDurations per-phase durations
     * @param scriptId script identifier string
     * @param script resolved Script instance
     * @return a new botcConfig instance populated with the provided values
     */
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
