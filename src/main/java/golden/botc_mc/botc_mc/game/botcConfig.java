package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

/**
 * Lightweight config decoded from Plasmid game JSON. It only selects which map to use.
 * Timing, phase durations, and player counts are provided by independent settings
 * (botcSettings and/or a dedicated game-settings config), not by this structure.
 */
public record botcConfig(Identifier mapId) {
    public static final MapCodec<botcConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("map_id").forGetter(botcConfig::mapId)
    ).apply(instance, botcConfig::new));

    public static final Codec<botcConfig> CODEC = MAP_CODEC.codec();
}
