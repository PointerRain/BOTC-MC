package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import golden.botc_mc.botc_mc.botc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

/**
 * File-backed storage for {@link PersistentGroup} definitions.
 * <p>
 * This class provides a simple, process-wide registry of named voice chat groups that BOTC
 * should attempt to keep alive across restarts. Key behaviors:
 * <ul>
 *   <li>Stores data as JSON at {@code gameDir/config/botc/config/botc-persistent-groups.json}.</li>
 *   <li>Maintains an in-memory list and a UUID → group cache for quick lookups by Simple Voice Chat id.</li>
 *   <li>All mutating methods are {@code synchronized} to provide basic thread-safety.</li>
 * </ul>
 */
public class PersistentGroupStore {
    private final File file;
    private final Gson gson;
    private final List<PersistentGroup> groups = new ArrayList<>();
    private final Map<UUID, PersistentGroup> cache = new HashMap<>();

    /** Construct an empty store for persistent voice groups. */
    public PersistentGroupStore() {
        // Use BOTC config root: {@code gameDir/config/botc/}
        Path botcRoot = VoiceRegionService.botcConfigRoot();
        // Ensure root directory exists so subsequent writes don't fail.
        try { java.nio.file.Files.createDirectories(botcRoot); } catch (Throwable ignored) {}

        // Global persistent groups file: {@code gameDir/config/botc/config/botc-persistent-groups.json}
        Path cfgDir = botcRoot.resolve("config");
        try { java.nio.file.Files.createDirectories(cfgDir); } catch (Throwable ignored) {}
        this.file = cfgDir.resolve("botc-persistent-groups.json").toFile();

        // Register a type adapter so Gson can deserialize into the immutable PersistentGroup
        JsonDeserializer<PersistentGroup> deserializer = (JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) -> {
            try {
                JsonObject obj = json.getAsJsonObject();
                String name = "";
                if (obj.has("name") && !obj.get("name").isJsonNull()) name = obj.get("name").getAsString();
                UUID id = null;
                // Accept multiple common key names for historical compatibility
                if (obj.has("voicechatId") && !obj.get("voicechatId").isJsonNull()) {
                    try { id = UUID.fromString(obj.get("voicechatId").getAsString()); } catch (Throwable ignored) {}
                } else if (obj.has("voicechat_id") && !obj.get("voicechat_id").isJsonNull()) {
                    try { id = UUID.fromString(obj.get("voicechat_id").getAsString()); } catch (Throwable ignored) {}
                } else if (obj.has("id") && !obj.get("id").isJsonNull()) {
                    try { id = UUID.fromString(obj.get("id").getAsString()); } catch (Throwable ignored) {}
                }
                return new PersistentGroup(name, id);
            } catch (Throwable t) {
                throw new JsonParseException(t);
            }
        };

        JsonSerializer<PersistentGroup> serializer = (PersistentGroup src, Type typeOfSrc, com.google.gson.JsonSerializationContext context) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", src.getName());
            obj.addProperty("voicechatId", src.getVoicechatId() == null ? null : src.getVoicechatId().toString());
            return obj;
        };

        this.gson = new GsonBuilder()
                .registerTypeAdapter(PersistentGroup.class, deserializer)
                .registerTypeAdapter(PersistentGroup.class, serializer)
                .setPrettyPrinting()
                .create();

        load();
    }


    /**
     * Load all persisted {@link PersistentGroup} entries from disk into memory. If the file does not
     * exist yet, this method is a no-op and the in-memory registry remains empty.
     */
    public synchronized void load() {
        try {
            if (!file.exists()) return;
            Type listType = new TypeToken<List<PersistentGroup>>(){}.getType();
            try (FileReader fr = new FileReader(file)) {
                List<PersistentGroup> loaded = gson.fromJson(fr, listType);
                if (loaded != null) {
                    groups.clear(); groups.addAll(loaded);
                    rebuildCache();
                }
            }
        } catch (Throwable t) {
            botc.LOGGER.warn("PersistentGroupStore load failed: {}", t.toString());
        }
    }

    /**
     * Persist the current in-memory list of groups to the JSON backing file. Any I/O problems are
     * logged but do not propagate as exceptions to callers.
     */
    public synchronized void save() {
        try (FileWriter fw = new FileWriter(file)) {
            gson.toJson(groups, fw);
        } catch (Throwable t) {
            botc.LOGGER.warn("PersistentGroupStore save failed: {}", t.toString());
        }
    }

    /**
     * Rebuild the UUID → {@link PersistentGroup} index from the current list. This is used after
     * load and after bulk modifications to ensure lookups stay in sync.
     */
    private void rebuildCache() {
        cache.clear();
        for (PersistentGroup g : groups) {
            if (g.getVoicechatId() != null) cache.put(g.getVoicechatId(), g);
        }
    }

    /** Immutable snapshot of all known groups.
     * @return unmodifiable list of persistent groups
     */
    public synchronized java.util.List<PersistentGroup> list() {
        return Collections.unmodifiableList(groups);
    }

    /**
     * Cache a runtime voice chat UUID against the persistent group instance and persist change.
     * @param id assigned voice chat group UUID (ignored if null)
     * @param g persistent group descriptor (ignored if null)
     */
    public synchronized void cacheGroup(java.util.UUID id, PersistentGroup g) {
        if (id == null || g == null) return;
        g.setVoicechatId(id);
        cache.put(id, g);
        save();
    }
    /** Lookup a group by runtime voice UUID.
     * @param id voice chat UUID
     * @return matching persistent group or null if not cached
     */
    public synchronized PersistentGroup getByVoiceId(java.util.UUID id) { return cache.get(id); }
}