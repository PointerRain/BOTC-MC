package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class StorytellerSeat extends Seat {

    @Override
    protected Formatting getColour(boolean dark) {
        return Team.Alignment.NEUTRAL.getColour(dark);
    }

    public Text getCharacterText() {
        MutableText text = (MutableText) (character != null ? character.toFormattedText(false) : Text.of("Storyteller"));
        text.styled(style -> style.withFormatting(getColour(false)).withBold(true).withItalic(false));
        return text;
    }

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
