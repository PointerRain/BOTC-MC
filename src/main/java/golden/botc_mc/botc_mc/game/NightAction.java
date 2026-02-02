package golden.botc_mc.botc_mc.game;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Represents a night action in the game.
 * Contains information about the action's ID, name, reminder text, and colour provider.
 */
public class NightAction {
    public static final NightAction DUSK = new NightAction("dusk", "Dusk", "Start the Night Phase.");
    public static final NightAction DAWN = new NightAction("dawn", "Dawn", "The night ends.");
    public static final NightAction MINIONINFO = new NightAction("minioninfo",
            "Minion Info",
            "If there are 7 or more players, wake all Minions: Show the THIS IS THE DEMON token. Point to the " +
                    "Demon. Show the THESE ARE YOUR MINIONS token. Point to the other Minions.");
    public static final NightAction DEMONINFO = new NightAction("demoninfo",
            "Demon Info",
            "If there are 7 or more players, wake the Demon: Show the THESE ARE YOUR MINIONS token. Point to all " +
                    "Minions. Show the THESE CHARACTERS ARE NOT IN PLAY token. Show 3 not-in-play good botcCharacter " +
                    "tokens.");

    final String id;
    final String name;
    final String reminder;

    public NightAction(String id, String name, String reminder) {
        this.id = id;
        this.name = name;
        this.reminder = reminder;
    }

    private static class CharacterNightAction extends NightAction {
        botcCharacter character;

        public CharacterNightAction(botcCharacter character, String reminder) {
            super(character.id(), character.name(), reminder);
            this.character = character;
        }

        public Formatting getColour(boolean dark) {
            return character.team().getColour(dark);
        }
    }

    // Static factory methods for creating NightActions

    // Creates a NightAction for the first night for a given botcCharacter
    public static NightAction firstNightAction(botcCharacter botcCharacter) {
        return new CharacterNightAction(botcCharacter, botcCharacter.firstNightReminder());
    }

    // Creates a NightAction for the first night for a given Character ID in a script, or special cases
    public static NightAction firstNightAction(Script script, String characterId) {
        switch (characterId) {
            case "dusk":
                return DUSK;
            case "dawn":
                return DAWN;
            case "minioninfo":
                return MINIONINFO;
            case "demoninfo":
                return DEMONINFO;
        }
        botcCharacter botcCharacter = script.characters().stream()
                .filter(c -> c.id().equals(characterId))
                .findFirst().orElseThrow();
        return new CharacterNightAction(botcCharacter, botcCharacter.firstNightReminder());
    }

    // Creates a NightAction for nights other than the first for a given botcCharacter
    public static NightAction otherNightAction(botcCharacter botcCharacter) {
        return new CharacterNightAction(botcCharacter, botcCharacter.otherNightReminder());
    }

    // Creates a NightAction for nights other than the first for a given botcCharacter ID in a script, or special cases
    public static NightAction otherNightAction(Script script, String characterId) {
        switch (characterId) {
            case "dusk":
                return DUSK;
            case "dawn":
                return DAWN;
        }
        botcCharacter botcCharacter = script.characters().stream()
                .filter(c -> c.id().equals(characterId))
                .findFirst().orElseThrow();
        return new CharacterNightAction(botcCharacter, botcCharacter.otherNightReminder());
    }

    private Formatting getColour(boolean dark) {
        return switch (this.id) {
            case "minioninfo" -> Team.MINION.getColour(dark);
            case "demoninfo" -> Team.DEMON.getColour(dark);
            default -> dark ? Formatting.DARK_GRAY : Formatting.GRAY;
        };
    }

    /**
     * Get the formatted text representation of the night action's name with appropriate colour.
     * @return The formatted text of the night action's name.
     */
    public MutableText toFormattedText(boolean dark, boolean bold, boolean withIcon, boolean withHoverText) {
        MutableText text = (MutableText) Text.of(name);

        if (bold) {
            text = text.formatted(Formatting.BOLD);
        }
        if (withIcon) {
            char iconChar = switch (this.id) {
                case "minioninfo" -> IconCharHelper.getIconChar("danger");
                case "demoninfo" -> IconCharHelper.getIconChar("skull");
                default -> this instanceof CharacterNightAction cna ? IconCharHelper.getIconChar(cna.character) : ' ';
            };
            if (iconChar != ' ') {
                MutableText iconText = (MutableText) Text.of(String.valueOf(iconChar));
                text = Text.empty().append(iconText).append(" ").append(text);
            }
        }
        text = text.formatted(this instanceof CharacterNightAction cna ? cna.getColour(dark) : getColour(dark));
        if (withHoverText && this.reminder != null && !this.reminder.isEmpty()) {
            MutableText reminderText = Text.literal(this.reminder).setStyle(Style.EMPTY.withItalic(true).withColor(Formatting.GRAY));
            text.styled(style -> style.withHoverEvent(new HoverEvent.ShowText(reminderText)));
        }
        return text;
    }

    @Override
    public String toString() {
        return "NightAction[id='" + id + "', name='" + name + "', reminder='" + reminder + "']";
    }
}
