package golden.botc_mc.botc_mc.game;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Objects;

/**
 * Represents a botcCharacter in the BOTC game, with various attributes and behaviours.
 */
public record botcCharacter(String id,
                            String name,
                            Team team,
                            String ability,
                            Object image,
                            String edition,
                            String flavor,
                            int firstNight,
                            String firstNightReminder,
                            int otherNight,
                            String otherNightReminder,
                            List<String> reminders,
                            List<String> remindersGlobal,
                            boolean setup,
                            List<Script.Jinx> jinxes,
                            String token) {

    /**
     * Constructs a botcCharacter by looking it up from the baseCharacters array using the given id.
     * This should not be directly used if loading from a script, as it will not fill in missing fields from the base character.
     * Use the script object's characters instead.
      * @param id The id of the character to construct.
      * @throws IllegalArgumentException if the id is null.
     */
    public botcCharacter(String id) {
        this(CharacterLoader.findCharacterById(id));
    }

    /**
     * Copy constructor to create a new botcCharacter from an existing one.
     */
    private botcCharacter(botcCharacter botcCharacter) {
        this(botcCharacter.id,
                botcCharacter.name,
                botcCharacter.team,
                botcCharacter.ability,
                botcCharacter.image,
                botcCharacter.edition,
                botcCharacter.flavor,
                botcCharacter.firstNight,
                botcCharacter.firstNightReminder,
                botcCharacter.otherNight,
                botcCharacter.otherNightReminder,
                botcCharacter.reminders,
                botcCharacter.remindersGlobal,
                botcCharacter.setup,
                botcCharacter.jinxes,
                botcCharacter.token);
    }

    public static botcCharacter EMPTY = new botcCharacter(
            "empty",
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            0,
            null,
            null,
            null,
            false,
            null,
            null
    );

    /**
     * Checks if the character is classified as an NPC based on its team's default alignment.
     * NPC characters cannot be assigned to players.
     * @return True if the character is an NPC, false otherwise.
     */
    public boolean isNPC() {
        return this.team.isNPC();
    }

    /**
     * Convert the character's name to a MutableText object.
     * @return The character's name as MutableText.
     */
    public MutableText toText() {
        return Text.translatable(Objects.requireNonNullElseGet(this.name, () -> "character.botc." + this.id + ".name"));
    }

    public Text abilityText() {
        return Text.translatable(Objects.requireNonNullElseGet(this.ability, () -> "character.botc." + this.id + ".ability"));
    }

    public Text flavorText() {
        return Text.translatable(Objects.requireNonNullElseGet(this.flavor, () -> "character.botc." + this.id + ".flavor"));
    }

    public Text firstNightReminderText() {
        return Text.translatable(Objects.requireNonNullElseGet(this.firstNightReminder, () -> "character.botc." + this.id + ".first"));
    }

    public Text otherNightReminderText() {
        return Text.translatable(Objects.requireNonNullElseGet(this.otherNightReminder, () -> "character.botc." + this.id + ".other"));
    }

    /**
     * Convert the character's name to a formatted Text object based on team colour.
     * @param dark             Whether to use dark mode colours.
     * @param bold             Whether to use bold formatting.
     * @param withIcon         Whether to include the character's icon.
     * @param withHoverAbility Whether to include hover text of the character's ability.
     * @return The character's name as formatted Text.
     */
    public Text toFormattedText(boolean dark, boolean bold, boolean withIcon, boolean withHoverAbility) {
        MutableText text = this.toText();
        if (bold) {
            text = text.formatted(Formatting.BOLD);
        }
        if (withIcon) {
            char iconChar = IconCharHelper.getIconChar(this);
            if (iconChar != ' ') {
                MutableText iconText = (MutableText) Text.of(String.valueOf(iconChar));
                iconText.styled(style -> style.withBold(false));
                text = Text.empty().append(iconText).append("\u00a0").append(text);
            }
        }
        if (this.team != null) {
            text = text.formatted(this.team.getColour(dark));
        }
        if (withHoverAbility) {
            MutableText abilityText = Text.empty();
            abilityText.append(this.toFormattedText(false, true, true, false));
            abilityText.append("\n");
            abilityText.append(this.abilityText().copy().setStyle(Style.EMPTY.withItalic(true).withColor(Formatting.GRAY)));
            text.styled(style -> style.withHoverEvent(new HoverEvent.ShowText(abilityText)));
        }
        return text;
    }

    public List<ReminderToken> reminderTokens() {
        if (this.reminders == null) {
            return List.of();
        }
        List<ReminderToken> reminderTokens = new java.util.ArrayList<>();
        for (String reminder : this.reminders) {
            reminderTokens.add(new ReminderToken(this, reminder, false));
        }
        return reminderTokens;
    }

    public List<ReminderToken> globalReminderTokens() {
        if (this.remindersGlobal == null) {
            return List.of();
        }
        List<ReminderToken> reminderTokens = new java.util.ArrayList<>();
        for (String reminder : this.remindersGlobal) {
            reminderTokens.add(new ReminderToken(this, reminder, true));
        }
        return reminderTokens;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        botcCharacter other = (botcCharacter) obj;
        return this.id.equals(other.id);
    }

    public record ReminderToken(botcCharacter character, String reminder, boolean global) {
        public static final ReminderToken CUSTOM = new ReminderToken(botcCharacter.EMPTY, "Custom", false);

        public Text toText() {
            return Text.translatable(this.reminder().replace('\n', ' '));
        }
    }
}