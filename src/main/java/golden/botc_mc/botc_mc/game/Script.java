package golden.botc_mc.botc_mc.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.resource.Resource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a script in the BOTC game, containing meta information and a list of characters.
 */
public record Script(Meta meta, List<Character> characters) {
    /**
     * Represents an empty script with no characters and default meta information.
     */
    public static final Script EMPTY = new Script(Meta.EMPTY, List.of());
    /**
     * Represents a missing script, indicated by a null value.
     */
    public static final Script MISSING = null;

    public Script(String name, String author, String logo, boolean hideTitle, String background, String almanac,
                  String flavor, List<String> bootlegger, List<String> firstNight, List<String> otherNight,
                  int[] colour,
                  List<Character> characters) {
        this(new Meta(
                "_meta",
                name,
                author,
                flavor,
                logo,
                hideTitle,
                background,
                almanac,
                bootlegger,
                firstNight,
                otherNight,
                colour
        ), characters);
    }

    /**
     * Load a Script from a given Resource.
     * @param resource The resource to load the script from.
     * @return The loaded Script object, or null if an error occurred.
     */
    public static Script fromResource(Resource resource) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Script.class, new ScriptDeserializer())
                .create();
        Script scriptData = null;
        try (var stream = resource.getInputStream()) {
            var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8);
            scriptData = gson.fromJson(reader, Script.class);
            botc.LOGGER.info("Read script {}", scriptData.meta.name());
        } catch (Exception e) {
            botc.LOGGER.error("Error reading script", e);
        }
        return scriptData;
    }

    /**
     * Retrieve a Script by its ID from the botc scripts map.
     * @param scriptId The ID of the script to retrieve.
     * @return The Script object if found, otherwise null.
     */
    public static Script fromId(String scriptId) {
        if (botc.scripts.isEmpty()) {
            botc.LOGGER.warn("Scripts map is empty. Lookup for script '{}' will fail.", scriptId);
            return null;
        }
        Script scriptData = botc.scripts.get(scriptId);
        if (scriptData == null) {
            scriptData = botc.scripts.get(scriptId + ".json");
            if (scriptData == null) {
                botc.LOGGER.error("Script with ID '{}' not found.", scriptId);
            }
        }
        if (scriptData == null || Character.baseCharacters == null) {
            return scriptData;
        }
        for (Character character : scriptData.characters) {
            Character fullCharacter = Character.fromPartialCharacter(character);
            // Replace character in the script's character list
            int index = scriptData.characters.indexOf(character);
            scriptData.characters.set(index, fullCharacter);
        }
        return scriptData;
    }

    /**
     * Convert the script's colour array to an integer representation.
     * @return The integer representation of the colour.
     * NOTE: Returns 0xFFFFFF (white) if colour is not defined.
     */
    public int colourInt() {
        if (meta.colour == null || meta.colour.length < 3) {
            return 0xFFFFFF; // Default to white
        }
        int r = meta.colour[0];
        int g = meta.colour[1];
        int b = meta.colour[2];
        return ((r << 16) | (g << 8) | b) & 0xFFFFFF;
    }

    /**
     * Convert the script's colour array to a hexadecimal string representation.
     * @return The hexadecimal string representation of the colour.
     * NOTE: Returns "#FFFFFF" (white) if colour is not defined.
     */
    public String colourHex() {
        return String.format("#%06X", colourInt());
    }

    /**
     * Get the formatted name of the script as a MutableText object with the script's colour.
     * @return The formatted name of the script.
     */
    public MutableText toFormattedText() {
        MutableText text = (MutableText) Text.of(meta.name());
        if (meta.colour == null || meta.colour.length < 3) {
            return text;
        }
        return text.withColor(colourInt());
    }

    /**
     * Get the order of actions for the first night.
     * @param isTeensy Whether the game is in teensy mode.
     * @return The list of character IDs in the order they act on the first night.
     */
    public List<String> firstNightOrder(boolean isTeensy) {
        List<String> order;
        if (meta.firstNight != null && !meta.firstNight.isEmpty()) {
            // Use predefined first night order from meta
            order = meta.firstNight;
        } else {
            order = new ArrayList<>();
            // Sort characters by firstNight value
            characters.stream()
                    .sorted(Comparator.comparingInt(Character::firstNight))
                    .forEach(c -> order.add(c.id()));
            if (!isTeensy) {
                order.addFirst("minioninfo");
                order.addFirst("demoninfo");
            }
            order.addFirst("dusk");
            order.add("dawn");
        }

        return order;
    }

    /**
     * Get the order of actions for the first night without teensy mode.
     * @return The list of character IDs in the order they act on the first night.
     */
    public List<String> firstNightOrder() {
        return firstNightOrder(false);
    }

    public List<String> otherNightOrder() {
        List<String> order;
        if (meta.otherNight != null && !meta.otherNight.isEmpty()) {
            order = meta.otherNight;
        } else {
            order = new ArrayList<>();
            // Sort characters by otherNight value
            characters.stream()
                    .sorted(Comparator.comparingInt(Character::otherNight))
                    .forEach(c -> order.add(c.id()));
            order.addFirst("dusk");
            order.add("dawn");
        }

        return order;
    }

    /**
     * Get jinxes on the script for a specific character.
     * Note: This doesn't include jinxes where the character is the secondary target.
     * This prevents duplicate entries when listing all jinxes.
     * @param character The character.
     * @return The list of jinxes for the character.
     */
    public List<Jinx> getJinxesForCharacter(Character character) {
        List<Jinx> jinxes = new ArrayList<>();
        if (character.jinxes() == null || character.jinxes().isEmpty()) {
            return jinxes;
        }
        for (Jinx jinx : character.jinxes()) {
            if (jinx == null || jinx.id() == null) {
                continue;
            }
            String targetId = jinx.id();
            boolean onScript = characters.stream().anyMatch(c -> targetId.equals(c.id()));
            if (onScript) {
                jinxes.add(jinx);
            }
        }
        return jinxes;
    }

    public List<Jinx> getJinxesForCharacter(String characterId) {
        return getJinxesForCharacter(new Character(characterId));
    }

    /**
     * Get all jinxes in the script.
     * @return The list of all jinxes.
     */
    public Map<Character, List<Jinx>> getJinxes() {
        HashMap<Character, List<Jinx>> allJinxes = new HashMap<>();
        for (Character character : characters) {
            List<Jinx> characterJinxes = getJinxesForCharacter(character);
            if (!characterJinxes.isEmpty()) allJinxes.put(character, characterJinxes);
        }
        return allJinxes;
    }

    /**
     * Get all characters belonging to a specific team.
     * @param team The team to filter characters by.
     * @return A list of characters belonging to the specified team.
     */
    public List<Character> getCharactersByTeam(Team team) {
        List<Character> teamCharacters = new ArrayList<>();
        for (Character character : characters) {
            if (character.team().equals(team)) {
                teamCharacters.add(character);
            }
        }
        return teamCharacters;
    }

    @Override
    public @NotNull String toString() {
        return "Script[name='" + meta.name + "', author='" + meta.author + "', logo='" + meta.logo +
                "', hideTitle=" + meta.hideTitle + ", background='" + meta.background + "', almanac='" + meta.almanac +
                "', bootlegger=" + meta.bootlegger + ", firstNight=" + meta.firstNight +
                ", otherNight=" + meta.otherNight + ", color=" + Arrays.toString(meta.colour) +
                ", characters=[" + characters.size() + " characters]]";
    }

    /**
     * Meta information that appears as the first element in array script files.
     * Stores information about the script.
     */
    public record Meta(String id,
                       String name,
                       String author,
                       String flavor,
                       String logo,
                       Boolean hideTitle,
                       String background,
                       String almanac,
                       List<String> bootlegger,
                       List<String> firstNight,
                       List<String> otherNight,
                       int[] colour) {

        public static final Meta EMPTY = new Meta(
                "_meta",
                "Unnamed Script",
                "",
                null,
                null,
                false,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    /**
     * Represents a jinx applied to a character.
     * Contains the ID of the target character and the rule change for the jinx.
     */
    public record Jinx(String id, String reason) {
        public static final Codec<Jinx> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Jinx::id),
                Codec.STRING.fieldOf("reason").forGetter(Jinx::reason)
        ).apply(instance, Jinx::new));
    }
}
