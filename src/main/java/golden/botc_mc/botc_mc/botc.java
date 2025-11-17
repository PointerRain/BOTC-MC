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

    // Statically register the GameType so it is available to client UI early
    public static final GameType<botcConfig> TYPE = GameType.register(
            Identifier.of(ID, "game"),
            botcConfig.MAP_CODEC,
            botcWaiting::open
    );

    @Override
    public void onInitialize() {
        if (TYPE != null) {
            LOGGER.info("GameType is present during onInitialize: {}", Identifier.of(ID, "game"));
        } else {
            LOGGER.error("GameType is NULL during onInitialize for id {}", Identifier.of(ID, "game"));
        }

        // Register commands and other runtime initialization
        botcCommands.register();
    }
}
