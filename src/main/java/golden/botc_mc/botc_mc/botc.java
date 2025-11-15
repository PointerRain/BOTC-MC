package golden.botc_mc.botc_mc;

import golden.botc_mc.botc_mc.game.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.api.game.GameType;

import java.util.ArrayList;

public class botc implements ModInitializer {

    public static final String ID = "botc-mc";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    public static final ArrayList<botcActive> ACTIVE_GAMES = new ArrayList<>();

    public static final GameType<botcConfig> TYPE = GameType.<botcConfig>register(
            Identifier.of(ID, "botc-mc"),
            botcConfig.MAP_CODEC,
            botcWaiting::open
    );

    /**
     * Add the active game to the list of active games
     * TODO: There is probably a preexisting way to manage active games in Plasmid
     */
    public static void addGame(botcActive game) {
        LOGGER.info("Adding the game! " + game);
        ACTIVE_GAMES.add(game);
    }
    /**
     * Remove the active game from the list of active games
     * TODO: There is probably a preexisting way to manage active games in Plasmid
     */
    public static void removeGame(botcActive game) {
        LOGGER.info("Removing the game! " + game);
        ACTIVE_GAMES.remove(game);
    }

    /**
     * Get the botcPlayer object for the given ServerPlayerEntity
     * @param player The player to get the botcPlayer object for
     * @return The botcPlayer object, or null if the player is not in an active game
     */
    public static botcPlayer getPlayer(ServerPlayerEntity player) {
        botcActive activeGame = getActiveGameByPlayer(player);
        if (activeGame != null) {
            return activeGame.getPlayer(player);
        }
        return null;
    }

    /**
     * Get the active game that the given player is in
     * @param player The player to get the active game for
     * @return The active game, or null if the player is not in an active game
     */
    public static botcActive getActiveGameByPlayer(ServerPlayerEntity player) {
        for (botcActive activeGame : ACTIVE_GAMES) {
            LOGGER.info("Getting active game! " + activeGame);
            if (activeGame.getPlayer(player) != null) {
                LOGGER.info("Found active game! " + activeGame);
                return activeGame;
            }
        }
        return null;
    }

    @Override
    public void onInitialize() {
        botcCommands.register();
    }
}
