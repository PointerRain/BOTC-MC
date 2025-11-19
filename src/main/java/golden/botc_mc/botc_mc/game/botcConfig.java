package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

public record botcConfig(Identifier mapId,
                          int players,
                          int timeLimitSecs,
                          botcPhaseDurations phaseDurations) {
    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("map_id").forGetter(botcConfig::mapId),
            Codec.INT.fieldOf("players").orElse(8).forGetter(botcConfig::players),
            Codec.INT.fieldOf("timeLimitSecs").orElse(300).forGetter(botcConfig::timeLimitSecs),
            botcPhaseDurations.MAP_CODEC.fieldOf("phase_durations").orElse(botcPhaseDurations.defaults()).forGetter(botcConfig::phaseDurations)
    ).apply(instance, botcConfig::new));

    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();

    public int timeLimitSecs() { return this.timeLimitSecs; }
}
