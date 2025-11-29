package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import golden.botc_mc.botc_mc.game.map.botcMapConfig;
import org.jetbrains.annotations.NotNull;

public record botcConfig(botcMapConfig mapConfig,
                         int players,
                         int timeLimitSecs,
                         botcPhaseDurations phaseDurations,
                         String scriptId,
                         Script script) {

    public botcConfig(botcMapConfig mapConfig,
                      int players,
                      int timeLimitSecs,
                      botcPhaseDurations phaseDurations,
                      String scriptId) {
        this(mapConfig, players, timeLimitSecs, phaseDurations, scriptId, Script.fromId(scriptId));
    }

    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            botcMapConfig.CODEC.fieldOf("map").forGetter(botcConfig::mapConfig),
            Codec.INT.fieldOf("players").orElse(8).forGetter(botcConfig::players),
            Codec.INT.fieldOf("timeLimitSecs").orElse(300).forGetter(botcConfig::timeLimitSecs),
            botcPhaseDurations.MAP_CODEC.fieldOf("phase_durations").orElse(botcPhaseDurations.defaults()).forGetter(botcConfig::phaseDurations),
            Codec.STRING.fieldOf("scriptId").forGetter(botcConfig::scriptId)
    ).apply(instance, botcConfig::new));

    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();

    public int timeLimitSecs() { return this.timeLimitSecs; }

    // utility factory to simplify programmatic construction from helpers
    public static botcConfig of(botcMapConfig mapConfig, int players, int timeLimitSecs, botcPhaseDurations phaseDurations, String scriptId, Script script) {
        return new botcConfig(mapConfig, players, timeLimitSecs, phaseDurations, scriptId, script);
    }

    @NotNull @Override
    public String toString() {
        return "botcConfig(map=" + mapConfig +
                ", players=" + players +
                ", timeLimitSecs=" + timeLimitSecs +
                ", phaseDurations=" + phaseDurations +
                ", scriptId=" + scriptId +
                ", script=" + script() +
                ")";
    }
}
