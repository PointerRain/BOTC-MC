package golden.botc_mc.botc_mc.game;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class botcPlayer {
    private final ServerPlayerEntity player;

    private botcPlayerType playerType;
    private boolean isDead = false;

    public botcPlayer(ServerPlayerEntity player) {
        this.player = player;

        // Add player to the "botc_players" team on the server scoreboard.
        MinecraftServer server = this.player.getServer();
        assert server != null;
        ServerScoreboard serverScoreboard = server.getScoreboard();
        // Create the team if it doesn't exist
        Team team = serverScoreboard.getTeam("botc_players");
        if (team == null) {
            team = serverScoreboard.addTeam("botc_players");
        }
        serverScoreboard.addScoreHolderToTeam(this.player.getGameProfile().getName(), team);

        this.playerType = botcPlayerType.PLAYER;

    }

    // added no-arg constructor to satisfy usages in botcActive; it will set player to null
    public botcPlayer() { this.player = null; }

    public ServerPlayerEntity getPlayer() {
        return this.player;
    }

    public botcPlayerType getPlayerType() {
        return this.playerType;
    }

    /**
     * Set the player's type
     * TODO: Spectator type should be handled by Plasmid's built-in spectator mode
     * @param playerType One of: STORYTELLER, PLAYER, SPECTATOR
     */
    public void setPlayerType(botcPlayerType playerType) {
        this.playerType = playerType;
    }

    /**
     * Check if the player is dead
     * @return true if the player is dead, false if they are alive
     */
    public boolean isDead() {
        return this.isDead;
    }

    /**
     * Set the player's dead/living status
     * @param isDead true if the player is dead, false if they are alive
     */
    public void setDead(boolean isDead) {
        this.isDead = isDead;
    }

    /**
     * Revive the player if they are dead
     * @return true if the player was revived, false if they were not dead
     */
    public boolean revive() {
        if (!this.isDead) return false;
        this.isDead = false;
        if (this.player != null) {
            this.player.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
        return true;
    }

    /**
     * Kill the player if they are alive
     * @return true if the player was killed, false if they were already dead
     */
    public boolean kill() {
        if (this.isDead) return false;
        this.isDead = true;
        if (this.player != null) {
            this.player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 1, false, false, false));
        }
        return true;
    }
}