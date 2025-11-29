package golden.botc_mc.botc_mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public record Script(Meta meta,
                     List<Character> characters
) {
    public Script(String name, String author, String logo, boolean hideTitle, String background, String almanac, String flavor, List<String> bootlegger, List<String> firstNight, List<String> otherNight, List<Character> characters) {
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
                otherNight
        ), characters);
    }

    /**
     * Meta object that appears as the first element in array script files.
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
                       List<String> otherNight) {

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
            List.of()
        );
    }

    public static final Script EMPTY = new Script(Meta.EMPTY, List.of());

    public static final Script MISSING = null;

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

    public List<String> firstNightOrder(boolean isTeensy) {
        List<String> order;
        if (meta.firstNight != null && !meta.firstNight.isEmpty()) {
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
        for (Jinx jinx : character.jinxes()) {
//            if (jinx.id()
        }
        return List.of();
    }

    public List<Jinx> getJinxesForCharacter(String characterId) {
        return getJinxesForCharacter(new Character(characterId));
    }

    /**
     * Get all jinxes in the script.
     * @return The list of all jinxes.
     */
    public List<Object> getJinxes() {
        return List.of();
    }

    @Override
    public @NotNull String toString() {
        return "Script[name='" + meta.name + "', author='" + meta.author + "', logo='" + meta.logo +
                "', hideTitle=" + meta.hideTitle + ", background='" + meta.background + "', almanac='" + meta.almanac +
                "', bootlegger=" + meta.bootlegger + ", firstNight=" + meta.firstNight +
                ", otherNight=" + meta.otherNight + ", characters=[" + characters.size() + " characters]]";
    }

    public record Jinx(String id, String reason) {
        public static final Codec<Jinx> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Jinx::id),
                Codec.STRING.fieldOf("reason").forGetter(Jinx::reason)
        ).apply(instance, Jinx::new));
    }
}
