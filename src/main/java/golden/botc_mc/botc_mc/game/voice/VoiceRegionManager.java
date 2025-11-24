package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
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
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and manages voice chat regions for a specific map or global scope.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Reads region definitions from multiple possible locations (per-map config under {@code config/botc},
 *       legacy global files, and optional datapack overrides / embedded JSON in a map's game file).</li>
 *   <li>Normalizes the data into {@link VoiceRegion} records and answers fast spatial queries such as
 *       {@link #regionForPlayer(net.minecraft.server.network.ServerPlayerEntity)}.</li>
 *   <li>Persists the active region set back to both the primary config file and a portable datapack override
 *       so voice regions can travel with a world or map.</li>
 * </ul>
 * The manager is intentionally tolerant of missing or malformed data: it logs and skips bad entries instead
 * of failing hard, so a misconfigured map cannot bring down the whole server.
 */
public class VoiceRegionManager {
    private final Map<String, VoiceRegion> regions = new ConcurrentHashMap<>();
    private final Path configPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // Debug flag (can be toggled at runtime via command later if needed)
    private static final boolean DEBUG_REGIONS = false;

    private final Identifier mapId; // optional map association
    private final ServerWorld world; // optional world association

    /** Manager bound to map &amp; world, auto-writing default if missing.
     * @param world server world
     * @param mapId map identifier
     * @return new manager instance
     */
    public static VoiceRegionManager forMap(ServerWorld world, Identifier mapId) {
        try { VoiceRegionService.writeDefaultConfigIfMissing(mapId); } catch (Throwable ignored) {}
        Path path = VoiceRegionService.configPathForMap(mapId);
        return new VoiceRegionManager(path, world, mapId);
    }

    /** Construct legacy/global manager.
     * @param configPath path to config file
     */
    public VoiceRegionManager(Path configPath) {
        this(configPath, null, null);
    }

    /** Construct full manager.
     * @param configPath config file path
     * @param world optional world
     * @param mapId optional map id
     */
    public VoiceRegionManager(Path configPath, ServerWorld world, Identifier mapId) {
        this.configPath = configPath;
        this.world = world;
        this.mapId = mapId;
        this.load();
    }

    /** Map id this manager targets.
     * @return map identifier or null
     */
    public Identifier getMapId() { return mapId; }

    /** World this manager is attached to.
     * @return ServerWorld or null
     */
    public ServerWorld getWorld() { return world; }

    /** Config path backing this manager.
     * @return path to JSON
     */
    public Path getConfigPath() { return configPath; }

    /**
     * Reload regions from disk, returning new count.
     * @return count of loaded regions
     */
    public synchronized int reload() {
        regions.clear();
        load();
        return regions.size();
    }

    /** Determine region for player.
     * @param player server player
     * @return region or null
     */
    public VoiceRegion regionForPlayer(ServerPlayerEntity player) {
        int bx = player.getBlockX();
        int by = player.getBlockY();
        int bz = player.getBlockZ();
        for (VoiceRegion r : regions.values()) {
            if (r.containsBlock(bx, by, bz)) {
                if (DEBUG_REGIONS) {
                    golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionManager: player {} in region {} group={} bounds= {}", player.getName().getString(), r.id(), r.groupName(), r.boundsDebug());
                }
                return r;
            }
        }
        return null;
    }

    /** List all regions.
     * @return collection view of regions
     */
    public Collection<VoiceRegion> list() { return regions.values(); }

    /** Get region by id.
     * @param id region identifier
     * @return VoiceRegion or null
     */
    public VoiceRegion get(String id) { return regions.get(id); }

    /** Create and persist new region.
     * @param id region identifier
     * @param groupName voice group name
     * @param groupId initial voice group id (optional)
     * @param a corner A
     * @param b corner B
     * @return created region
     */
    public VoiceRegion create(String id, String groupName, String groupId, BlockPos a, BlockPos b) {
        VoiceRegion r = new VoiceRegion(id, groupName, groupId, a, b);
        regions.put(id, r);
        save();
        return r;
    }

    /** Update group id for region.
     * @param id region id
     * @param newGroupId new group UUID string
     * @return true if update applied
     */
    public boolean updateGroupId(String id, String newGroupId) {
        VoiceRegion existing = regions.get(id);
        if (existing == null) return false;
        // Recreate region with new groupId
        VoiceRegion updated = new VoiceRegion(existing.id(), existing.groupName(), newGroupId, existing.cornerA(), existing.cornerB());
        regions.put(id, updated);
        save();
        return true;
    }

    private boolean tryParseAndImport(String raw, Identifier mapIdContext, boolean saveAfter) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
            JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
            if (parseRegionsFromVoiceSection(voiceSection)) {
                if (saveAfter) save();
                if (mapIdContext != null) golden.botc_mc.botc_mc.botc.LOGGER.info("VoiceRegionManager: imported {} global region(s) into map '{}'", regions.size(), mapIdContext);
                return true;
            }
        } else if (trimmed.startsWith("[")) {
            parseLegacyArray(trimmed);
            if (!regions.isEmpty()) {
                if (saveAfter) save();
                if (mapIdContext != null) golden.botc_mc.botc_mc.botc.LOGGER.info("VoiceRegionManager: imported {} global region(s) into map '{}'", regions.size(), mapIdContext);
                return true;
            }
        }
        return false;
    }

    private boolean importGlobalLegacyIfPossible() {
        if (mapId == null) return false;
        Path global = VoiceRegionService.legacyGlobalConfigPath();
        if (!Files.exists(global)) return false;
        try {
            String s = new String(Files.readAllBytes(global));
            return tryParseAndImport(s, mapId, true);
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager: failed importing global regions for map {}: {}", mapId, t.toString());
            return false;
        }
    }

    private boolean importLegacyDoubleRunIfPossible() {
        if (mapId != null) return false; // only for global manager mode
        Path legacy = FabricLoader.getInstance().getGameDir().resolve(Paths.get("run","config","voice_regions.json"));
        if (!Files.exists(legacy)) return false;
        try {
            String s = new String(Files.readAllBytes(legacy));
            boolean imported = tryParseAndImport(s, null, false);
            if (imported) golden.botc_mc.botc_mc.botc.LOGGER.info("Loaded voice regions from legacy double-run path fallback.");
            return imported;
        } catch (Throwable ignored) { return false; }
    }

    /**
     * Internal loader that chooses the best available source based on the current {@link #configPath},
     * {@link #mapId} and {@link #world}:
     * <ol>
     *   <li>Explicit per-map config under {@code config/botc/voice/&lt;ns&gt;/&lt;path&gt;.json}.</li>
     *   <li>Legacy global file (optionally imported into per-map config for migration).</li>
     *   <li>Legacy double-run path for older setups.</li>
     *   <li>Datapack override and then embedded JSON inside the map's game definition.</li>
     * </ol>
     * Any valid regions discovered are accumulated into {@link #regions}.
     */
    private void load() {
        try {
            // Priority 1: explicit per-map config file (run/config/botc/voice/..)
            if (Files.exists(configPath)) {
                String s = new String(Files.readAllBytes(configPath));
                if (tryParseAndImport(s, null, false)) return;
            }

            // Global fallback for per-map managers: if map-specific file missing, import legacy global file
            if (importGlobalLegacyIfPossible()) return;

            // Fallback: legacy double-run path (gameDir/run/config/voice_regions.json) for global manager only
            if (importLegacyDoubleRunIfPossible()) return;

            // Priority 2: world datapack override (run/world/datapacks/botc_overrides/...)
            if (world != null && mapId != null) {
                Path override = VoiceRegionService.datapackOverrideGameFile(mapId);
                if (Files.exists(override)) {
                    String s = new String(Files.readAllBytes(override));
                    JsonObject obj = gson.fromJson(s, JsonObject.class);
                    JsonObject voiceSection = obj != null && obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                    if (parseRegionsFromVoiceSection(voiceSection)) return;
                }
                try {
                    Identifier resourceId = Identifier.of(mapId.getNamespace(), "plasmid/game/" + mapId.getPath() + ".json");
                    var optional = world.getServer().getResourceManager().getResource(resourceId);
                    if (optional.isPresent()) {
                        try (InputStream is = optional.get().getInputStream(); Reader r = new InputStreamReader(is)) {
                            JsonObject obj = gson.fromJson(r, JsonObject.class);
                            JsonObject voiceSection = obj != null && obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                            if (parseRegionsFromVoiceSection(voiceSection)) return;
                        }
                    }
                } catch (Exception ex) {
                    golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionManager: failed to read embedded map game json: {}", ex.toString());
                }
            }

            // No explicit data found — leave empty
        } catch (Exception ex) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager load failed: {}", ex.toString());
        }
    }

    private boolean parseRegionsFromVoiceSection(JsonObject voiceSection) {
        if (voiceSection == null || !voiceSection.has("voice_regions")) return false;
        try {
            var vrElem = voiceSection.get("voice_regions");
            if (vrElem != null && vrElem.isJsonArray()) {
                int added = 0;
                for (JsonElement el : vrElem.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String id = optString(o, "id");
                    if (id == null) continue;
                    String groupName = optString(o, "groupName");
                    if (groupName == null) groupName = id; // fallback
                    String groupId = optString(o, "groupId");
                    BlockPos a = parseCorner(o.get("cornerA"));
                    BlockPos b = parseCorner(o.get("cornerB"));
                    if (a == null || b == null) continue;
                    regions.put(id, new VoiceRegion(id, groupName, groupId, a, b));
                    added++;
                }
                if (added > 0) {
                    golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionManager: loaded {} region(s) from voice section", added);
                    return true;
                }
            }
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager: parseRegionsFromVoiceSection error: {}", t.toString());
        }
        return false;
    }

    private void parseLegacyArray(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return;
            int added = 0;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String id = optString(o, "id");
                if (id == null) continue;
                String groupName = optString(o, "groupName");
                if (groupName == null) groupName = id;
                String groupId = optString(o, "groupId");
                BlockPos a = parseCorner(o.get("cornerA"));
                BlockPos b = parseCorner(o.get("cornerB"));
                if (a == null || b == null) continue;
                regions.put(id, new VoiceRegion(id, groupName, groupId, a, b));
                added++;
            }
            if (added > 0) golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionManager: loaded {} legacy array region(s)", added);
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager: parseLegacyArray error: {}", t.toString());
        }
    }

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

    private JsonObject buildVoiceSection(JsonObject base) {
        JsonObject voiceSection = base != null && base.has("voice") && base.get("voice").isJsonObject() ? base.getAsJsonObject("voice") : new JsonObject();
        JsonElement regionsElem = gson.toJsonTree(new ArrayList<>(regions.values()));
        voiceSection.add("voice_regions", regionsElem);
        if (!voiceSection.has("voice_groups")) voiceSection.add("voice_groups", new com.google.gson.JsonArray());
        return voiceSection;
    }

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
    }

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
    }

    /**
     * Persist the current region list.
     * <p>
     * Two write targets are used:
     * <ul>
     *   <li>The primary per-map config file under {@code config/botc/voice/...} (backwards-compatible)</li>
     *   <li>A world datapack override that mirrors the map's game JSON, ensuring regions are bundled
     *       with the world so they can be moved between servers.</li>
     * </ul>
     * Existing JSON in both places is preserved as much as possible; only the {@code voice.voice_regions}
     * section is rewritten.
     */
    public void save() {
        try { writePerMapConfig(); } catch (IOException ex) { golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager save failed: {}", ex.toString()); }
        try { writeOverrideDatapack(); } catch (IOException ex) { golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager override datapack save failed: {}", ex.toString()); }
    }
}
