package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.Character;
import golden.botc_mc.botc_mc.game.Team;

import java.util.ArrayList;
import java.util.List;

class PlayerSeat extends Seat {

    List<String> reminders = new ArrayList<>();

    @Override
    void setCharacter(Character character) throws IllegalArgumentException {
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

    void addReminder(String reminder) {
        this.reminders.add(reminder);
    }

    boolean hasReminder(String reminder) {
        return this.reminders.contains(reminder);
    }

    void removeReminder(String reminder) {
        this.reminders.remove(reminder);
    }

    void clearReminders() {
        this.reminders.clear();
    }


}
