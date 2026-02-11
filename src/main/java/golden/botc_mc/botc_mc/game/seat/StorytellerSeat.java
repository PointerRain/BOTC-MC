package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.botcCharacter;

public class StorytellerSeat extends Seat {


    @Override
    public String toString() {
        String output = "StorytellerSeat{";
        if (this.playerEntity != null) {
            output += "player=" + this.playerEntity.getName().getString() + ", ";
        } else {
            output += "player=null, ";
        }
        if (this.character != null && this.character != botcCharacter.EMPTY) {
            output += "character=" + this.character.name() + ", ";
        }
        output += "alive=" + this.alive;
        output += "}";
        return output;
    }
}
