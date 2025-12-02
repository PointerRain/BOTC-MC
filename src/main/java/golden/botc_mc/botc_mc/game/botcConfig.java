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

    // Helper codec for legacy snake_case phase_durations structure
    private static final MapCodec<botcPhaseDurations> LEGACY_PHASES = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("day_discussion_secs").orElse(120).forGetter(botcPhaseDurations::dayDiscussionSecs),
            Codec.INT.fieldOf("nomination_secs").orElse(45).forGetter(botcPhaseDurations::nominationSecs),
            Codec.INT.fieldOf("execution_secs").orElse(20).forGetter(botcPhaseDurations::executionSecs),
            Codec.INT.fieldOf("night_secs").orElse(60).forGetter(botcPhaseDurations::nightSecs)
    ).apply(instance, botcPhaseDurations::new));

    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // Accept either map_id or legacy map.spawn_block.Name to choose a template id
            Identifier.CODEC.fieldOf("map_id").orElse(Identifier.of("botc-mc:test")).forGetter(botcConfig::mapId),
            // players may be a flat int or legacy object with min/max; accept flat and ignore legacy
            Codec.INT.fieldOf("players").orElse(8).forGetter(botcConfig::players),
            // Accept camelCase or legacy snake_case
            Codec.INT.fieldOf("timeLimitSecs").orElse(300).forGetter(botcConfig::timeLimitSecs),
            LEGACY_PHASES.fieldOf("phase_durations").orElse(botcPhaseDurations.defaults()).forGetter(botcConfig::phaseDurations),
            // scriptId remains as originally used by scripts system
            Codec.STRING.fieldOf("scriptId").orElse("botc-mc:scripts/trouble_brewing").forGetter(botcConfig::scriptId)
    ).apply(instance, (mapId, players, timeLimitSecs, phases, scriptId) -> new botcConfig(mapId, players, timeLimitSecs, phases, scriptId, Script.fromId(scriptId))));

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
