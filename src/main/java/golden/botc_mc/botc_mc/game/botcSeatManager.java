package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.game.exceptions.InvalidSeatException;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.Seat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Math.floorMod;

public class botcSeatManager {
    private final List<PlayerSeat> playerSeats = new ArrayList<>();
    private final List<StorytellerSeat> storytellerSeats = new ArrayList<>();
    private final List<botcCharacter> npcCharacters = new ArrayList<>();

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 18;
    public static final int MAX_STORYTELLERS = 3;

    // Constructor for default 8 player seats
    public botcSeatManager() {
        this(8); // Default to 8 player seats
    }

    /**
     * Constructor for specified number of player seats.
     * @param numPlayerSeats Number of player seats to initialise.
     */
    public botcSeatManager(int numPlayerSeats) {
        this.storytellerSeats.add(new StorytellerSeat());
        for (int i = 0; i < numPlayerSeats; i++) {
            this.playerSeats.add(new PlayerSeat());
        }
    }

    /**
     * Sets the number of player seats.
     * @param count Number of player seats to set (between 5 and 20).
     * @throws IllegalArgumentException if count is out of range.
     */
    public void setPlayerCount(int count) throws IllegalArgumentException {
        if (count < MIN_PLAYERS || count > MAX_PLAYERS) {
            throw new IllegalArgumentException("Count must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS);
        }
        // Add seats until we reach the desired count
        while (this.playerSeats.size() < count) {
            this.playerSeats.add(new PlayerSeat());
        }

        // Remove seats with no player, no character, and no reminders first
        for (int i = this.playerSeats.size() - 1; i >= 0 && this.playerSeats.size() > count; i--) {
            PlayerSeat seat = this.playerSeats.get(i);
            if (seat.getPlayerEntity() == null && seat.getCharacter() == botcCharacter.EMPTY && seat.getReminders().isEmpty()) {
                seat.clearCharacter();
                seat.removePlayerEntity();
                this.playerSeats.remove(i);
            }
        }
        // Remove seats with no player next
        for (int i = this.playerSeats.size() - 1; i >= 0 && this.playerSeats.size() > count; i--) {
            PlayerSeat seat = this.playerSeats.get(i);
            if (seat.getPlayerEntity() == null) {
                seat.clearCharacter();
                seat.removePlayerEntity();
                this.playerSeats.remove(i);
            }
        }
        // Remove the last seat if needed even if it is occupied
        while (this.playerSeats.size() > count) {
            this.playerSeats.getLast().clearCharacter();
            this.playerSeats.getLast().removePlayerEntity();
            this.playerSeats.removeLast();
        }
    }

    /**
     * Gets the current number of player seats.
     * @return Number of player seats.
     */
    public int getSeatCount() {
        return this.playerSeats.size();
    }

    /**
     * Gets a combined seat (player or storyteller) for the given player.
     * @param player The player to find the seat for.
     * @return The Seat assigned to the player, or null if none found.
     */
    public Seat getSeatFromPlayer(ServerPlayerEntity player) {
        Seat seat = getPlayerSeatFromPlayer(player);
        if (seat != null) {
            return seat;
        }
        return getStorytellerSeatFromPlayer(player);
    }

    /**
     * Gets the PlayerSeat for the given player.
     * @param player The player to find the seat for.
     * @return The PlayerSeat assigned to the player, or null if none found.
     */
    @Nullable
    public PlayerSeat getPlayerSeatFromPlayer(ServerPlayerEntity player) {
        for (PlayerSeat seat : this.playerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            }
        }
        return null;
    }

    /**
     * Gets the StorytellerSeat for the given player.
     * @param player The player to find the seat for.
     * @return The StorytellerSeat assigned to the player, or null if none found.
     */
    public StorytellerSeat getStorytellerSeatFromPlayer(ServerPlayerEntity player) {
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            }
        }
        return null;
    }

    /**
     * Gets the PlayerSeat by seat number.
     * @param seatNumber The seat number to get (1-based index).
     * @return The PlayerSeat at the given seat number.
     */
    public PlayerSeat getSeatFromNumber(int seatNumber) throws IndexOutOfBoundsException {
        return this.playerSeats.get(seatNumber - 1);
    }

    /**
     * Removes a player from any seat they are assigned to.
     * @param player The player to remove.
     * @throws InvalidSeatException If the player is not assigned to any seat.
     */
    public void removePlayerFromSeat(ServerPlayerEntity player) {
        Seat seat = getSeatFromPlayer(player);
        if (seat != null) {
            seat.removePlayerEntity();
            return;
        }
        throw new InvalidSeatException("Player is not assigned to any seat.");
    }

    /**
     * Assign a player to a seat by seat number.
     * @param player The player to assign.
     * @param seatNumber The seat number to assign the player to (1-based index).
     * @return The Seat assigned to the player.
     * @throws IllegalArgumentException If the seat number is invalid.
     * @throws InvalidSeatException If the seat is already occupied by another player.
     */
    public Seat assignPlayerToSeat(ServerPlayerEntity player, int seatNumber)
            throws IllegalArgumentException, InvalidSeatException {
        if (seatNumber < 1 || seatNumber > this.playerSeats.size()) {
            // Invalid seat number
            throw new IllegalArgumentException("Invalid seat number: " + seatNumber);
        }
        // Remove player from any other seat they may be assigned to
        try {
            removePlayerFromSeat(player);
        } catch (InvalidSeatException e) {
            // Player was not assigned to any seat, ignore
        }

        PlayerSeat seat = this.playerSeats.get(seatNumber - 1);
        // If the seat is already occupied, raise error.
        if (seat.getPlayerEntity() != null && !seat.getPlayerEntity().equals(player)) {
            throw new InvalidSeatException("Seat " + seatNumber + " is already occupied by another player.");
        }
        seat.setPlayerEntity(player);
        return seat;
    }

    /**
     * Steps a player up to a storyteller seat. If the player is already a storyteller, their seat is returned.
     * If there is already a storyteller in the game, an exception is thrown.
     * @param player The player to step up.
     * @return The StorytellerSeat assigned to the player.
     * @throws InvalidSeatException If there is already a storyteller in the game.
     */
    public Seat stepUpToStoryteller(ServerPlayerEntity player) throws InvalidSeatException {
        // If player is already a storyteller, return their seat
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            } else if (seat.getPlayerEntity() != null) {
                throw new InvalidSeatException("There is already a storyteller in this game.");
            }
        }
        return assignPlayerToStorytellerSeat(player);
    }

    /**
     * Steps a player down from their storyteller seat.
     * @param player The player to step down.
     * @throws InvalidSeatException If the player is not a storyteller.
     */
    public void stepDownFromStoryteller(ServerPlayerEntity player) {
        StorytellerSeat seat = getStorytellerSeatFromPlayer(player);
        if (seat == null) {
            throw new InvalidSeatException("Player is not a storyteller.");
        }
        seat.removePlayerEntity();
    }

    /**
     * Assigns a player to a storyteller seat.
     * If the player is already assigned to a storyteller seat, that seat is returned.
     * If there are no available storyteller seats, a new one is created.
     * @param player The player to assign.
     * @return The StorytellerSeat assigned to the player.
     */
    public Seat assignPlayerToStorytellerSeat(ServerPlayerEntity player) {
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            }
            if (seat.getPlayerEntity() == null) {
                seat.setPlayerEntity(player);
                return seat;
            }
        }
        try {
            removePlayerFromSeat(player);
        } catch (InvalidSeatException e) {
            // Player was not assigned to any seat, ignore
        }
        StorytellerSeat newSeat = new StorytellerSeat();
        newSeat.setPlayerEntity(player);
        this.storytellerSeats.add(newSeat);
        return newSeat;
    }

    /**
     * Checks if a player is assigned to a storyteller seat.
     * @param player The player to check.
     * @return True if the player is a storyteller, false otherwise.
     */
    public boolean isStoryteller(ServerPlayerEntity player) {
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the seat number (1-based index) for a given PlayerSeat.
     * @param seat The PlayerSeat to get the number for.
     * @return The seat number (1-based index).
     * @throws IllegalArgumentException If the seat is not found in player seats.
     */
    public int getSeatNumber(PlayerSeat seat) {
        int index = this.playerSeats.indexOf(seat);
        if (index == -1) {
            throw new IllegalArgumentException("Seat not found in player seats.");
        }
        return index + 1; // Convert to 1-based index
    }

    /**
     * Gets the list of storyteller seats.
     * @return List of StorytellerSeat objects.
     */
    public List<StorytellerSeat> getStorytellers() {
        return this.storytellerSeats;
    }

    /**
     * Gets the list of NPCs in the game.
     * @return List of botcCharacter objects.
     */
    public List<botcCharacter> getNPCs() {
        return this.npcCharacters;
    }

    /**
     * Adds an NPC character to the game.
     * @param character The botcCharacter to add.
     */
    public void addNPC(botcCharacter character) {
        this.npcCharacters.add(character);
    }

    /**
     * Removes an NPC character from the game.
     * @param character The botcCharacter to remove.
     * @return True if the character was removed, false otherwise.
     */
    public boolean removeNPC(botcCharacter character) {
        return this.npcCharacters.remove(character);
    }

    /**
     * Shuffles the order of player seats.
     */
    public void shuffle() {
        Collections.shuffle(this.playerSeats);
    }

    /**
     * Inserts a new player seat at position n (1-based index).
     * @param seatNumber The position to insert the new seat at (1-based index).
     * @throws IllegalArgumentException If the position is out of range or max players exceeded.
     */
    public void insert(int seatNumber) {
        if (seatNumber < 1 || seatNumber > this.playerSeats.size() + 1) {
            throw new IllegalArgumentException("Insert position must be between 1 and " + (this.playerSeats.size() + 1));
        }
        if (this.playerSeats.size() >= MAX_PLAYERS) {
            throw new IllegalArgumentException("Cannot have more than " + MAX_PLAYERS + " player seats.");
        }
        this.playerSeats.add(seatNumber - 1, new PlayerSeat());
    }

    /**
     * Removes the player seat at position n (1-based index).
     * @param seatNumber The position of the seat to remove (1-based index).
     * @throws IllegalArgumentException If the position is out of range or min players exceeded.
     */
    public void remove(int seatNumber) {
        if (seatNumber < 1 || seatNumber > this.playerSeats.size()) {
            throw new IllegalArgumentException("Remove position must be between 1 and " + this.playerSeats.size());
        }
        if (this.playerSeats.size() <= MIN_PLAYERS) {
            throw new IllegalArgumentException("Cannot have fewer than " + MIN_PLAYERS + " player seats.");
        }
        this.playerSeats.remove(seatNumber - 1);
    }

    /**
     * Moves a player seat from one position to another (1-based index).
     * @param from The current position of the seat to move (1-based index).
     * @param to The new position to move the seat to (1-based index). Wraps around if out of range.
     * @throws IllegalArgumentException If the from position is out of range.
     */
    public void moveSeat(int from, int to) {
        to = floorMod(to - 1, this.playerSeats.size()) + 1;
        if (from < 1 || from > this.playerSeats.size()) {
            throw new IllegalArgumentException("Move positions must be between 1 and " + this.playerSeats.size());
        }
        if (from == to) {
            return; // No need to move
        }
        PlayerSeat seat = this.playerSeats.remove(from - 1);
        this.playerSeats.add(to - 1, seat);
    }

    @Override
    public String toString() {
        return "botcSeatManager{" +
                "playerSeats=" + playerSeats +
                ", storytellerSeats=" + storytellerSeats +
                '}';
    }
}
