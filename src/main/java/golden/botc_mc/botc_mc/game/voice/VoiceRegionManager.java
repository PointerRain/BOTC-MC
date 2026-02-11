package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for voice regions tied to a specific map or a global scope.
 *
 * <p>Overview
 * <p>This class loads voice-region definitions from multiple potential sources, normalizes them into
 * {@link VoiceRegion} records and provides two primary runtime services:
 *
 * <ul>
 *   <li>Spatial lookup: given a {@link ServerPlayerEntity} it can return the matching VoiceRegion
 *       containing the player (method {@link #regionForPlayer}).</li>
 *   <li>Persistence: read/write of the per-map JSON config and an optional world datapack override
 *       (methods {@link #save} / internal write helpers).</li>
 * </ul>
 *
 * <p>File / JSON format expectations
 * <p>Voice region JSON is expected in one of two shapes:
 * <ol>
 *   <li>An object with a <code>voice</code> section containing <code>voice_regions</code> (preferred):
 *     <pre>
 *     {
 *       "voice": {
 *         "voice_regions": [ { "id":"foo", "groupName":"G", "groupId":"&lt;uuid&gt;", "cornerA":{...}, "cornerB":{...} }, ... ],
 *         "voice_groups": [ ... ]
 *       }
 *     }
 *     </pre>
 *   </li>
 *   <li>A plain array of region objects (portable shape):
 *     <pre>[ { "id":"foo", "groupName":"G", "cornerA":{...}, "cornerB":{...} }, ... ]</pre>
 *   </li>
 * </ol>
 *
 * <p>Each region object must include an <code>id</code> and two corners (<code>cornerA</code>, <code>cornerB</code>)
 * describing an axis-aligned bounding box; optional fields include <code>groupName</code> and <code>groupId</code>.
 * Missing yaw/pitch or other metadata is tolerated.
 *
 * <p>Thread-safety and runtime behaviour
 * <p>- Uses a concurrent map for fast concurrent reads. Mutating operations that touch files are not globally
 *   synchronized; callers should avoid heavy concurrent writes.
 * <p>- Parsing is robust: malformed entries are skipped and logged; a single bad entry will not abort loading.
 *
 * <p>High-level data shapes (in memory):
 * <p>- <code>regions</code>: Map&lt;String, VoiceRegion&gt; keyed by region id. Each VoiceRegion contains bounds and
 *   optional group linkage.
 *
 * <p>Usage contract
 * <p>- Use {@link #forMap} to create a manager bound to a server-world and map id; the factory will write a default
 *   config if missing.
 * <p>- Call {@link #reload} to refresh runtime data after external edits.
 */
public class VoiceRegionManager {
    // Concurrent container for fast spatial lookup and listings. Keys are region ids.
    private final Map<String, VoiceRegion> regions = new ConcurrentHashMap<>();

    // Backing config file path (per-map or global)
    private final Path configPath;

    // JSON serializer used for reading/writing config files
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Debug flag (can be toggled at runtime via command later if needed)
    private static final boolean DEBUG_REGIONS = false;

    // Optional map association - null when this manager is global/legacy
    private final Identifier mapId;
    // Optional server world reference - used for datapack / embedded resource fallback
    private final ServerWorld world;

    /**
     * Create or open a manager for a specific map. If the per-map config is missing, a default
     * copy will be created by consulting the bundled assets for that map id.
     *
     * @param world server world instance used when resolving embedded map resources
     * @param mapId namespaced map identifier
     * @return new VoiceRegionManager bound to the map
     */
    public static VoiceRegionManager forMap(ServerWorld world, Identifier mapId) {
        try { VoiceRegionService.writeDefaultConfigIfMissing(mapId); } catch (Throwable ignored) {}
        Path path = VoiceRegionService.configPathForMap(mapId);
        return new VoiceRegionManager(path, world, mapId);
    }

    /**
     * Create a manager backed by an explicit config path (global manager)
     *
     * @param configPath path to a JSON config file
     */
    public VoiceRegionManager(Path configPath) {
        this(configPath, null, null);
    }

    /**
     * Internal constructor used by factories.
     *
     * @param configPath path to per-map or global JSON config
     * @param world optional server world used for embedded resource fallback
     * @param mapId optional map identifier
     */
    public VoiceRegionManager(Path configPath, ServerWorld world, Identifier mapId) {
        this.configPath = configPath;
        this.world = world;
        this.mapId = mapId;
        // Load any existing regions on construction
        this.load();
    }

    /**
     * Get the configured map identifier for this manager, or {@code null} when this manager is global.
     * @return configured map id or {@code null} if manager is global
     */
    public Identifier getMapId() { return mapId; }

    /**
     * Get the file system path used for the per-map configuration file backing this manager.
     * @return file path used for per-map config
     */
    public Path getConfigPath() { return configPath; }

    /**
     * Reload the on-disk config and return the new region count. Useful when the file was edited externally.
     * @return number of loaded regions after reload
     */
    public synchronized int reload() {
        regions.clear();
        load();
        return regions.size();
    }

    /**
     * Spatial query: find the voice region containing the player's block coordinates.
     * This is a simple linear scan over regions; the number of regions is expected to be small
     * (typically less than 50) so this is acceptable. If you need large-scale maps, consider a spatial index.
     *
     * @param player server player entity
     * @return matching VoiceRegion or null if player is not inside any region
     */
    public VoiceRegion regionForPlayer(ServerPlayerEntity player) {
        int bx = player.getBlockX();
        int by = player.getBlockY();
        int bz = player.getBlockZ();
        for (VoiceRegion r : regions.values()) {
            if (r.containsBlock(bx, by, bz)) {
                if (DEBUG_REGIONS) {
                    // When debugging, print a concise single-line record that helps trace which region matched
                    golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionManager: player {} in region {} group={} bounds= {}",
                            player.getName().getString(), r.id(), r.groupName(), r.boundsDebug());
                }
                return r;
            }
        }
        return null;
    }

    /**
     * Return an iterable view of all currently known voice regions. The returned collection is backed by
     * the internal map values view and should be treated as read-only by callers.
     *
     * @return collection of known VoiceRegion objects
     */
    public Collection<VoiceRegion> list() { return regions.values(); }

    /**
     * Update the stored group id for the region with the provided id and persist the change.
     * This is used when the voice group is created or repaired and the runtime UUID becomes known.
     *
     * @param id region id to update
     * @param newGroupId new group UUID string or null to clear
     */
    public void updateGroupId(String id, String newGroupId) {
        VoiceRegion existing = regions.get(id);
        if (existing == null) return;
        VoiceRegion updated = new VoiceRegion(existing.id(), existing.groupName(), newGroupId, existing.cornerA(), existing.cornerB());
        regions.put(id, updated);
        save();
    }

    // --- internal logging helpers -------------------------------------------------
    private String ctx() { return mapId == null ? "GLOBAL" : mapId.toString(); }
    private static String fmt(String pattern, Object... args) {
        if (pattern == null) return "";
        StringBuilder sb = new StringBuilder(pattern.length() + 32);
        int argIndex = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '{' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    Object a = args[argIndex++];
                    sb.append(a == null ? "null" : a);
                } else {
                    sb.append("{}");
                }
                i++; // skip closing brace
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    private void logLoad(String code, String fmtPattern, Object... args) { String msg = "[VRM:" + code + ":" + ctx() + "] " + fmt(fmtPattern, args); golden.botc_mc.botc_mc.botc.LOGGER.info(msg); }
    private void logDebug(String code, String fmtPattern, Object... args) { String msg = "[VRM:" + code + ":" + ctx() + "] " + fmt(fmtPattern, args); golden.botc_mc.botc_mc.botc.LOGGER.debug(msg); }
    private void logWarn(String code, String fmtPattern, Object... args) { String msg = "[VRM:" + code + ":" + ctx() + "] " + fmt(fmtPattern, args); golden.botc_mc.botc_mc.botc.LOGGER.warn(msg); }

    // --- parsing and loading ----------------------------------------------------
    /**
     * Try to interpret a raw config string. Supports object-shaped config (with "voice" section)
     * or a plain array of regions. Returns true when regions were successfully parsed and loaded.
     */
    private boolean tryParseAndImport(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
            JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
            return parseRegionsFromVoiceSection(voiceSection);
        } else if (trimmed.startsWith("[")) {
            // legacy portable array form
            parseArray(trimmed);
            return !regions.isEmpty();
        }
        return false;
    }

    /**
     * Load strategy summary:
     * <p>1) If the explicit per-map config file (configPath) exists, parse it and return early.
     * <p>2) If a datapack override exists, read and parse its voice section.
     * <p>3) Fallback: attempt to read the embedded game JSON for the map (plasmid/game/&lt;path&gt;.json)
     *    and parse its voice section.
     *
     * <p>All parsed regions are added to the internal map; malformed entries are skipped.
     */
    private void load() {
        try {
            // Priority 1: explicit per-map config file (run/config/botc/voice/..)
            if (Files.exists(configPath)) {
                String s = new String(Files.readAllBytes(configPath));
                if (tryParseAndImport(s)) return;
            }

            // Datapack override & embedded resource fallback only
            if (world != null && mapId != null) {
                // Try datapack override file next
                Path override = VoiceRegionService.datapackOverrideGameFile(mapId);
                if (Files.exists(override)) {
                    String s = new String(Files.readAllBytes(override));
                    JsonObject obj = gson.fromJson(s, JsonObject.class);
                    JsonObject voiceSection = obj != null && obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                    if (parseRegionsFromVoiceSection(voiceSection)) { logLoad("OVERRIDE", "Loaded override regions count={}", regions.size()); }
                }
                // Embedded map JSON fallback (plasmid/game/<path>.json inside map asset)
                try {
                    Identifier resourceId = Identifier.of(mapId.getNamespace(), "plasmid/game/" + mapId.getPath() + ".json");
                    var optional = world.getServer().getResourceManager().getResource(resourceId);
                    if (optional.isPresent()) {
                        try (InputStream is = optional.get().getInputStream(); Reader r = new InputStreamReader(is)) {
                            JsonObject obj = gson.fromJson(r, JsonObject.class);
                            JsonObject voiceSection = obj != null && obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                            parseRegionsFromVoiceSection(voiceSection);
                            if (!regions.isEmpty()) { logLoad("EMBED", "Loaded embedded regions count={}", regions.size()); }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) { logWarn("LOAD-ERR", "load error {}", t.toString()); }
    }

    /**
     * Attempt to create a region from a JSON object and put it into the map.
     * Returns true when a region was successfully constructed and added.
     */
    private boolean addRegionFromJson(JsonObject o) {
        if (o == null) return false;
        String id = optString(o, "id");
        if (id == null) return false;
        String groupName = optString(o, "groupName");
        if (groupName == null) groupName = id; // fallback to id
        String groupId = optString(o, "groupId");
        BlockPos a = parseCorner(o.get("cornerA"));
        BlockPos b = parseCorner(o.get("cornerB"));
        if (a == null || b == null) return false;
        regions.put(id, new VoiceRegion(id, groupName, groupId, a, b));
        return true;
    }

    /**
     * Parse and load the 'voice_regions' array inside a voice section object.
     * Returns true when one or more regions were added.
     */
    private boolean parseRegionsFromVoiceSection(JsonObject voiceSection) {
        if (voiceSection == null || !voiceSection.has("voice_regions")) return false;
        try {
            var vrElem = voiceSection.get("voice_regions");
            if (vrElem != null && vrElem.isJsonArray()) {
                int added = 0;
                for (JsonElement el : vrElem.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    if (addRegionFromJson(el.getAsJsonObject())) added++;
                }
                if (added > 0) {
                    logDebug("PARSE-SECTION", "Parsed section regions added={}", added);
                    return true;
                }
            }
        } catch (Throwable t) {
            logWarn("PARSE-SECTION-ERR", "Error parsing voice section: {}", t.toString());
        }
        return false;
    }

    /**
     * Parse the simple array-of-objects format and add any valid regions found.
     * Kept for robustness when encountering portable map configs that embed only
     * an array (no top-level "voice" container).
     */
    private void parseArray(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return;
            int added = 0;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                if (addRegionFromJson(el.getAsJsonObject())) added++;
            }
            if (added > 0) logDebug("PARSE-ARRAY", "Parsed array regions added={}", added);
        } catch (Throwable t) {
            logWarn("PARSE-ARRAY-ERR", "Error parsing array: {}", t.toString());
        }
    }

    /**
     * Parse a corner object with x/y/z int fields. Returns null on parse failure.
     */
    private BlockPos parseCorner(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        try {
            int x = o.has("x") ? o.get("x").getAsInt() : 0;
            int y = o.has("y") ? o.get("y").getAsInt() : 0;
            int z = o.has("z") ? o.get("z").getAsInt() : 0;
            return new BlockPos(x, y, z);
        } catch (Throwable t) {
            return null;
        }
    }

    private String optString(JsonObject o, String key) {
        if (o == null || !o.has(key)) return null;
        try { return o.get(key).getAsString(); } catch (Throwable ignored) { return null; }
    }

    /**
     * Build the contained "voice" JSON section for writing files.
     * Preserves any existing top-level fields in the base object by attaching
     * (or replacing) a "voice" element containing the current state.
     */
    private JsonObject buildVoiceSection(JsonObject base) {
        JsonObject voiceSection = base != null && base.has("voice") && base.get("voice").isJsonObject() ? base.getAsJsonObject("voice") : new JsonObject();
        JsonElement regionsElem = gson.toJsonTree(new ArrayList<>(regions.values()));
        voiceSection.add("voice_regions", regionsElem);
        if (!voiceSection.has("voice_groups")) voiceSection.add("voice_groups", new com.google.gson.JsonArray());
        return voiceSection;
    }

    /**
     * Write the per-map config file, preserving unrelated JSON fields when possible. This
     * rewrites only the "voice" child of the root object to keep the rest of the map
     * JSON intact (authors, metadata, etc.).
     */
    private void writePerMapConfig() throws IOException {
        if (configPath.getParent() != null) Files.createDirectories(configPath.getParent());
        JsonObject root = new JsonObject();
        if (Files.exists(configPath)) {
            String raw = new String(Files.readAllBytes(configPath));
            if (raw.trim().startsWith("{")) {
                try { root = JsonParser.parseString(raw).getAsJsonObject(); } catch (Throwable ignored) {}
            }
        }
        JsonObject voiceSection = buildVoiceSection(root);
        root.add("voice", voiceSection);
        Files.write(configPath, gson.toJson(root).getBytes());
        logDebug("SAVE-CONFIG", "Wrote per-map config regions={}", regions.size());
    }

    /**
     * Write a datapack override file mirroring the map's game JSON so regions can be bundled with
     * a world. This method attempts to preserve existing content in the target file when present,
     * but will create a new JSON structure if necessary.
     */
    private void writeOverrideDatapack() throws IOException {
        if (world == null || mapId == null) return;
        Path datapackBase = VoiceRegionService.datapackOverrideBase();
        Path target = VoiceRegionService.datapackOverrideGameFile(mapId);
        Files.createDirectories(target.getParent());
        JsonObject obj = null;
        if (Files.exists(target)) {
            String existing = new String(Files.readAllBytes(target));
            obj = gson.fromJson(existing, JsonObject.class);
        } else {
            try {
                Identifier resourceId = Identifier.of(mapId.getNamespace(), "plasmid/game/" + mapId.getPath() + ".json");
                var optional = world.getServer().getResourceManager().getResource(resourceId);
                if (optional.isPresent()) {
                    try (InputStream is = optional.get().getInputStream(); Reader r = new InputStreamReader(is)) {
                        obj = gson.fromJson(r, JsonObject.class);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (obj == null) obj = new JsonObject();
        JsonObject voiceSection = buildVoiceSection(obj);
        obj.add("voice", voiceSection);
        Files.write(target, gson.toJson(obj).getBytes());
        VoiceRegionService.ensureOverridesPackMeta(datapackBase, "BOTC overrides datapack");
        logDebug("SAVE-OVERRIDE", "Wrote override datapack regions={}", regions.size());
    }

    /**
     * Persist the current region list to disk.
     * Writes both the per-map config and a datapack override so the data can be portable.
     */
    public void save() {
        try { writePerMapConfig(); } catch (IOException ex) { logWarn("SAVE-CONFIG-ERR", "Per-map save failed: {}", ex.toString()); }
        try { writeOverrideDatapack(); } catch (IOException ex) { logWarn("SAVE-OVERRIDE-ERR", "Override save failed: {}", ex.toString()); }
    }
}
