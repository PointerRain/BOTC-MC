package golden.botc_mc.botc_mc.game;

import net.minecraft.server.network.ServerPlayerEntity;

public final class botcPlayer {
    private final ServerPlayerEntity player;

    public botcPlayer(ServerPlayerEntity player) {
        this.player = player;
    }

    // added no-arg constructor to satisfy usages in botcActive; it will set player to null
    public botcPlayer() { this.player = null; }

    public ServerPlayerEntity getPlayer() {
        return this.player;
    }
}

