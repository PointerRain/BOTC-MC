package golden.botc_mc.botc_mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Script(String name,
                     String author,
                     String logo,
                     boolean hideTitle,
                     String background,
                     String almanac,
                     List<String> bootlegger,
                     List<String> firstNight,
                     List<String> otherNight,

                     List<golden.botc_mc.botc_mc.Character> characters
) {


    public static Script fromResource(Resource resource) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Script.class, new ScriptDeserializer())
                .create();
        Script scriptData = null;
        try (var stream = resource.getInputStream()) {
            var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8);
            scriptData = gson.fromJson(reader, Script.class);
            botc.LOGGER.info("Loaded script {}", scriptData.name());
        } catch (Exception e) {
            botc.LOGGER.error("Error reading script", e);
        }
        return scriptData;
    }


    public List<String> firstNightOrder(boolean isTeensy) {
        List<String> order;
        if (firstNight != null && !firstNight.isEmpty()) {
            order = firstNight;
        } else {
            order = new ArrayList<>();
            // Sort characters by firstNight value
            characters.stream()
                    .sorted(Comparator.comparingInt(golden.botc_mc.botc_mc.Character::firstNight))
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
        if (otherNight != null && !otherNight.isEmpty()) {
            order = otherNight;
        } else {
            order = new ArrayList<>();
            // Sort characters by otherNight value
            characters.stream()
                    .sorted(Comparator.comparingInt(golden.botc_mc.botc_mc.Character::otherNight))
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
    public List<Jinx> getJinxesForCharacter(golden.botc_mc.botc_mc.Character character) {
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
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public @NotNull String toString() {
        return "Script[name='" + name + "', author='" + author + "', logo='" + logo + "', hideTitle=" + hideTitle +
                ", background='" + background + "', almanac='" + almanac + "', bootlegger=" + bootlegger +
                ", firstNight=" + firstNight + ", otherNight=" + otherNight + "," +
                "characters=[" + characters.size() + " characters]]";
    }

    public record Jinx(String id, String reason) {}
}
