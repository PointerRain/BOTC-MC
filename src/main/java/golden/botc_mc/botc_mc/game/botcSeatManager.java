package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.game.exceptions.InvalidSeatException;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.Seat;
import golden.botc_mc.botc_mc.game.seat.StorytellerSeat;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class botcSeatManager {
    private final List<PlayerSeat> playerSeats = new ArrayList<>();
    private final List<StorytellerSeat> storytellerSeats = new ArrayList<>();

    public botcSeatManager() {
        this(8); // Default to 8 player seats
    }

    public botcSeatManager(int numPlayerSeats) {
        for (int i = 0; i < numPlayerSeats; i++) {
            this.playerSeats.add(new PlayerSeat());
        }
    }

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
        // Remove the last seat if needed even if occupied
        while (this.playerSeats.size() > count) {
            this.playerSeats.getLast().clearCharacter();
            this.playerSeats.getLast().removePlayerEntity();
            this.playerSeats.removeLast();
        }
    }

    public int getSeatCount() {
        return this.playerSeats.size();
    }

    public Seat getSeatFromPlayer(ServerPlayerEntity player) {
        Seat seat = getPlayerSeatFromPlayer(player);
        if (seat != null) {
            return seat;
        }
        return getStorytellerSeatFromPlayer(player);
    }

    public PlayerSeat getPlayerSeatFromPlayer(ServerPlayerEntity player) {
        for (PlayerSeat seat : this.playerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            }
        }
        return null;
    }

    public StorytellerSeat getStorytellerSeatFromPlayer(ServerPlayerEntity player) {
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            }
        }
        return null;
    }

    public void removePlayerFromSeat(ServerPlayerEntity player) {
        for (PlayerSeat seat : this.playerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                seat.removePlayerEntity();
                return;
            }
        }
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                seat.removePlayerEntity();
                return;
            }
        }
    }

    /**
     * Assign a player to a seat by seat number.
     * @param player
     * @param seatNumber
     * @return
     */
    public Seat assignPlayerToSeat(ServerPlayerEntity player, int seatNumber)
            throws IllegalArgumentException, InvalidSeatException {
        if (seatNumber > this.playerSeats.size()) {
            // Invalid seat number
            throw new IllegalArgumentException("Invalid seat number: " + seatNumber);
        }
        // Remove player from any other seat they may be assigned to
        removePlayerFromSeat(player);

        PlayerSeat seat = this.playerSeats.get(seatNumber - 1);
        // If the seat is already occupied, raise error.
        if (seat.getPlayerEntity() != null && !seat.getPlayerEntity().equals(player)) {
            throw new InvalidSeatException("Seat " + seatNumber + " is already occupied by another player.");
        }
        seat.setPlayerEntity(player);
        return seat;
    }

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

    public Seat stepDownFromStoryteller(ServerPlayerEntity player) {
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                seat.removePlayerEntity();
                return seat;
            }
        }
        throw new InvalidSeatException("Player is not a storyteller.");
    }

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
        removePlayerFromSeat(player);
        StorytellerSeat newSeat = new StorytellerSeat();
        newSeat.setPlayerEntity(player);
        this.storytellerSeats.add(newSeat);
        return newSeat;
    }

    @Override
    public String toString() {
        return "botcSeatManager{" +
                "playerSeats=" + playerSeats +
                ", storytellerSeats=" + storytellerSeats +
                '}';
    }

    public Seat getSeatFromNumber(int seatNumber) {
        return this.playerSeats.get(seatNumber - 1);
    }

    public boolean isStoryteller(ServerPlayerEntity player) {
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return true;
            }
        }
        return false;
    }

}
