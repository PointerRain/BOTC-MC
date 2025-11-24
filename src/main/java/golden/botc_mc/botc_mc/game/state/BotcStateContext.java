package golden.botc_mc.botc_mc.game.state;

import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;

/**
 * Encapsulates the environment/state used by the finite state machine actions.
 * Provides helpers to broadcast messages, access players, and hook into the GameSpace.
 */
public record BotcStateContext(GameSpace space) {
    /** Active player set wrapper.
     * @return PlayerSet for the space
     */
    public PlayerSet players() {
        return this.space.getPlayers();
    }

    /**
     * Broadcast a chat/system message to all players.
     * @param message text component
     */
    public void broadcast(net.minecraft.text.Text message) {
        this.players().sendMessage(message);
    }
}
