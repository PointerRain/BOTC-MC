package golden.botc_mc.botc_mc.game;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Lightweight wrapper around a Minecraft player for BOTC-specific metadata expansion.
 * @param player the server-side player entity participating in the game
 */
public record botcPlayer(ServerPlayerEntity player) {
    /** Default no-arg record constructor retained for potential serialization frameworks. */
    public botcPlayer() { this(null); }
}
