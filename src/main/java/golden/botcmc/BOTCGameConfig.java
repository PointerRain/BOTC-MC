package golden.botcmc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class BOTCGameConfig {
    public static final MapCodec<BOTCGameConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        return instance.group(
                Codec.STRING.fieldOf("greeting").forGetter(BOTCGameConfig::greeting)
        ).apply(instance, BOTCGameConfig::new);
    });

    private final String greeting;

    public BOTCGameConfig(String greeting) {
		this.greeting = greeting;
	}

    public String greeting() {
        return this.greeting;
    }
}
