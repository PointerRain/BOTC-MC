package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.Character;

public class StorytellerSeat extends Seat {


    @Override
    public String toString() {
        String output = "StorytellerSeat{";
        if (this.playerEntity != null) {
            output += "player=" + this.playerEntity.getName().getString() + ", ";
        } else {
            output += "player=null, ";
        }
        if (this.character != null && this.character != Character.EMPTY) {
            output += "character=" + this.character.name() + ", ";
        }
        output += "alive=" + this.alive;
        output += "}";
        return output;
    }
}
