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
    public static final NightAction DUSK = new NightAction("dusk");
    public static final NightAction DAWN = new NightAction("dawn");
    public static final NightAction MINIONINFO = new NightAction("minioninfo");
    public static final NightAction DEMONINFO = new NightAction("demoninfo");

    public final String id;

    public NightAction(String id) {
        this.id = id;
    }

    public Text getName() {
        return Text.translatable("character.botc-mc." + this.id + ".name");
    }

    public Text getNightReminder(NightType type) {
        return Text.translatable("character.botc-mc." + this.id + "." + type.toString());
    }

    private Formatting getColour(boolean dark) {
        return switch (this.id) {
            case "minioninfo" -> Team.MINION.getColour(dark);
            case "demoninfo" -> Team.DEMON.getColour(dark);
            default -> dark ? Formatting.DARK_GRAY : Formatting.GRAY;
        };
    }

    private static class CharacterNightAction extends NightAction {
        botcCharacter character;

        public CharacterNightAction(botcCharacter character) {
            super(character.id());
            this.character = character;
        }

        @Override
        public Text getName() {
            return character.toText();
        }

        @Override
        public Text getNightReminder(NightType type) {
            return switch (type) {
                case FIRST -> character.firstNightReminderText();
                case OTHER -> character.otherNightReminderText();
            };
        }

        public Formatting getColour(boolean dark) {
            return character.team().getColour(dark);
        }
    }

    // Static factory methods for creating NightActions

    // Creates a NightAction for the first night for a given botcCharacter
    public static NightAction characterNightAction(botcCharacter botcCharacter) {
        return new CharacterNightAction(botcCharacter);
    }

    // Creates a NightAction for the first night for a given Character ID in a script, or special cases
    public static NightAction nightActionFromScript(Script script, String characterId) {
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
                .findFirst().orElse(null);
        if (botcCharacter == null) {
            return null;
        }
        return new CharacterNightAction(botcCharacter);
    }

    /**
     * Get the formatted text representation of the night action's name with appropriate colour.
     * @return The formatted text of the night action's name.
     */
    public MutableText toFormattedText(boolean dark, boolean bold, boolean withIcon, boolean withHoverText, NightType night) {
        MutableText text = (MutableText) this.getName();

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

        if (withHoverText) {
            MutableText reminderText = (MutableText) this.getNightReminder(night);
            reminderText.setStyle(Style.EMPTY.withItalic(true).withColor(Formatting.GRAY));
            text.styled(style -> style.withHoverEvent(new HoverEvent.ShowText(reminderText)));
        }
        return text;
    }

    @Override
    public String toString() {
        return "NightAction[id='" + id + "']";
    }
}
