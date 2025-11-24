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

    /**
     * Best-effort preload/repair of persisted groups into Simple Voice Chat on server start.
     * See class Javadoc for algorithm details.
     */
    public void preload() {
        try {
            final boolean creationDisabled = SvcBridge.isGroupCreationDisabled();
            try { Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi"); } catch (ClassNotFoundException ignored) {}
            int ok = 0;
            int total = 0;
            for (PersistentGroup g : store.list()) {
                total++;
                try {
                    if (g.getVoicechatId() != null) {
                        // touch cache to justify its presence
                        PersistentGroup existing = store.getByVoiceId(g.getVoicechatId());
                        if (existing != null) {
                            botc.LOGGER.debug("VoicechatPlugin preload existing cached group: {}", existing);
                        }
                        if (SvcBridge.clearPasswordAndOpenById(g.getVoicechatId())) ok++; // repair existing
                        if (attemptCreate(g, creationDisabled)) ok++; // optional creation if name missing at runtime
                        continue;
                    }
                    if (attemptCreate(g, creationDisabled)) ok++; // creation path when no id yet
                } catch (Throwable t) {
                    botc.LOGGER.warn("VoicechatPlugin preload failed for {}: {}", g.getName(), t.toString());
                }
            }
            // Legacy/global regions import
            try {
                VoiceRegionManager vrm = new VoiceRegionManager(VoiceRegionService.legacyGlobalConfigPath());
                for (VoiceRegion r : vrm.list()) {
                    try {
                        total++;
                        boolean repaired = false;
                        if (r.groupId() != null && !r.groupId().isEmpty()) {
                            repaired = SvcBridge.clearPasswordAndOpenByIdString(r.groupId());
                            if (repaired) ok++;
                        }
                        if (!creationDisabled && !repaired && r.groupName() != null && !r.groupName().isEmpty() && !SvcBridge.isGroupPresent(r.groupName())) {
                            java.util.UUID id = SvcBridge.createOrGetGroup(r.groupName());
                            if (id != null) {
                                SvcBridge.clearPasswordAndOpenById(id);
                                store.getByName(r.groupName()).ifPresentOrElse(g -> store.cacheGroup(id, g), () -> {
                                    PersistentGroup pg = new PersistentGroup(r.groupName(), id);
                                    store.addGroup(pg);
                                });
                                ok++;
                            }
                        }
                    } catch (Throwable t) {
                        botc.LOGGER.warn("Region preload error for {}: {}", r.id(), t.toString());
                    }
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
     * Called when a BOTC map is opened. Ensures per-map defaults exist and materializes region groups.
     * @param mapId identifier of the map that has just been opened (ignored if null)
     */
    public void onMapOpen(Identifier mapId) {
        if (mapId == null) return;
        try {
            VoiceRegionService.writeDefaultConfigIfMissing(mapId);
            // Load voice groups defined in map JSON (override or embedded)
            VoiceGroupManager gm = VoiceGroupManager.forServer(this.server, mapId);
            for (PersistentGroup pg : gm.list()) {
                try {
                    if (pg.getVoicechatId() != null) {
                        SvcBridge.clearPasswordAndOpenById(pg.getVoicechatId());
                    } else if (pg.getName() != null && !pg.getName().isEmpty() && !SvcBridge.isGroupPresent(pg.getName()) && !SvcBridge.isGroupCreationDisabled()) {
                        java.util.UUID id = SvcBridge.createOrGetGroup(pg.getName());
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
                            if (!SvcBridge.isGroupPresent(r.groupName())) {
                                java.util.UUID gid = SvcBridge.createOrGetGroup(r.groupName());
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

    /** Attempt to create and open a voice group for the persistent entry if allowed and missing.
     * @param g persistent group descriptor
     * @param creationDisabled global creation disabled flag
     * @return true if a group was created and opened
     */
    private boolean attemptCreate(PersistentGroup g, boolean creationDisabled) {
        if (creationDisabled || g == null || g.getName() == null || g.getName().isEmpty()) return false;
        if (SvcBridge.isGroupPresent(g.getName())) return false; // group already exists
        java.util.UUID id = SvcBridge.createOrGetGroup(g.getName());
        if (id == null) return false;
        store.cacheGroup(id, g);
        SvcBridge.clearPasswordAndOpenById(id);
        return true;
    }
}
