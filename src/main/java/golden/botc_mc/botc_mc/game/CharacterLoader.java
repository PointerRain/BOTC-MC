package golden.botc_mc.botc_mc.game;

import com.google.gson.Gson;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.resource.Resource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CharacterLoader {

    /**
     * The base botcCharacters loaded from the JSON resource.
     */
    public static botcCharacter[] baseCharacters;
    public static List<String> firstNightOrder;
    public static List<String> otherNightOrder;

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
                botcCharacter.jinxes() != null ? botcCharacter.jinxes() : baseCharacter.jinxes(),
                botcCharacter.token() != null ? botcCharacter.token() : baseCharacter.token());
    }

    /**
     * Finds a Character by its id from the baseCharacters array. Returns a new Character with only the id set if not found.
     * This should not be directly used if loading from a script.
     * @param id The id of the character to find.
     * @return The Character with the matching id.
     */
    static botcCharacter findCharacterById(String id) {
        for (botcCharacter character : baseCharacters) {
            if (character.id().equals(id)) {
                return character;
            }
        }
        return new botcCharacter(id, null,
                Team.TOWNSFOLK, null, null, null, null,
                0, null,
                0, null, null, null,
                false, null, null
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
            baseCharacters = new botcCharacter[0];
        }
    }

    public static void registerNightOrder(NightType night, Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));

            Gson gson = new Gson();
            List<String> nightOrder = gson.fromJson(reader, List.class);
            if (night == NightType.FIRST) {
                firstNightOrder = nightOrder;
                botc.LOGGER.info("Loaded first_night.json: {}", firstNightOrder);
            } else {
                otherNightOrder = nightOrder;
                botc.LOGGER.info("Loaded other_night.json: {}", otherNightOrder);
            }
        } catch (Exception e) {
            botc.LOGGER.error("Error parsing base_characters.json", e);
        }
    }

}
