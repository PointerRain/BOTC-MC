package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import golden.botc_mc.botc_mc.game.map.botcMapConfig;

public record botcConfig(botcMapConfig mapConfig, int players, int timeLimitSecs) {
    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            botcMapConfig.CODEC.fieldOf("map").forGetter(botcConfig::mapConfig),
            Codec.INT.fieldOf("players").orElse(8).forGetter(botcConfig::players),
            Codec.INT.fieldOf("timeLimitSecs").orElse(300).forGetter(botcConfig::timeLimitSecs)
    ).apply(instance, botcConfig::new));

    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();

    public int timeLimitSecs() { return this.timeLimitSecs; }
}

