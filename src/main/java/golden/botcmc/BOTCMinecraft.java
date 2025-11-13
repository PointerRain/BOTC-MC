package golden.botcmc;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;

public class BOTCMinecraft implements ModInitializer {

    public static final GameType<BOTCGameConfig> BOTCGAME_TYPE = GameType.register(
        Identifier.of("botc", "botc_example"),
        BOTCGameConfig.CODEC,
        BOTCGame::open
    );

    @Override
    public void onInitialize() {

    }
}
