package golden.botc_mc.botc_mc.game;

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
        while (this.playerSeats.size() < count) {
            this.playerSeats.add(new PlayerSeat());
        }
        while (this.playerSeats.size() > count) {
            // Remove the last seat
            this.playerSeats.getLast().clearCharacter();
            this.playerSeats.getLast().removePlayerEntity();
            this.playerSeats.removeLast();
        }
    }

    public int getSeatCount() {
        return this.playerSeats.size();
    }

    public Seat getSeatFromPlayer(ServerPlayerEntity player) {
        for (PlayerSeat seat : this.playerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            }
        }
        for (StorytellerSeat seat : this.storytellerSeats) {
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player)) {
                return seat;
            }
        }
        return null;
    }

    /**
     * Assign a player to a seat by seat number.
     * @param player
     * @param seatNumber
     * @return
     */
    public Seat assignPlayerToSeat(ServerPlayerEntity player, int seatNumber) {
        if (seatNumber >= this.playerSeats.size()) {
            // Invalid seat number
            throw new IllegalArgumentException("Invalid seat number: " + seatNumber);
        }
        // Remove player from any other seat they may be assigned to
        for (int n = 0; n < this.playerSeats.size(); n++) {
            PlayerSeat seat = this.playerSeats.get(n);
            if (seat.getPlayerEntity() != null && seat.getPlayerEntity().equals(player) && n != seatNumber) {
                seat.removePlayerEntity();
                return seat;
            }
        }
        PlayerSeat seat = this.playerSeats.get(seatNumber);
        // If the seat is already occupied, raise error.
        if (seat.getPlayerEntity() != null && !seat.getPlayerEntity().equals(player)) {
            throw new IllegalArgumentException("Seat " + seatNumber + " is already occupied by another player.");
        }
        seat.setPlayerEntity(player);
        return seat;
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
}
