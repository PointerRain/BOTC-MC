package golden.botc_mc.botc_mc;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resource.Resource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
    /** The base characters loaded from the JSON resource. */
    public static Character[] baseCharacters;

    /**
     * Constructs a TestCharacter by looking it up from the baseCharacters array using the given id.
     */
    public Character(String id) {
        this(findCharacterById(id));
    }

    /** Copy constructor to create a new TestCharacter from an existing one. */
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
        throw new IllegalArgumentException("No TestCharacter found with id: " + id);
    }

    /**
     * Registers base characters by loading and parsing the JSON resource.
     * @param resource The resource containing the base_characters.json data.
     */
    public static void registerBaseCharacters(Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            botc.LOGGER.info("Successfully loaded base_characters.json, size: {} bytes", stream.available());
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));

            Gson gson = new Gson();
            baseCharacters = gson.fromJson(reader, Character[].class);
            botc.LOGGER.info("Successfully parsed base_characters.json, size: {} characters", baseCharacters.length);
        } catch (Exception e) {
            botc.LOGGER.error("Error parsing base_characters.json", e);
        }
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

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }
}
