package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages persistent voice chat groups stored inside the map JSON under key "voice_groups".
 * Load precedence: override datapack -> embedded resource; Save target: override datapack only.
 */
public class VoiceGroupManager {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("botc.VoiceGroupManager");

    private final Identifier mapId;
    private final MinecraftServer server; // optional alternative for embedded read
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final List<PersistentGroup> groups = new ArrayList<>();

    private VoiceGroupManager(Identifier mapId, MinecraftServer server) {
        this.mapId = mapId;
        this.server = server;
        load();
    }

    public static VoiceGroupManager forServer(MinecraftServer server, Identifier mapId) {
        return new VoiceGroupManager(mapId, server);
    }

    public List<PersistentGroup> list() { return Collections.unmodifiableList(groups); }

    private void load() {
        groups.clear();
        try {
            // 1) World override datapack
            if (mapId != null) {
                Path override = Paths.get("run", "world", "datapacks", "botc_overrides", "data", mapId.getNamespace(), "plasmid", "game", mapId.getPath() + ".json");
                if (Files.exists(override)) {
                    String s = new String(Files.readAllBytes(override));
                    JsonObject obj = gson.fromJson(s, JsonObject.class);
                    if (obj != null) {
                        JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                        if (voiceSection.has("voice_groups")) {
                            JsonElement arr = voiceSection.get("voice_groups");
                            Type type = new TypeToken<List<PersistentGroup>>(){}.getType();
                            List<PersistentGroup> list = gson.fromJson(arr, type);
                            if (list != null) groups.addAll(list);
                            return;
                        }
                    }
                }
            }
            // 2) Embedded resource in mod datapack
            if (server != null && mapId != null) {
                try {
                    Identifier res = Identifier.of(mapId.getNamespace(), "plasmid/game/" + mapId.getPath() + ".json");
                    var opt = server.getResourceManager().getResource(res);
                    if (opt.isPresent()) {
                        try (InputStream is = opt.get().getInputStream(); Reader r = new InputStreamReader(is)) {
                            JsonObject obj = gson.fromJson(r, JsonObject.class);
                            if (obj != null) {
                                JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                                if (voiceSection.has("voice_groups")) {
                                    JsonElement arr = voiceSection.get("voice_groups");
                                    Type type = new TypeToken<List<PersistentGroup>>(){}.getType();
                                    List<PersistentGroup> list = gson.fromJson(arr, type);
                                    if (list != null) groups.addAll(list);
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.debug("VoiceGroupManager: failed to read embedded map game json: {}", ex.toString());
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("VoiceGroupManager load failed: {}", ex.toString());
        }
    }

    public void save() {
        if (mapId == null) return;
        Path datapackBase = Paths.get("run", "world", "datapacks", "botc_overrides");
        Path target = datapackBase.resolve(Paths.get("data", mapId.getNamespace(), "plasmid", "game", mapId.getPath() + ".json"));
        try {
            Files.createDirectories(target.getParent());
            JsonObject obj = null;
            if (Files.exists(target)) {
                String existing = new String(Files.readAllBytes(target));
                obj = gson.fromJson(existing, JsonObject.class);
            } else if (server != null) {
                try {
                    Identifier res = Identifier.of(mapId.getNamespace(), "plasmid/game/" + mapId.getPath() + ".json");
                    var opt = server.getResourceManager().getResource(res);
                    if (opt.isPresent()) {
                        try (InputStream is = opt.get().getInputStream(); Reader r = new InputStreamReader(is)) {
                            obj = gson.fromJson(r, JsonObject.class);
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (obj == null) obj = new JsonObject();
            // if (!obj.has("type")) obj.addProperty("type", "botc-mc:game");
            // obj.addProperty("map_id", mapId.toString());
            // Ensure we write groups inside 'voice' section to avoid creating map objects
            JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : new JsonObject();
            JsonElement groupsElem = gson.toJsonTree(groups, new TypeToken<List<PersistentGroup>>(){}.getType());
            voiceSection.add("voice_groups", groupsElem);
            obj.add("voice", voiceSection);

            String out = gson.toJson(obj);
            Files.write(target, out.getBytes());

            // ensure pack.mcmeta
            Path packMeta = datapackBase.resolve("pack.mcmeta");
            if (!Files.exists(packMeta)) {
                JsonObject pack = new JsonObject();
                JsonObject inner = new JsonObject();
                inner.addProperty("pack_format", 12);
                inner.addProperty("description", "BOTC overrides datapack");
                pack.add("pack", inner);
                Files.write(packMeta, gson.toJson(pack).getBytes());
            }
        } catch (IOException e) {
            LOGGER.warn("VoiceGroupManager save failed: {}", e.toString());
        }
    }
}
