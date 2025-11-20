package golden.botc_mc.botc_mc.game;

import net.minecraft.server.network.ServerPlayerEntity;

public record botcPlayer(ServerPlayerEntity player) {

    // added no-arg constructor to satisfy usages in botcActive; it will set player to null
    public botcPlayer() {
        this(null);
    }
}

