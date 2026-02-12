package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.botcCharacter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public abstract class Seat {

    // character. Storyteller seats usually have botcCharacter.EMPTY, but can be assigned other character if desired.
    botcCharacter character = botcCharacter.EMPTY;
    // PlayerEntity associated with this seat
    ServerPlayerEntity playerEntity = null;
    // Alive status. Not particularly meaningful for storyteller seats.
    boolean alive = true;

    private static final String SCOREBOARD_TEAM = "botc-mc:game";

    /**
     * Sets the player entity associated with this seat.
     * @param playerEntity The ServerPlayerEntity to associate with this seat.
     */
    public void setPlayerEntity(ServerPlayerEntity playerEntity) {
        this.playerEntity = playerEntity;

        // Add player to the "botc-mc:game" team on the server scoreboard.
        MinecraftServer server = playerEntity.getServer();
        assert server != null;
        ServerScoreboard serverScoreboard = server.getScoreboard();
        // Create the team if it doesn't exist
        Team team = serverScoreboard.getTeam(SCOREBOARD_TEAM);
        if (team == null) {
            team = serverScoreboard.addTeam(SCOREBOARD_TEAM);
        }
        serverScoreboard.addScoreHolderToTeam(this.playerEntity.getGameProfile().getName(), team);

        // If the seat is not alive, make the player invisible
        if (!this.isAlive()) {
            this.playerEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, -1));
        }
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
        if (!hasPlayerEntity()) {
            return;
        }
        // Remove the player from the "botc-mc:game" team
        MinecraftServer server = this.playerEntity.getServer();
        assert server != null;
        ServerScoreboard serverScoreboard = server.getScoreboard();
        Team team = serverScoreboard.getTeam(SCOREBOARD_TEAM);
        if (team != null) {
            serverScoreboard.removeScoreHolderFromTeam(this.playerEntity.getGameProfile().getName(), team);
        }
        // Clear their invisibility effect if they were invisible
        this.playerEntity.removeStatusEffect(StatusEffects.INVISIBILITY);

        this.playerEntity = null;
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
     * @throws IllegalArgumentException if the character is an NPC character (non-NPC characters and EMPTY are allowed).
     */
    public void setCharacter(botcCharacter character) throws IllegalArgumentException {
        if (character == botcCharacter.EMPTY) {
            this.character = botcCharacter.EMPTY;
            return;
        }
        if (character.isNPC()) {
            throw new IllegalArgumentException("Cannot assign NPC character to seat");
        }
        this.character = character;
    }

    /**
     * Clears the character and alignment assigned to this seat, setting it to Character.EMPTY.
     */
    public void clearCharacter() {
        this.character = botcCharacter.EMPTY;
    }

    /**
     * Gets the character assigned to this seat.
     * @return The Character assigned to this seat.
     */
    public botcCharacter getCharacter() {
        return this.character;
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
        if (!this.alive) {
            return false;
        }

        this.alive = false;
        if (this.hasPlayerEntity()) {
            this.playerEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, -1));
        }
        return true;
    }

    /**
     * Revives the player in this seat if they are currently dead.
     * @return True if the player was dead and is now revived, false if they were already alive.
     */
    public boolean revive() {
        if (this.alive) {
            return false;
        }

        this.alive = true;
        if (this.hasPlayerEntity()) {
            this.playerEntity.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
        return true;
    }


    public Formatting getColour(boolean dark) {
        if (character != botcCharacter.EMPTY && character.team() != null) {
            return character.team().getColour(dark);
        }
        return Formatting.WHITE;
    }

    public Text getOccupantText() {
        MutableText text = (MutableText) (playerEntity != null ? playerEntity.getDisplayName() : Text.of("(Unoccupied)"));
        if (text == null) {
            // This should never happen, but just in case
            text = (MutableText) Text.of("(Occupied)");
        }
        text.styled(style -> style.withFormatting(getColour(false)).withItalic(false));
        return text;
    }

    public Text getCharacterText() {
        MutableText text = (MutableText) (character != null ? character.toFormattedText(false, false, true, false) : Text.of("Empty"));
        text.styled(style -> style.withFormatting(getColour(false)).withItalic(false));
        return text;
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
