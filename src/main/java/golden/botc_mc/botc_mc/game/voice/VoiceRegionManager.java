package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceRegionManager {
    private final Map<String, VoiceRegion> regions = new ConcurrentHashMap<>();
    private final Path configPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // Debug flag (can be toggled at runtime via command later if needed)
    private static volatile boolean DEBUG_REGIONS = false;

    // Added public accessor/mutator for debug flag
    public static void setDebugRegions(boolean enabled) { DEBUG_REGIONS = enabled; }
    public static boolean isDebugRegions() { return DEBUG_REGIONS; }

    public VoiceRegionManager(Path configPath) {
        this.configPath = configPath;
        this.load();
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
                    golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionManager: player {} in region {} group={} bounds={}", player.getName().getString(), r.id, r.groupName, r.boundsDebug());
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
            if (!Files.exists(configPath)) return;
            String s = new String(Files.readAllBytes(configPath));
            Type type = new TypeToken<List<VoiceRegion>>(){}.getType();
            List<VoiceRegion> list = gson.fromJson(s, type);
            if (list != null) for (VoiceRegion r : list) regions.put(r.id, r);
        } catch (Exception ex) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager load failed: {}", ex.toString());
        }
    }

    private void save() {
        try {
            if (configPath.getParent() != null) Files.createDirectories(configPath.getParent());
            List<VoiceRegion> list = new ArrayList<>(regions.values());
            String s = gson.toJson(list);
            Files.write(configPath, s.getBytes());
        } catch (IOException ex) { golden.botc_mc.botc_mc.botc.LOGGER.warn("VoiceRegionManager save failed: {}", ex.toString()); }
    }
}
