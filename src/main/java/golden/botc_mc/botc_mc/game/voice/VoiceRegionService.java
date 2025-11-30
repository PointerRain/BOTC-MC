package golden.botc_mc.botc_mc.game.voice;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/** Voice region path utilities and active manager tracking. */
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

    /** BOTC config root path.
     * @return path to {@code gameDir/config/botc}
     */
    public static Path botcConfigRoot() {
        return configRoot().resolve("botc");
    }

    /** Per-map config path for voice JSON.
     * @param mapId map identifier
     * @return path to config json
     */
    public static Path configPathForMap(Identifier mapId) {
        String ns = mapId.getNamespace();
        String path = mapId.getPath();
        // New layout: {@code gameDir/config/botc/voice/<ns>/<path>.json}
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
     * @param overwrite true to replace existing file if present
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
            boolean needs = !Files.exists(target) || Files.size(target)==0;
            if (!needs) {
                String raw = new String(Files.readAllBytes(target));
                String trimmed = raw.trim();
                if (trimmed.isEmpty() || trimmed.equals("{}") || trimmed.equals("[]")) needs = true;
            }
            if (needs) copyDefault(mapId, true);
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.debug("writeDefaultConfigIfMissing error {}", t.toString());
        }
    }

    /**
     * Register a {@link VoiceRegionManager} as the globally active manager.
     * @param manager manager instance (may be null to clear)
     */
    public static synchronized void setActive(VoiceRegionManager manager) {
        activeManager = manager;
    }

    /** Currently active manager accessor.
     * @return active VoiceRegionManager or null if none set
     */
    public static VoiceRegionManager getActiveManager() { return activeManager; }

    /** Ensure a minimal pack.mcmeta exists for the overrides datapack.
     * @param base overrides datapack base directory
     * @param desc pack description text
     */
    public static void ensureOverridesPackMeta(Path base, String desc) {
        try {
            Path packMeta = base.resolve("pack.mcmeta");
            if (Files.exists(packMeta)) return;
            com.google.gson.JsonObject pack = new com.google.gson.JsonObject();
            com.google.gson.JsonObject inner = new com.google.gson.JsonObject();
            inner.addProperty("pack_format", 12);
            inner.addProperty("description", desc == null ? "BOTC overrides" : desc);
            pack.add("pack", inner);
            Files.createDirectories(base);
            Files.write(packMeta, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(pack).getBytes());
        } catch (Throwable t) {
            golden.botc_mc.botc_mc.botc.LOGGER.debug("ensureOverridesPackMeta error {}", t.toString());
        }
    }
}