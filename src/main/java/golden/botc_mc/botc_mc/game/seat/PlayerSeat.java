package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.Character;
import golden.botc_mc.botc_mc.game.Team;

import java.util.ArrayList;
import java.util.List;

public class PlayerSeat extends Seat {

    List<String> reminders = new ArrayList<>();

    @Override
    public void setCharacter(Character character) throws IllegalArgumentException {
        if (character == Character.EMPTY) {
            this.character = Character.EMPTY;
        }
        if (character.team().getDefaultAlignment() == Team.Alignment.NPC) {
            throw new IllegalArgumentException("Cannot assign NPC character to player seat");
        }
        this.character = character;
        this.alignment = switch (character.team().getDefaultAlignment()) {
            case NEUTRAL, NPC -> character.team().getDefaultAlignment();
            case GOOD, EVIL -> switch (this.alignment) {
                case GOOD, EVIL -> this.alignment;
                case NEUTRAL, NPC -> character.team().getDefaultAlignment();
            };
        };
    }

    public void addReminder(String reminder) {
        this.reminders.add(reminder);
    }

    public boolean hasReminder(String reminder) {
        return this.reminders.contains(reminder);
    }

    public void removeReminder(String reminder) {
        this.reminders.remove(reminder);
    }

    public void clearReminders() {
        this.reminders.clear();
    }

    public List<String> getReminders() {
        return this.reminders;
    }

    @Override
    public String toString() {
        if (this.character == Character.EMPTY && this.playerEntity == null) return "PlayerSeat{}";

        String output = "PlayerSeat{";
        if (this.playerEntity != null) {
            output += "player=" + this.playerEntity.getName().getString() + ", ";
        } else {
            output += "player=null, ";
        }
        if (this.character != null && this.character != Character.EMPTY) {
            output += "character=" + this.character.name() + ", ";
        } else {
            output += "character=Character.EMPTY, ";
        }
        output += "alignment=" + this.alignment + ", ";
        output += "alive=" + this.alive + ", ";
        output += "reminders=" + this.reminders;
        output += "}";
        return output;
    }
}
