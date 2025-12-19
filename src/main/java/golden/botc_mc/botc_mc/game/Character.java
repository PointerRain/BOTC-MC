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
 * Represents a character in the BOTC game, with various attributes and behaviors.
 */
public record Character(String id,
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

    public static final Codec<Character> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Character::id),
            Codec.STRING.fieldOf("name").forGetter(Character::name),
            Team.CODEC.fieldOf("team").forGetter(Character::team),
            Codec.STRING.fieldOf("ability").forGetter(Character::ability),
            Codec.STRING.fieldOf("image").forGetter(Character::image),
            Codec.STRING.fieldOf("edition").forGetter(Character::edition),
            Codec.STRING.fieldOf("flavor").forGetter(Character::flavor),
            Codec.INT.fieldOf("firstNight").forGetter(Character::firstNight),
            Codec.STRING.fieldOf("firstNightReminder").forGetter(Character::firstNightReminder),
            Codec.INT.fieldOf("otherNight").forGetter(Character::otherNight),
            Codec.STRING.fieldOf("otherNightReminder").forGetter(Character::otherNightReminder),
            Codec.STRING.listOf().fieldOf("reminders").forGetter(Character::reminders),
            Codec.STRING.listOf().fieldOf("remindersGlobal").forGetter(Character::remindersGlobal),
            Codec.BOOL.fieldOf("setup").forGetter(Character::setup),
            Script.Jinx.CODEC.listOf().fieldOf("jinxes").forGetter(Character::jinxes)
    ).apply(instance, Character::new));

    /**
     * The base characters loaded from the JSON resource.
     */
    public static Character[] baseCharacters;

    /**
     * Constructs a Character by looking it up from the baseCharacters array using the given id.
     */
    public Character(String id) {
        this(findCharacterById(id));
    }

    /**
     * Copy constructor to create a new Character from an existing one.
     */
    private Character(Character character) {
        this(character.id,
                character.name,
                character.team,
                character.ability,
                character.image,
                character.edition,
                character.flavor,
                character.firstNight,
                character.firstNightReminder,
                character.otherNight,
                character.otherNightReminder,
                character.reminders,
                character.remindersGlobal,
                character.setup,
                character.jinxes);
    }

    public static Character EMPTY = new Character(
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
     * @param character The partial Character object.
     * @return A (hopefully) complete Character object.
     */
    public static Character fromPartialCharacter(Character character) {
        if (character.id() == null) {
            throw new IllegalArgumentException("Character id cannot be null");
        }
        if (baseCharacters == null) {
            botc.LOGGER.warn("Base characters not loaded yet, returning partial character as is.");
            return character;
        }
        Character baseCharacter = findCharacterById(character.id());
        return new Character(
                character.id(),
                character.name() != null ? character.name() : baseCharacter.name(),
                character.team() != null ? character.team() : baseCharacter.team(),
                character.ability() != null ? character.ability() : baseCharacter.ability(),
                character.image() != null ? character.image() : baseCharacter.image(),
                character.edition() != null ? character.edition() : baseCharacter.edition(),
                character.flavor() != null ? character.flavor() : baseCharacter.flavor(),
                character.firstNight() != 0 ? character.firstNight() : baseCharacter.firstNight(),
                character.firstNightReminder() != null ? character.firstNightReminder() :
                        baseCharacter.firstNightReminder(),
                character.otherNight() != 0 ? character.otherNight() : baseCharacter.otherNight(),
                character.otherNightReminder() != null ? character.otherNightReminder() :
                        baseCharacter.otherNightReminder(),
                character.reminders() != null ? character.reminders() : baseCharacter.reminders(),
                character.remindersGlobal() != null ? character.remindersGlobal() : baseCharacter.remindersGlobal(),
                character.setup() || baseCharacter.setup(),
                character.jinxes != null ? character.jinxes() : baseCharacter.jinxes());
    }

    /**
     * Finds a TestCharacter by its id from the baseCharacters array.
     * @param id The id of the character to find.
     * @return The TestCharacter with the matching id.
     */
    private static Character findCharacterById(String id) {
        for (Character character : baseCharacters) {
            if (character.id.equals(id)) {
                return character;
            }
        }
        return new Character(id, id,
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
            baseCharacters = gson.fromJson(reader, Character[].class);
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
        return this.team.getDefaultAlignment() == Team.Alignment.NPC;
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

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    public record ReminderToken(Character character, String reminder) {}
}