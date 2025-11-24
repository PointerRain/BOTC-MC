package golden.botc_mc.botc_mc.game.state;

import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;

/**
 * Encapsulates the environment/state used by the finite state machine actions.
 * Provides helpers to broadcast messages, access players, and hook into the GameSpace.
 */
public final class BotcStateContext {
    private final GameSpace space;
    private GameLifecycleStatus lifecycleStatus = GameLifecycleStatus.STOPPED;

    /**
     * Construct a context bound to a game space.
     * @param space game space instance
     */
    public BotcStateContext(GameSpace space) {
        this.space = space;
    }

    /** Current lifecycle status value.
     * @return current lifecycle status
     */
    public GameLifecycleStatus getLifecycleStatus() {
        return this.lifecycleStatus;
    }

    /** Set lifecycle status.
     * @param status new lifecycle status
     */
    public void setLifecycleStatus(GameLifecycleStatus status) {
        this.lifecycleStatus = status;
    }

    /** Underlying game space reference.
     * @return bound GameSpace
     */
    public GameSpace space() {
        return this.space;
    }

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

    /** Iterable view of connected players.
     * @return iterable of online players
     */
    public Iterable<ServerPlayerEntity> iterablePlayers() {
        return this.players();
    }
}
