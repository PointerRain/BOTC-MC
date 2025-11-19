package golden.botc_mc.botc_mc;


import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ScriptDeserializer implements JsonDeserializer<Script> {

    @Override
    public Script deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (!json.isJsonArray()) {
            throw new JsonParseException("Expected a JSON array");
        }

        JsonArray jsonArray = json.getAsJsonArray();
        String name = null;
        String author = null;
        String logo = null;
        boolean hideTitle = false;
        String background = null;
        String almanac = null;
        List<String> bootlegger = null;
        List<String> firstNight = null;
        List<String> otherNight = null;
        List<Character> characters = new ArrayList<>();

        for (JsonElement element : jsonArray) {
            if (element.isJsonObject() && element.getAsJsonObject().has("id") && "_meta".equals(element.getAsJsonObject().get("id").getAsString())) {
                JsonObject metaObj = element.getAsJsonObject();
                name = getString(metaObj, "name");
                author = getString(metaObj, "author");
                logo = getString(metaObj, "logo");
                hideTitle = metaObj.has("hide_title") && metaObj.get("hide_title").getAsBoolean();
                background = getString(metaObj, "background");
                almanac = getString(metaObj, "almanac");
                bootlegger = getStringList(metaObj, "bootlegger");
                firstNight = getStringList(metaObj, "first_night");
                otherNight = getStringList(metaObj, "other_night");

            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                characters.add(new Character(element.getAsString()));
            } else if (element.isJsonObject()) {
                characters.add(context.deserialize(element, Character.class));
            } else {
                throw new JsonParseException("Unexpected JSON element: " + element);
            }
        }

        return new Script(name, author, logo, hideTitle, background, almanac, bootlegger, firstNight, otherNight,
                characters);
    }

    // Helper methods
    private String getString(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : null;
    }

    private List<String> getStringList(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement element : obj.get(key).getAsJsonArray()) {
                list.add(element.getAsString());
            }
            return list;
        }
        return null;
    }
}