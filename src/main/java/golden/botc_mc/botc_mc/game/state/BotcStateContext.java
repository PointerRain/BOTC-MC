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

    public BotcStateContext(GameSpace space) {
        this.space = space;
    }

    public GameSpace space() {
        return this.space;
    }

    public PlayerSet players() {
        return this.space.getPlayers();
    }

    public void broadcast(net.minecraft.text.Text message) {
        this.players().sendMessage(message);
    }

    public Iterable<ServerPlayerEntity> iterablePlayers() {
        return this.players();
    }
}

