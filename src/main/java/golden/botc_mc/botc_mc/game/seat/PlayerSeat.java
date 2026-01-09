package golden.botc_mc.botc_mc.game.seat;

import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.exceptions.InvalidAlignmentException;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class PlayerSeat extends Seat {

    Team.Alignment alignment = Team.Alignment.NEUTRAL;

    final List<botcCharacter.ReminderToken> reminders = new ArrayList<>();

    @Override
    public void setCharacter(botcCharacter character) throws IllegalArgumentException {
        super.setCharacter(character);
        if (character.team() == null) {
            this.alignment = Team.Alignment.NEUTRAL;
            return;
        }
        this.alignment = switch (character.team().getDefaultAlignment()) {
            case NEUTRAL, NPC -> character.team().getDefaultAlignment();
            case GOOD, EVIL -> switch (this.alignment) {
                case GOOD, EVIL -> this.alignment;
                case NEUTRAL, NPC -> character.team().getDefaultAlignment();
            };
        };
    }

    public Team.Alignment getAlignment() {
        return this.alignment;
    }

    /**
     * Toggles the alignment of the seat. For Traveller botcCharacters, cycles through Neutral -> Good -> Evil.
     * For Townsfolk, Outsider, Minion, and Demon botcCharacters, toggles between Good and Evil.
     * Fabled and Loric botcCharacters remain NPC.
     * NOTE: This means toggling twice may not return to the original alignment.
     * @return The new alignment after toggling.
     */
    public Team.Alignment toggleAlignment() {
        if (this.character == null || this.character == botcCharacter.EMPTY) {
            return this.alignment;
        }
        this.alignment = switch (this.character.team()) {
            case FABLED, LORIC -> Team.Alignment.NPC;
            case TRAVELLER -> switch (this.alignment) {
                case NEUTRAL -> Team.Alignment.GOOD;
                case GOOD -> Team.Alignment.EVIL;
                case EVIL -> Team.Alignment.NEUTRAL;
                default -> this.alignment;
            };
            case TOWNSFOLK, OUTSIDER, MINION, DEMON -> switch (this.alignment) {
                case GOOD -> Team.Alignment.EVIL;
                case EVIL -> Team.Alignment.GOOD;
                default -> this.alignment;
            };
        };
        return this.alignment;
    }

    /**
     * Sets the alignment of the seat. Validates against botcCharacter team restrictions.
     * @param alignment The desired alignment to set.
     * @return The new alignment after setting.
     * @throws InvalidAlignmentException If the alignment is invalid for the botcCharacter's team.
     */
    public Team.Alignment setAlignment(Team.Alignment alignment) throws InvalidAlignmentException {
        if (this.character == null || this.character == botcCharacter.EMPTY) {
            return this.alignment;
        }
        if (alignment == Team.Alignment.NPC) {
            throw new InvalidAlignmentException("Cannot set player seat alignment to NPC");
        }
        if (this.character.team() != Team.TRAVELLER && alignment == Team.Alignment.NEUTRAL) {
            throw new InvalidAlignmentException("Cannot set player seat alignment to NEUTRAL for non-Traveller botcCharacters");
        }
        this.alignment = alignment;
        return this.alignment;
    }

    /**
     * Clears the botcCharacter and alignment assigned to this seat, setting it to botcCharacter.EMPTY.
     */
    public void clearCharacter() {
        super.clearCharacter();
        this.alignment = Team.Alignment.NEUTRAL;
    }

    public void addReminderToken(String reminder) {
        this.reminders.add(new botcCharacter.ReminderToken(botcCharacter.EMPTY, reminder, false));
    }

    public void addReminderToken(botcCharacter.ReminderToken token) {
        this.reminders.add(token);
    }

//    public boolean hasReminder(String reminder) {
//        return this.reminders.contains(reminder);
//    }

//    public void removeReminder(String reminder) {
//        this.reminders.remove(reminder);
//    }

    public botcCharacter.ReminderToken removeReminder(int index) {
        if (index >= 0 && index < this.reminders.size()) {
            return this.reminders.remove(index);
        }
        return null;
    }

    public void clearReminders() {
        this.reminders.clear();
    }

    public List<botcCharacter.ReminderToken> getReminders() {
        return this.reminders;
    }

    @Override
    protected Formatting getColour(boolean dark) {
        // Return white if no character assigned
        if (character == botcCharacter.EMPTY) {
            return Formatting.WHITE;
        }
        // Return team colour if alignment matches default alignment
        if (this.character.team() != null && alignment == this.character.team().getDefaultAlignment()) {
            return this.character.team().getColour(dark);
        }
        // Otherwise, return alignment colour
        return this.alignment.getColour(dark);
    }

    @Override
    public String toString() {
        if (this.character == botcCharacter.EMPTY && this.playerEntity == null) return "PlayerSeat{}";

        String output = "PlayerSeat{";
        if (this.playerEntity != null) {
            output += "player=" + this.playerEntity.getName().getString() + ", ";
        } else {
            output += "player=null, ";
        }
        if (this.character != null && this.character != botcCharacter.EMPTY) {
            output += "character=" + this.character.name() + ", ";
        } else {
            output += "character=botcCharacter.EMPTY, ";
        }
        output += "alignment=" + this.alignment + ", ";
        output += "alive=" + this.alive + ", ";
        output += "reminders=" + this.reminders;
        output += "}";
        return output;
    }
}
