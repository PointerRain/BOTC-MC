package golden.botc_mc.botc_mc.game.voice;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Static service utilities for locating, migrating, and tracking active voice region managers.
 * <p>
 * At a high level this class:
 * <ul>
 *   <li>Defines the canonical BOTC config root (under the Minecraft {@code config/} directory).</li>
 *   <li>Exposes helper methods for per-map voice JSON paths and legacy migration.</li>
 *   <li>Tracks the currently "active" {@link VoiceRegionManager} and its associated world + map id
 *       so that runtime tasks (like {@link VoiceRegionTask}) can query it without additional wiring.</li>
 * </ul>
 */
public final class VoiceRegionService {
    private static volatile VoiceRegionManager activeManager;

    private VoiceRegionService() {}

    /**
     * @return Absolute path to the Minecraft game directory as seen by Fabric.
     */
    private static Path gameDir() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    /**
     * Root {@code config/} folder inside the Minecraft game directory.
     */
    private static Path configRoot() {
        return gameDir().resolve("config");
    }

    /** Root BOTC configuration path.
     * @return {@code <gameDir>/config/botc} path
     */
    public static Path botcConfigRoot() {
        return configRoot().resolve("botc");
    }

    /** Legacy global regions config path.
     * @return legacy file path
     */
    public static Path legacyGlobalConfigPath() {
        // legacy location for global regions (kept for migration)
        return configRoot().resolve("voice_regions.json");
    }

    /**
     * Attempt migration from a historical double-run path ({@code gameDir/run/config/voice_regions.json})
     * to the new canonical location if a file is not already present there.
     */
    public static void migrateLegacyGlobalRegionsIfNeeded() {
        try {
            Path target = legacyGlobalConfigPath();
            if (!java.nio.file.Files.exists(target)) {
                Path legacy = gameDir().resolve(Paths.get("run","config","voice_regions.json"));
                if (java.nio.file.Files.exists(legacy)) {
                    java.nio.file.Files.createDirectories(target.getParent());
                    java.nio.file.Files.copy(legacy, target);
                    golden.botc_mc.botc_mc.botc.LOGGER.info("Migrated legacy voice_regions.json from double-run path.");
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Per-map config path for voice JSON.
     * @param mapId map identifier
     * @return path to config json
     */
    public static Path configPathForMap(Identifier mapId) {
        String ns = mapId.getNamespace();
        String path = mapId.getPath();
        // New layout: <gameDir>/config/botc/voice/<ns>/<path>.json
        return botcConfigRoot().resolve(Paths.get("voice", ns, path + ".json"));
    }

    /** Overrides datapack base directory.
     * @return path to overrides datapack root
     */
    public static Path datapackOverrideBase() {
        return gameDir().resolve(Paths.get("world", "datapacks", "botc_overrides"));
    }

    /** Override game json location.
     * @param mapId map identifier
     * @return path within overrides datapack
     */
    public static Path datapackOverrideGameFile(Identifier mapId) {
        return datapackOverrideBase().resolve(Paths.get("data", mapId.getNamespace(), "plasmid", "game", mapId.getPath() + ".json"));
    }

    /** Copy bundled default config.
     * @param mapId map identifier
     * @param overwrite true to replace existing file
     */
    public static void copyDefault(Identifier mapId, boolean overwrite) {
        String res = String.format("assets/%s/voice_defaults/%s.json", mapId.getNamespace(), mapId.getPath());
        try (InputStream in = VoiceRegionService.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) return;
            Path target = configPathForMap(mapId);
            Files.createDirectories(target.getParent());
            if (!overwrite && Files.exists(target)) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            in.transferTo(baos);
            Files.write(target, baos.toByteArray());
            golden.botc_mc.botc_mc.botc.LOGGER.info("Copied default voice config for {} to {}", mapId, target);
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("copyDefault failed for {}: {}", mapId, t.toString());
        }
    }

    /** Write default config if missing/empty.
     * @param mapId map identifier
     */
    public static void writeDefaultConfigIfMissing(Identifier mapId) {
        try {
            Path target = configPathForMap(mapId);
            boolean needs = !Files.exists(target);
            if (!needs) {
                long size = Files.size(target);
                if (size == 0) needs = true; else {
                    String raw = new String(Files.readAllBytes(target));
                    String trimmed = raw.trim();
                    if (trimmed.isEmpty() || trimmed.equals("{}") || trimmed.equals("[]")) needs = true;
                }
            }
            if (!needs) return;
            copyDefault(mapId, true);
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("Failed to write default voice config for {}: {}", mapId, t.toString());
        }
    }

    /**
     * Register a {@link VoiceRegionManager} as the globally active manager for a particular world + map.
     * The active manager is what runtime systems will operate on when adjusting player voice membership.
     * @param manager manager instance
     */
    public static synchronized void setActive(VoiceRegionManager manager) {
        activeManager = manager;
    }

    /** Currently active manager accessor.
     * @return active VoiceRegionManager or null
     */
    public static VoiceRegionManager getActiveManager() { return activeManager; }

    /** Ensure a minimal pack.mcmeta exists for the overrides datapack.
     * Centralizes creation logic used by both VoiceGroupManager and VoiceRegionManager.
     * @param datapackBase base path of overrides datapack (run/world/datapacks/botc_overrides)
     * @param description human readable description
     */
    public static void ensureOverridesPackMeta(Path datapackBase, String description) {
        try {
            Path packMeta = datapackBase.resolve("pack.mcmeta");
            if (java.nio.file.Files.exists(packMeta)) return;
            com.google.gson.JsonObject pack = new com.google.gson.JsonObject();
            com.google.gson.JsonObject inner = new com.google.gson.JsonObject();
            inner.addProperty("pack_format", 12); // 1.21.x
            inner.addProperty("description", description == null ? "BOTC overrides" : description);
            pack.add("pack", inner);
            java.nio.file.Files.createDirectories(datapackBase);
            java.nio.file.Files.write(packMeta, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(pack).getBytes());
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.warn("ensureOverridesPackMeta failed: {}", t.toString());
        }
    }
}