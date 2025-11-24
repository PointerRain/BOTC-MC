package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/**
 * High-level orchestrator for BOTC's Simple Voice Chat integration.
 * <p>
 * This plugin is responsible for:
 * <ul>
 *   <li>Loading persistent group definitions from {@link PersistentGroupStore}.</li>
 *   <li>Ensuring those groups exist in the running Simple Voice Chat server and repairing them if needed.</li>
 *   <li>Materializing per-map voice groups and region-linked groups when a map is opened.</li>
 * </ul>
 * It intentionally hides the reflection-heavy details behind {@link SvcBridge}, so the rest of the
 * codebase interacts with voice via a stable, minimal surface.
 */
public class VoicechatPlugin {
    private static VoicechatPlugin INSTANCE = null;

    private final PersistentGroupStore store;
    private final MinecraftServer server;

    /**
     * Retrieve (or lazily create) the singleton plugin instance for the given server.
     * Although this method stores the first server it sees, in practice BOTC targets
     * a single-server environment, so the instance is effectively global.
     * @param server minecraft server
     * @return plugin instance
     */
    public static synchronized VoicechatPlugin getInstance(MinecraftServer server) {
        if (INSTANCE == null) {
            INSTANCE = new VoicechatPlugin(server);
        }
        return INSTANCE;
    }

    /** Construct plugin bound to server; loads persistent groups.
     * @param server minecraft server instance
     */
    public VoicechatPlugin(MinecraftServer server) {
        this.server = server;
        this.store = new PersistentGroupStore();
    }

    /** Server bound to this plugin.
     * @return server instance
     */
    public MinecraftServer getServer() { return server; }

    /**
     * Best-effort preload/repair of persisted groups into Simple Voice Chat on server start.
     * <p>
     * The algorithm:
     * <ol>
     *   <li>Check global group creation configuration via {@link SvcBridge#isGroupCreationDisabled()}.</li>
     *   <li>Iterate all {@link PersistentGroup} entries from {@link PersistentGroupStore} and attempt to
     *       reopen or recreate missing groups, caching newly assigned ids.</li>
     *   <li>Optionally scan legacy {@link VoiceRegionManager} data and ensure any referenced groups also exist.</li>
     * </ol>
     * Any failures are logged but do not abort server startup.
     */
    public void preload() {
        try {
            boolean creationDisabled = SvcBridge.isGroupCreationDisabled();
            // Try to detect voicechat API presence (optional)
            try {
                Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi");
            } catch (ClassNotFoundException ignored) {}
            int ok = 0;
            int total = 0;
            for (PersistentGroup g : store.list()) {
                total++;
                try {
                    if (creationDisabled && (g.voicechatId == null)) {
                        // Skip attempts entirely if disabled and no existing id
                        continue;
                    }
                    if (g.voicechatId != null) {
                        boolean repaired = SvcBridge.clearPasswordAndOpenById(g.voicechatId);
                        if (!repaired && !creationDisabled && !SvcBridge.groupExists(g.name)) {
                            java.util.UUID id = SvcBridge.createOrGetGroup(g.name, null);
                            if (id != null) {
                                store.addCached(id, g);
                                SvcBridge.clearPasswordAndOpenById(id);
                                ok++;
                            }
                        } else if (repaired) {
                            ok++;
                        }
                        continue;
                    }
                    if (!creationDisabled && !SvcBridge.groupExists(g.name)) {
                        java.util.UUID id = SvcBridge.createOrGetGroup(g.name, null);
                        if (id != null) {
                            store.addCached(id, g);
                            SvcBridge.clearPasswordAndOpenById(id);
                            ok++;
                        }
                    }
                } catch (Throwable t) { botc.LOGGER.warn("VoicechatPlugin preload failed for {}: {}", g.name, t.toString()); }
            }
            try {
                VoiceRegionManager vrm = new VoiceRegionManager(VoiceRegionService.legacyGlobalConfigPath());
                for (VoiceRegion r : vrm.list()) {
                    try {
                        total++;
                        boolean repaired = false;
                        if (r.groupId() != null && !r.groupId().isEmpty()) {
                            repaired = SvcBridge.clearPasswordAndOpenByIdString(r.groupId());
                        }
                        if (!repaired && !creationDisabled && r.groupName() != null && !r.groupName().isEmpty() && !SvcBridge.groupExists(r.groupName())) {
                            java.util.UUID id = SvcBridge.createOrGetGroup(r.groupName(), null);
                            if (id != null) {
                                SvcBridge.clearPasswordAndOpenById(id);
                                store.getByName(r.groupName()).ifPresentOrElse(g -> store.addCached(id, g), () -> {
                                    PersistentGroup pg = new PersistentGroup(r.groupName());
                                    pg.voicechatId = id; store.addGroup(pg);
                                });
                                ok++;
                            }
                        } else if (repaired) {
                            ok++;
                        }
                    } catch (Throwable t) { botc.LOGGER.warn("Region preload error for {}: {}", r.id(), t.toString()); }
                }
            } catch (Throwable t) {
                botc.LOGGER.debug("VoiceRegionManager not available during preload: {}", t.toString());
            }
            botc.LOGGER.info("VoicechatPlugin: preloaded {}/{} voice groups (store+regions)", ok, total);
        } catch (Throwable t) {
            botc.LOGGER.warn("VoicechatPlugin preload error: {}", t.toString());
        }
    }

    /**
     * Called when a BOTC map is opened. Ensures per-map defaults exist, preloads any groups that are
     * defined either in the per-map voice JSON or region configuration, and attempts to repair region
     * group identifiers where possible.
     *
     * @param mapId identifier of the map that has just been opened
     */
    public void onMapOpen(Identifier mapId) {
        if (mapId == null) return;
        try {
            VoiceRegionService.writeDefaultConfigIfMissing(mapId);
            // Load voice groups defined in map JSON (override or embedded)
            VoiceGroupManager gm = VoiceGroupManager.forServer(this.server, mapId);
            for (PersistentGroup pg : gm.list()) {
                try {
                    if (pg.voicechatId != null) {
                        SvcBridge.clearPasswordAndOpenById(pg.voicechatId);
                    } else if (pg.name != null && !pg.name.isEmpty() && !SvcBridge.groupExists(pg.name) && !SvcBridge.isGroupCreationDisabled()) {
                        java.util.UUID id = SvcBridge.createOrGetGroup(pg.name, null);
                        if (id != null) {
                            SvcBridge.clearPasswordAndOpenById(id);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // Materialize region groups from active per-map manager (already set active in waiting logic)
            VoiceRegionManager active = VoiceRegionService.getActiveManager();
            if (active != null && mapId.equals(active.getMapId())) {
                for (VoiceRegion r : active.list()) {
                    try {
                        if (r.groupName() == null || r.groupName().isEmpty()) continue;
                        boolean creationDisabled = SvcBridge.isGroupCreationDisabled();
                        if (r.groupId() != null && !r.groupId().isEmpty()) {
                            SvcBridge.clearPasswordAndOpenByIdString(r.groupId());
                        } else if (!creationDisabled) {
                            if (!SvcBridge.groupExists(r.groupName())) {
                                java.util.UUID gid = SvcBridge.createOrGetGroup(r.groupName(), null);
                                if (gid != null) {
                                    active.updateGroupId(r.id(), gid.toString());
                                    SvcBridge.clearPasswordAndOpenById(gid);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        botc.LOGGER.warn("VoicechatPlugin: region materialize error for {}: {}", r.id(), t.toString());
                    }
                }
            } else {
                botc.LOGGER.debug("VoicechatPlugin: active manager missing or mapId mismatch for materialization (mapId={})", mapId);
            }
        } catch (Throwable t) {
            botc.LOGGER.warn("VoicechatPlugin.onMapOpen failed for {}: {}", mapId, t.toString());
        }
    }
}
