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

public class VoiceRegionManager {
    private final Map<String, VoiceRegion> regions = new ConcurrentHashMap<>();
    private final Path configPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // Debug flag (can be toggled at runtime via command later if needed)
    private static volatile boolean DEBUG_REGIONS = false;

    private final Identifier mapId; // optional map association
    private final ServerWorld world; // optional world association

    // Added public accessor/mutator for debug flag
    public static void setDebugRegions(boolean enabled) { DEBUG_REGIONS = enabled; }
    public static boolean isDebugRegions() { return DEBUG_REGIONS; }

    public static VoiceRegionManager forMap(ServerWorld world, Identifier mapId) {
        try { VoiceRegionService.writeDefaultConfigIfMissing(mapId); } catch (Throwable ignored) {}
        Path path = VoiceRegionService.configPathForMap(mapId);
        return new VoiceRegionManager(path, world, mapId);
    }

    public VoiceRegionManager(Path configPath) {
        this(configPath, null, null);
    }

    public VoiceRegionManager(Path configPath, ServerWorld world, Identifier mapId) {
        this.configPath = configPath;
        this.world = world;
        this.mapId = mapId;
        this.load();
    }

    /** Returns the active map id this manager is tied to (may be null for legacy). */
    public Identifier getMapId() { return mapId; }

    /** Returns the world this manager is monitoring (may be null for legacy). */
    public ServerWorld getWorld() { return world; }

    /** Returns the config path this manager is using. */
    public Path getConfigPath() { return configPath; }

    /** Reload regions from disk (clears current set). */
    public synchronized int reload() {
        regions.clear();
        load();
        return regions.size();
    }

    /**
     * Returns the region the player is currently in (first match) or null.
     * Uses block coordinates to avoid floating precision boundary glitches.
     */
    public VoiceRegion regionForPlayer(ServerPlayerEntity player) {
        int bx = player.getBlockX();
        int by = player.getBlockY();
        int bz = player.getBlockZ();
        for (VoiceRegion r : regions.values()) {
            if (r.containsBlock(bx, by, bz)) {
                if (DEBUG_REGIONS) {
                    golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionManager: player {} in region {} group={} bounds= {}", player.getName().getString(), r.id, r.groupName, r.boundsDebug());
                }
                return r;
            }
        }
        return null;
    }

    public Collection<VoiceRegion> list() { return regions.values(); }

    public VoiceRegion get(String id) { return regions.get(id); }

    public VoiceRegion create(String id, String groupName, String groupId, BlockPos a, BlockPos b) {
        VoiceRegion r = new VoiceRegion(id, groupName, groupId, a, b);
        regions.put(id, r);
        save();
        return r;
    }


    public VoiceRegion remove(String id) {
        VoiceRegion r = regions.remove(id);
        save();
        return r;
    }

    /**
     * Remove all regions and persist the empty configuration.
     * @return number of regions removed
     */
    public int clearAll() {
        int count = regions.size();
        regions.clear();
        save();
        return count;
    }

    private void load() {
        try {
            // Priority 1: explicit per-map config file (run/config/botc/voice/..)
            if (Files.exists(configPath)) {
                String s = new String(Files.readAllBytes(configPath));
                String trimmed = s.trim();
                if (trimmed.startsWith("{")) {
                    JsonObject obj = JsonParser.parseString(s).getAsJsonObject();
                    JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                    parseRegionsFromVoiceSection(voiceSection);
                    return;
                } else if (trimmed.startsWith("[")) {
                    // Legacy flat array
                    parseLegacyArray(s);
                    return;
                }
            }

            // Global fallback for per-map managers: if map-specific file missing, import legacy global file
            if (mapId != null) {
                Path global = VoiceRegionService.legacyGlobalConfigPath();
                if (Files.exists(global)) {
                    try {
                        String s = new String(Files.readAllBytes(global));
                        String trimmed = s.trim();
                        if (trimmed.startsWith("{")) {
                            JsonObject obj = JsonParser.parseString(s).getAsJsonObject();
                            JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                            if (parseRegionsFromVoiceSection(voiceSection)) {
                                golden.botc_mc.botc_mc.botc.LOGGER.info("VoiceRegionManager: imported {} global region(s) into map '{}'", regions.size(), mapId);
                                save();
                                return;
                            }
                        } else if (trimmed.startsWith("[")) {
                            parseLegacyArray(s);
                            if (!regions.isEmpty()) {
                                golden.botc_mc.botc_mc.botc.LOGGER.info("VoiceRegionManager: imported {} global region(s) into map '{}'", regions.size(), mapId);
                                save();
                                return;
                            }
                        }
                    } catch (Throwable t) {
                        golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager: failed importing global regions for map {}: {}", mapId, t.toString());
                    }
                }
            }

            // Fallback: legacy double-run path (gameDir/run/config/voice_regions.json) for global manager only
            if (mapId == null) {
                Path legacy = FabricLoader.getInstance().getGameDir().resolve(Paths.get("run","config","voice_regions.json"));
                if (Files.exists(legacy)) {
                    String s = new String(Files.readAllBytes(legacy));
                    String trimmed = s.trim();
                    if (trimmed.startsWith("{")) {
                        JsonObject obj = JsonParser.parseString(s).getAsJsonObject();
                        JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : obj;
                        parseRegionsFromVoiceSection(voiceSection);
                        golden.botc_mc.botc_mc.botc.LOGGER.info("Loaded voice regions from legacy double-run path fallback.");
                        return;
                    } else if (trimmed.startsWith("[")) {
                        parseLegacyArray(s);
                        golden.botc_mc.botc_mc.botc.LOGGER.info("Loaded voice regions from legacy double-run path fallback.");
                        return;
                    }
                }
            }

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


    public void save() {
        // Save to per-map config first (backwards compatible)
        try {
            if (configPath.getParent() != null) Files.createDirectories(configPath.getParent());
            // Start with existing object if present to preserve voice_groups
            JsonObject root = new JsonObject();
            if (Files.exists(configPath)) {
                String raw = new String(Files.readAllBytes(configPath));
                if (raw.trim().startsWith("{")) {
                    try { root = JsonParser.parseString(raw).getAsJsonObject(); } catch (Throwable ignored) {}
                }
            }
            // Ensure 'voice' section exists and write regions there
            JsonObject voiceSection = root.has("voice") && root.get("voice").isJsonObject() ? root.getAsJsonObject("voice") : new JsonObject();
            JsonElement regionsElem = gson.toJsonTree(new ArrayList<>(regions.values()));
            voiceSection.add("voice_regions", regionsElem);
            // Preserve voice_groups if already present inside voice section
            if (!voiceSection.has("voice_groups")) voiceSection.add("voice_groups", new com.google.gson.JsonArray());
            root.add("voice", voiceSection);
            String out = gson.toJson(root);
            Files.write(configPath, out.getBytes());
        } catch (IOException ex) { golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager save failed: {}", ex.toString()); }

        // Also write an override datapack entry so the regions are stored alongside the map JSON for portability
        if (world != null && mapId != null) {
            Path datapackBase = VoiceRegionService.datapackOverrideBase();
            Path target = VoiceRegionService.datapackOverrideGameFile(mapId);
            try {
                Files.createDirectories(target.getParent());

                // Try to preserve existing content from the target or embedded resource
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
                // Write voice section only (avoid writing type/map_id to prevent creating phantom maps)
                JsonObject voiceSection = obj.has("voice") && obj.get("voice").isJsonObject() ? obj.getAsJsonObject("voice") : new JsonObject();
                JsonElement regionsElem = gson.toJsonTree(new ArrayList<>(regions.values()));
                voiceSection.add("voice_regions", regionsElem);
                if (!voiceSection.has("voice_groups")) voiceSection.add("voice_groups", new com.google.gson.JsonArray());
                obj.add("voice", voiceSection);

                String out = gson.toJson(obj);
                Files.write(target, out.getBytes());

                // Ensure a minimal pack.mcmeta so Minecraft will treat this as a datapack
                Path packMeta = datapackBase.resolve("pack.mcmeta");
                if (!Files.exists(packMeta)) {
                    JsonObject pack = new JsonObject();
                    JsonObject inner = new JsonObject();
                    // pack_format 12 corresponds to 1.21.x, adjust in future if needed
                    inner.addProperty("pack_format", 12);
                    inner.addProperty("description", "BOTC overrides datapack");
                    pack.add("pack", inner);
                    Files.write(packMeta, gson.toJson(pack).getBytes());
                }
            } catch (IOException e) { golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager override datapack save failed: {}", e.toString()); }
        }
    }
}
