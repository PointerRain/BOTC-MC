package golden.botc_mc.botc_mc.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.resource.Resource;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
            var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
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
     * Get a character from the script by its ID.
     * @param id The ID of the character to retrieve.
     * @return The Character object if found, otherwise a base character or Character.EMPTY.
     */
    public Character getCharacter(String id) {
        // Try to find character by ID
        for (Character character : this.characters) {
            if (character.id().equals(id)) {
                return character;
            }
        }
        // Find character from all base characters as fallback
        Character baseCharacter = new Character(id);
        return switch (baseCharacter.team()) {
            case FABLED, LORIC, TRAVELLER -> baseCharacter;
            default -> null;
        };
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
     * @return The list of NightActions in the order they act on the first night.
     */
    public List<NightAction> firstNightOrder(boolean isTeensy) {
        ArrayList<NightAction> order = new ArrayList<>();
        if (meta.firstNight != null && !meta.firstNight.isEmpty()) {
            // Use predefined first night order from meta
            meta.firstNight.forEach(s -> order.add(NightAction.firstNightAction(this, s)));
        } else {
            // Sort characters by firstNight value
            characters.stream()
                    .filter(c -> c.firstNight() > 0 && c.firstNightReminder() != null && !c.firstNightReminder().isEmpty())
                    .sorted(Comparator.comparingInt(Character::firstNight))
                    .map(NightAction::firstNightAction)
                    .forEach(order::add);
            if (!isTeensy) {
                order.addFirst(NightAction.DEMONINFO);
                order.addFirst(NightAction.MINIONINFO);
            }
            order.addFirst(NightAction.DUSK);
            order.add(NightAction.DAWN);
        }

        return order;
    }

    /**
     * Get the order of actions for the first night without teensy mode.
     * @return The list of NightActions in the order they act on the first night.
     */
    public List<NightAction> firstNightOrder() {
        return firstNightOrder(false);
    }

    /**
     * Get the order of actions for nights other than the first.
     * @return The list of NightActions in the order they act on nights other than the first.
     */
    public List<NightAction> otherNightOrder() {
        List<NightAction> order = new ArrayList<>();
        if (meta.otherNight != null && !meta.otherNight.isEmpty()) {
            // Use predefined other night order from meta
            meta.otherNight.forEach(s -> order.add(NightAction.otherNightAction(this, s)));
        } else {
            // Sort characters by otherNight value
            characters.stream()
                    .filter(c -> c.otherNight() > 0 && c.otherNightReminder() != null && !c.otherNightReminder().isEmpty())
                    .sorted(Comparator.comparingInt(Character::otherNight))
                    .map(NightAction::otherNightAction)
                    .forEach(order::add);
            order.addFirst(NightAction.DUSK);
            order.add(NightAction.DAWN);
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

        /**
         * Get a formatted * character with hover text containing the Jinx information.
         * @return The formatted text of the "jinx star".
         */
        public MutableText jinxStar() {
            MutableText jinxText = Text.empty();
            jinxText.append(new Character(this.id()).toFormattedText(false));
            jinxText.append(Text.literal("\n"));
            jinxText.append(Text.literal(this.reason()).setStyle(Style.EMPTY.withItalic(true).withColor(Formatting.GRAY)));
            HoverEvent hover = new HoverEvent.ShowText(jinxText);
            return Text.literal("*")
                            .styled(style -> style
                                    .withColor(Team.FABLED.getColour(false))
                                    .withBold(false).withUnderline(false)
                                    .withHoverEvent(hover));
        }
    }

    /**
     * Represents a night action in the game.
     * Contains information about the action's ID, name, reminder text, and colour provider.
     */
    public static class NightAction {
        public static final NightAction DUSK = new NightAction("dusk", "Dusk", "Start the Night Phase.",
                b -> Formatting.GRAY);
        public static final NightAction DAWN = new NightAction("dawn", "Dawn", "The night ends.",
                b -> Formatting.GRAY);
        public static final NightAction MINIONINFO = new NightAction("minioninfo",
                "Minion Info",
                "If there are 7 or more players, wake all Minions: Show the THIS IS THE DEMON token. Point to the " +
                        "Demon. Show the THESE ARE YOUR MINIONS token. Point to the other Minions.",
                Team.MINION::getColour);
        public static final NightAction DEMONINFO = new NightAction("demoninfo",
                "Demon Info",
                "If there are 7 or more players, wake the Demon: Show the THESE ARE YOUR MINIONS token. Point to all " +
                        "Minions. Show the THESE CHARACTERS ARE NOT IN PLAY token. Show 3 not-in-play good character " +
                        "tokens.",
                Team.DEMON::getColour);

        final String id;
        final String name;
        final String reminder;
        final Function<? super Boolean, Formatting> colourProvider;

        public NightAction(String id, String name, String reminder, Function<? super Boolean, Formatting> colourProvider) {
            this.id = id;
            this.name = name;
            this.reminder = reminder;
            this.colourProvider = colourProvider;
        }

        // Construct NightAction from Character and reminder
        public NightAction(Character character, String reminder) {
            this(character.id(),
                    character.name(),
                    reminder,
                    character.team() != null ? character.team()::getColour : b -> Formatting.BLACK);
        }

        // Static factory methods for creating NightActions

        // Creates a NightAction for the first night for a given character
        public static NightAction firstNightAction(Character character) {
            return new NightAction(character, character.firstNightReminder());
        }

        // Creates a NightAction for the first night for a given character ID in a script
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
            Character character = script.characters().stream()
                    .filter(c -> c.id().equals(characterId))
                    .findFirst().orElseThrow();
            return new NightAction(character, character.firstNightReminder());
        }

        // Creates a NightAction for nights other than the first for a given character
        public static NightAction otherNightAction(Character character) {
            return new NightAction(character, character.otherNightReminder());
        }

        // Creates a NightAction for nights other than the first for a given character ID in a script
        public static NightAction otherNightAction(Script script, String characterId) {
            switch (characterId) {
                case "dusk":
                    return DUSK;
                case "dawn":
                    return DAWN;
            }
            Character character = script.characters().stream()
                    .filter(c -> c.id().equals(characterId))
                    .findFirst().orElseThrow();
            return new NightAction(character, character.otherNightReminder());
        }

        /**
         * Get the formatted text representation of the night action's name with appropriate colour.
         * @return The formatted text of the night action's name.
         */
        public MutableText toFormattedText() {
            MutableText text = (MutableText) Text.of(name);
            return text.formatted(colourProvider.apply(false));
        }

        @Override
        public String toString() {
            return "NightAction[id='" + id + "', name='" + name + "', reminder='" + reminder + "']";
        }
    }
}