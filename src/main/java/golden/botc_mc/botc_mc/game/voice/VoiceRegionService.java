package golden.botc_mc.botc_mc.game.voice;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public final class VoiceRegionService {
    private static volatile VoiceRegionManager activeManager;
    private static volatile Identifier activeMapId;
    private static volatile ServerWorld activeWorld;

    private VoiceRegionService() {}

    private static Path gameDir() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    private static Path configRoot() {
        return gameDir().resolve("config");
    }

    /** BOTC configuration root: <gameDir>/config/botc */
    public static Path botcConfigRoot() {
        return configRoot().resolve("botc");
    }

    public static Path legacyGlobalConfigPath() {
        // legacy location for global regions (kept for migration)
        return configRoot().resolve("voice_regions.json");
    }

    /** Attempt migration from legacy double-run path (gameDir/run/config/voice_regions.json) if current file missing. */
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

    public static Path configPathForMap(Identifier mapId) {
        String ns = mapId.getNamespace();
        String path = mapId.getPath();
        // New layout: <gameDir>/config/botc/voice/<ns>/<path>.json
        return botcConfigRoot().resolve(Paths.get("voice", ns, path + ".json"));
    }

    public static Path datapackOverrideBase() {
        return gameDir().resolve(Paths.get("world", "datapacks", "botc_overrides"));
    }

    public static Path datapackOverrideGameFile(Identifier mapId) {
        return datapackOverrideBase().resolve(Paths.get("data", mapId.getNamespace(), "plasmid", "game", mapId.getPath() + ".json"));
    }

    /** Returns true if a bundled default asset exists for the given map id. */
    public static boolean defaultAssetExists(Identifier mapId) {
        String res = String.format("assets/%s/voice_defaults/%s.json", mapId.getNamespace(), mapId.getPath());
        try (InputStream in = VoiceRegionService.class.getClassLoader().getResourceAsStream(res)) {
            return in != null;
        } catch (Throwable ignored) { return false; }
    }

    /** Copy bundled default to per-map config. overwrite==true replaces existing file. */
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

    /** If per-map config missing or empty, attempt to write the bundled default. Writes file if needed. */
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

    public static synchronized void setActive(ServerWorld world, Identifier mapId, VoiceRegionManager manager) {
        activeWorld = world;
        activeMapId = mapId;
        activeManager = manager;
    }

    public static VoiceRegionManager getActiveManager() {
        return activeManager;
    }

    // Accessors added so assigned fields are accessible and to silence warnings
    public static Identifier getActiveMapId() { return activeMapId; }
    public static ServerWorld getActiveWorld() { return activeWorld; }
}