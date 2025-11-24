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
 * Manages persistent voice chat groups attached to a specific map's game JSON.
 * <p>
 * Data model:
 * <ul>
 *   <li>Groups are stored under the {@code voice.voice_groups} array inside a Plasmid game json.</li>
 *   <li>Each entry is a {@link PersistentGroup} describing a Simple Voice Chat group to preload.</li>
 * </ul>
 * Load precedence:
 * <ol>
 *   <li>World override datapack under {@code run/world/datapacks/botc_overrides}.</li>
 *   <li>Embedded game JSON shipped with the mod/datapack for the given map id.</li>
 * </ol>
 * Save behaviour:
 * <ul>
 *   <li>Only writes to the overrides datapack, never back into the original embedded JSON.</li>
 *   <li>Preserves any non-voice keys already present in the target JSON.</li>
 * </ul>
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

    /** Factory for manager tied to server &amp; map.
     * @param server minecraft server
     * @param mapId map identifier
     * @return new VoiceGroupManager
     */
    public static VoiceGroupManager forServer(MinecraftServer server, Identifier mapId) {
        return new VoiceGroupManager(mapId, server);
    }

    /** Immutable snapshot of loaded groups.
     * @return unmodifiable list
     */
    public List<PersistentGroup> list() { return Collections.unmodifiableList(groups); }

    /**
     * Reload groups from disk, preferring overrides and then falling back to embedded JSON.
     * Errors are logged but do not throw, so a bad overrides file will not crash the server.
     */
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

    /**
     * Persist the in-memory list of {@link PersistentGroup} entries to the overrides datapack.
     * If a base JSON file exists (either from a previous override or embedded resource), its
     * non-voice fields are preserved and only the {@code voice.voice_groups} field is updated.
     */
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
            JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : new JsonObject();
            JsonElement groupsElem = gson.toJsonTree(groups, new TypeToken<List<PersistentGroup>>(){}.getType());
            voiceSection.add("voice_groups", groupsElem);
            obj.add("voice", voiceSection);
            String out = gson.toJson(obj);
            Files.write(target, out.getBytes());
            // Use centralized helper instead of duplicated pack.mcmeta logic
            VoiceRegionService.ensureOverridesPackMeta(datapackBase, "BOTC overrides datapack");
        } catch (IOException e) {
            LOGGER.warn("VoiceGroupManager save failed: {}", e.toString());
        }
    }
}
