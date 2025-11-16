package golden.botc_mc.botc_mc;

import net.fabricmc.api.ModInitializer;
import xyz.nucleoid.plasmid.api.game.GameType;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import golden.botc_mc.botc_mc.game.botcConfig;
import golden.botc_mc.botc_mc.game.botcWaiting;
import golden.botc_mc.botc_mc.game.botcCommands;

public class botc implements ModInitializer {

    public static final String ID = "botc-mc";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    // We will use the built-in ScreenHandlerType.GENERIC_9X1 when creating handlers to avoid
    // registry/mapping mismatches in the dev environment.

    public static final GameType<botcConfig> TYPE = GameType.<botcConfig>register(
            Identifier.of(ID, "botc-mc"),
            botcConfig.MAP_CODEC,
            botcWaiting::open
    );

    @Override
    public void onInitialize() {
        botcCommands.register();
    }
}
