package golden.botc_mc.botc_mc.game.voice;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

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

    public static Path legacyGlobalConfigPath() {
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
        // Store per map under <gameDir>/config/voice_regions/<ns>/<path>.json
        return configRoot().resolve(Paths.get("voice_regions", ns, path + ".json"));
    }

    public static Path datapackOverrideBase() {
        return gameDir().resolve(Paths.get("world", "datapacks", "botc_overrides"));
    }

    public static Path datapackOverrideGameFile(Identifier mapId) {
        return datapackOverrideBase().resolve(Paths.get("data", mapId.getNamespace(), "plasmid", "game", mapId.getPath() + ".json"));
    }

    public static synchronized void setActive(ServerWorld world, Identifier mapId, VoiceRegionManager manager) {
        activeWorld = world;
        activeMapId = mapId;
        activeManager = manager;
    }

    public static synchronized void clearActive() {
        activeWorld = null;
        activeMapId = null;
        activeManager = null;
    }

    public static VoiceRegionManager getActiveManager() {
        return activeManager;
    }

    public static Identifier getActiveMapId() {
        return activeMapId;
    }

    public static ServerWorld getActiveWorld() { return activeWorld; }

    public static VoiceRegionManager managerForWorld(ServerWorld world) {
        VoiceRegionManager mgr = activeManager;
        if (mgr == null) return null;
        return Objects.equals(activeWorld, world) ? mgr : null;
    }
}
