package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
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
        int before = regions.size();
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

    public boolean updateGroupId(String id, String groupId) {
        VoiceRegion r = regions.get(id);
        if (r == null) return false;
        VoiceRegion updated = new VoiceRegion(r.id, r.groupName, groupId, r.cornerA, r.cornerB);
        regions.put(id, updated);
        save();
        return true;
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
            // Priority 1: explicit per-map config file (run/config/voice_regions/...)
            if (Files.exists(configPath)) {
                String s = new String(Files.readAllBytes(configPath));
                Type type = new TypeToken<List<VoiceRegion>>(){}.getType();
                List<VoiceRegion> list = gson.fromJson(s, type);
                if (list != null) for (VoiceRegion r : list) regions.put(r.id, r);
                return;
            }
            // Global fallback for per-map managers: if map-specific file missing, import legacy global file
            if (mapId != null) {
                Path global = VoiceRegionService.legacyGlobalConfigPath();
                if (Files.exists(global)) {
                    try {
                        String s = new String(Files.readAllBytes(global));
                        Type type = new TypeToken<List<VoiceRegion>>(){}.getType();
                        List<VoiceRegion> list = gson.fromJson(s, type);
                        if (list != null && !list.isEmpty()) {
                            for (VoiceRegion r : list) regions.put(r.id, r);
                            golden.botc_mc.botc_mc.botc.LOGGER.info("VoiceRegionManager: imported {} global region(s) into map '{}'", list.size(), mapId);
                            // Persist immediately to create the per-map file so subsequent loads are map-scoped
                            save();
                            return;
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
                    Type type = new TypeToken<List<VoiceRegion>>(){}.getType();
                    List<VoiceRegion> list = gson.fromJson(s, type);
                    if (list != null) for (VoiceRegion r : list) regions.put(r.id, r);
                    golden.botc_mc.botc_mc.botc.LOGGER.info("Loaded voice regions from legacy double-run path fallback.");
                    return;
                }
            }

            // Priority 2: world datapack override (run/world/datapacks/botc_overrides/...)
            if (world != null && mapId != null) {
                Path override = VoiceRegionService.datapackOverrideGameFile(mapId);
                if (Files.exists(override)) {
                    String s = new String(Files.readAllBytes(override));
                    JsonObject obj = gson.fromJson(s, JsonObject.class);
                    if (obj != null && obj.has("voice_regions")) {
                        JsonElement vr = obj.get("voice_regions");
                        Type type = new TypeToken<List<VoiceRegion>>(){}.getType();
                        List<VoiceRegion> list = gson.fromJson(vr, type);
                        if (list != null) for (VoiceRegion r : list) regions.put(r.id, r);
                        return;
                    }
                }

                // Priority 3: embedded resource (mod datapack) — read from server resource manager
                try {
                    Identifier resourceId = Identifier.of(mapId.getNamespace(), "plasmid/game/" + mapId.getPath() + ".json");
                    var optional = world.getServer().getResourceManager().getResource(resourceId);
                    if (optional.isPresent()) {
                        try (InputStream is = optional.get().getInputStream(); Reader r = new InputStreamReader(is)) {
                            JsonObject obj = gson.fromJson(r, JsonObject.class);
                            if (obj != null && obj.has("voice_regions")) {
                                JsonElement vr = obj.get("voice_regions");
                                Type type = new TypeToken<List<VoiceRegion>>(){}.getType();
                                List<VoiceRegion> list = gson.fromJson(vr, type);
                                if (list != null) for (VoiceRegion reg : list) regions.put(reg.id, reg);
                                return;
                            }
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

    public void save() {
        // Save to per-map config first (backwards compatible)
        try {
            if (configPath.getParent() != null) Files.createDirectories(configPath.getParent());
            List<VoiceRegion> list = new ArrayList<>(regions.values());
            String s = gson.toJson(list);
            Files.write(configPath, s.getBytes());
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
                // Ensure type and map_id are present
                if (!obj.has("type")) obj.addProperty("type", "botc-mc:game");
                obj.addProperty("map_id", mapId.toString());

                JsonElement regionsElem = gson.toJsonTree(new ArrayList<>(regions.values()), new TypeToken<List<VoiceRegion>>(){}.getType());
                obj.add("voice_regions", regionsElem);

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