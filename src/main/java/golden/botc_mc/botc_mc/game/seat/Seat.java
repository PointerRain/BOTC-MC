package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.Character;
import golden.botc_mc.botc_mc.game.Team;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class Seat {

    // Character. Storyteller seats usually have Character.EMPTY, but can be assigned other characters if desired.
    Character character = Character.EMPTY;
    // PlayerEntity associated with this seat
    ServerPlayerEntity playerEntity = null;
    // Alive status. Not particularly meaningful for storyteller seats.
    boolean alive = true;

    /**
     * Sets the player entity associated with this seat.
     * @param playerEntity The ServerPlayerEntity to associate with this seat.
     */
    public void setPlayerEntity(ServerPlayerEntity playerEntity) {
        this.playerEntity = playerEntity;
        // TODO: Add to the "botc_players" team on the server scoreboard.
    }

    /**
     * Gets the player entity associated with this seat.
     * @return The ServerPlayerEntity associated with this seat, or null if none is set.
     */
    public ServerPlayerEntity getPlayerEntity() {
        return this.playerEntity;
    }

    /**
     * Removes the player entity association from this seat.
     * Does not affect the character or other seat properties.
     */
    public void removePlayerEntity() {
        this.playerEntity = null;
        // TODO: Remove any effects from the player if needed.
    }

    /**
     * Checks if this seat has an associated player entity.
     * @return True if a player entity is associated, false otherwise.
     */
    public boolean hasPlayerEntity() {
        return this.playerEntity != null;
    }

    /**
     * Sets the character assigned to this seat.
     * If the seat's alignment is NEUTRAL or NPC, it will be updated to the character's team's default alignment.
     * If the character is NPC or NEUTRAL, the seat's alignment will be set to that.
     * Otherwise, the seat's alignment remains unchanged.
     * @param character The Character to assign to this seat.
     */
    public void setCharacter(Character character) {
        if (character == Character.EMPTY) {
            this.character = Character.EMPTY;
        }
        if (character.team().getDefaultAlignment() == Team.Alignment.NPC) {
            throw new IllegalArgumentException("Cannot assign NPC character to seat");
        }
        this.character = character;
    }



    /**
     * Clears the character and alignment assigned to this seat, setting it to Character.EMPTY.
     */
    public void clearCharacter() {
        this.character = Character.EMPTY;
    }

    /**
     * Gets the character assigned to this seat.
     * @return The Character assigned to this seat.
     */
    public Character getCharacter() {
        return this.character;
    }

    /**
     * Sets the alive status of the player in this seat.
     * @param alive True if the player is alive, false if dead.
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /**
     * Checks if the player in this seat is alive.
     * @return True if the player is alive, false otherwise.
     */
    public boolean isAlive() {
        return this.alive;
    }

    /**
     * Kills the player in this seat if they are currently alive.
     * @return True if the player was alive and is now killed, false if they were already dead.
     */
    public boolean kill() {
        if (this.alive) {
            this.alive = false;
            return true;
        }
        return false;
    }

    /**
     * Revives the player in this seat if they are currently dead.
     * @return True if the player was dead and is now revived, false if they were already alive.
     */
    public boolean revive() {
        if (!this.alive) {
            this.alive = true;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Seat{" +
                "character=" + character +
                ", playerEntity=" + (playerEntity != null ? playerEntity.getName().getString() : "null") +
                ", alive=" + alive +
                '}';
    }
}
