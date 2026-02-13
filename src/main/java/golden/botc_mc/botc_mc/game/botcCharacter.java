package golden.botc_mc.botc_mc.game;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.resource.Resource;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Represents a botcCharacter in the BOTC game, with various attributes and behaviors.
 */
public record botcCharacter(String id,
                            String name,
                            Team team,
                            String ability,
                            String image,
                            String edition,
                            String flavor,
                            int firstNight,
                            String firstNightReminder,
                            int otherNight,
                            String otherNightReminder,
                            List<String> reminders,
                            List<String> remindersGlobal,
                            boolean setup,
                            List<Script.Jinx> jinxes) {

    public static final Codec<botcCharacter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(botcCharacter::id),
            Codec.STRING.fieldOf("name").forGetter(botcCharacter::name),
            Team.CODEC.fieldOf("team").forGetter(botcCharacter::team),
            Codec.STRING.fieldOf("ability").forGetter(botcCharacter::ability),
            Codec.STRING.fieldOf("image").forGetter(botcCharacter::image),
            Codec.STRING.fieldOf("edition").forGetter(botcCharacter::edition),
            Codec.STRING.fieldOf("flavor").forGetter(botcCharacter::flavor),
            Codec.INT.fieldOf("firstNight").forGetter(botcCharacter::firstNight),
            Codec.STRING.fieldOf("firstNightReminder").forGetter(botcCharacter::firstNightReminder),
            Codec.INT.fieldOf("otherNight").forGetter(botcCharacter::otherNight),
            Codec.STRING.fieldOf("otherNightReminder").forGetter(botcCharacter::otherNightReminder),
            Codec.STRING.listOf().fieldOf("reminders").forGetter(botcCharacter::reminders),
            Codec.STRING.listOf().fieldOf("remindersGlobal").forGetter(botcCharacter::remindersGlobal),
            Codec.BOOL.fieldOf("setup").forGetter(botcCharacter::setup),
            Script.Jinx.CODEC.listOf().fieldOf("jinxes").forGetter(botcCharacter::jinxes)
    ).apply(instance, botcCharacter::new));

    /**
     * The base botcCharacters loaded from the JSON resource.
     */
    public static botcCharacter[] baseCharacters;

    /**
     * Constructs a botcCharacter by looking it up from the baseCharacters array using the given id.
     */
    public botcCharacter(String id) {
        this(findCharacterById(id));
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
                botcCharacter.jinxes);
    }

    public static botcCharacter EMPTY = new botcCharacter(
            "empty",
            "Empty",
            null,
            "",
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
            null
    );

    /**
     * Constructs a Character from a partial Character, filling in missing fields from the baseCharacters array.
     * @param botcCharacter The partial Character object.
     * @return A (hopefully) complete Character object.
     */
    public static botcCharacter fromPartialCharacter(botcCharacter botcCharacter) {
        if (botcCharacter.id() == null) {
            throw new IllegalArgumentException("character id cannot be null");
        }
        if (baseCharacters == null) {
            botc.LOGGER.warn("Base characters not loaded yet, returning partial character as is.");
            return botcCharacter;
        }
        botcCharacter baseCharacter = findCharacterById(botcCharacter.id());
        return new botcCharacter(
                botcCharacter.id(),
                botcCharacter.name() != null ? botcCharacter.name() : baseCharacter.name(),
                botcCharacter.team() != null ? botcCharacter.team() : baseCharacter.team(),
                botcCharacter.ability() != null ? botcCharacter.ability() : baseCharacter.ability(),
                botcCharacter.image() != null ? botcCharacter.image() : baseCharacter.image(),
                botcCharacter.edition() != null ? botcCharacter.edition() : baseCharacter.edition(),
                botcCharacter.flavor() != null ? botcCharacter.flavor() : baseCharacter.flavor(),
                botcCharacter.firstNight() != 0 ? botcCharacter.firstNight() : baseCharacter.firstNight(),
                botcCharacter.firstNightReminder() != null ? botcCharacter.firstNightReminder() :
                        baseCharacter.firstNightReminder(),
                botcCharacter.otherNight() != 0 ? botcCharacter.otherNight() : baseCharacter.otherNight(),
                botcCharacter.otherNightReminder() != null ? botcCharacter.otherNightReminder() :
                        baseCharacter.otherNightReminder(),
                botcCharacter.reminders() != null ? botcCharacter.reminders() : baseCharacter.reminders(),
                botcCharacter.remindersGlobal() != null ? botcCharacter.remindersGlobal() : baseCharacter.remindersGlobal(),
                botcCharacter.setup() || baseCharacter.setup(),
                botcCharacter.jinxes != null ? botcCharacter.jinxes() : baseCharacter.jinxes());
    }

    /**
     * Finds a TestCharacter by its id from the baseCharacters array.
     * @param id The id of the character to find.
     * @return The TestCharacter with the matching id.
     */
    private static botcCharacter findCharacterById(String id) {
        for (botcCharacter character : baseCharacters) {
            if (character.id.equals(id)) {
                return character;
            }
        }
        return new botcCharacter(id, id,
                null, "", null, null, null,
                0, null,
                0, null, null, null,
                false, null
        );
    }

    /**
     * Registers base characters by loading and parsing the JSON resource.
     * @param resource The resource containing the base_characters.json data.
     */
    public static void registerBaseCharacters(Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));

            Gson gson = new Gson();
            baseCharacters = gson.fromJson(reader, botcCharacter[].class);
            botc.LOGGER.info("Successfully parsed base_characters.json, size: {} characters", baseCharacters.length);
        } catch (Exception e) {
            botc.LOGGER.error("Error parsing base_characters.json", e);
        }
    }

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
        return Text.literal(this.name);
    }

    /**
     * Convert the character's name to a formatted Text object based on team color.
     * @param dark Whether to use dark mode colors.
     * @return The character's name as formatted Text.
     */
    public Text toFormattedText(boolean dark) {
        if (this.team == null) {
            return this.toText();
        }
        return this.toText().formatted(this.team.getColour(dark));
    }

    /**
     * Convert the character's name to a formatted Text object with hover text of the character's ability.
     * @param dark Whether to use dark mode colors.
     * @return The character's name as formatted Text with hover text of the ability.
     */
    public Text toTextWithHoverAbility(boolean dark) {
        MutableText text = (MutableText) this.toFormattedText(dark);
        if (this.ability != null && !this.ability.isEmpty()) {
            MutableText abilityText = Text.empty();
            abilityText.append(this.toFormattedText(false));
            abilityText.append("\n");
            abilityText.append(Text.literal(this.ability).setStyle(Style.EMPTY.withItalic(true).withColor(Formatting.GRAY)));
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
    }
}