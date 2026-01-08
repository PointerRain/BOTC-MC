package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.game.exceptions.InvalidSeatException;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.Seat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class botcSeatManager {
    private final List<PlayerSeat> playerSeats = new ArrayList<>();
    private final List<StorytellerSeat> storytellerSeats = new ArrayList<>();

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
        if (count < 5 || count > 20) {
            throw new IllegalArgumentException("Count must be between 5 and 20.");
        }
        // Add seats until we reach the desired count
        while (this.playerSeats.size() < count) {
            this.playerSeats.add(new PlayerSeat());
        }
        // Remove empty seats from the end
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

    @Override
    public String toString() {
        return "botcSeatManager{" +
                "playerSeats=" + playerSeats +
                ", storytellerSeats=" + storytellerSeats +
                '}';
    }

    public int getSeatNumber(PlayerSeat seat) {
        int index = this.playerSeats.indexOf(seat);
        if (index == -1) {
            throw new IllegalArgumentException("Seat not found in player seats.");
        }
        return index + 1; // Convert to 1-based index
    }

    public List<StorytellerSeat> getStorytellers() {
        return this.storytellerSeats;
    }
}
