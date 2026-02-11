package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.game.seat.Seat;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A lightweight wrapper around a Minecraft player for BOTC-specific metadata expansion.
 * @param player the server-side player entity participating in the game
 */
public record botcPlayer(ServerPlayerEntity player, Seat seat) {

    /** Default no-arg record constructor retained for potential serialization frameworks. */
    public botcPlayer() { this(null, null); }

    /**
     * Gets the Seat assigned to this botcPlayer.
     * @return the Seat assigned to this botcPlayer
     */
    public Seat getSeat() {
        return seat;
    }

    /**
     * Creates a new botcPlayer with the specified Seat.
     * @param seat the Seat to assign to the new botcPlayer
     * @return a new botcPlayer instance with the specified Seat
     */
    public botcPlayer setSeat(Seat seat) {
        return new botcPlayer(this.player, seat);
    }
}