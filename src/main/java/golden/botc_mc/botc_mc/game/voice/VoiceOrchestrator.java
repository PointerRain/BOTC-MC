package golden.botc_mc.botc_mc.game.voice;

import de.maxhenkel.voicechat.api.Group;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/**
 * High-level orchestrator for BOTC's Simple Voice Chat integration.
 * <p>
 * Responsible for ensuring persistent voice groups exist in the running SVC server and
 * materializing per-map groups and region-linked groups when a map is opened.
 * All voice interactions go through {@link VoiceService} — no reflection involved.
 */
public class VoiceOrchestrator {
    private static VoiceOrchestrator INSTANCE = null;

    private final PersistentGroupStore store;
    private final MinecraftServer server;

    public static synchronized VoiceOrchestrator getInstance(MinecraftServer server) {
        if (INSTANCE == null) {
            INSTANCE = new VoiceOrchestrator(server);
        }
        return INSTANCE;
    }

    public static synchronized void reset() {
        INSTANCE = null;
    }

    private VoiceOrchestrator(MinecraftServer server) {
        this.server = server;
        this.store = new PersistentGroupStore();
    }

    /**
     * Ensures all persisted groups exist in SVC. Called once after SVC becomes available.
     */
    public void preload() {
        if (!VoiceService.isAvailable()) return;
        int ok = 0;
        int total = 0;
        for (PersistentGroup g : store.list()) {
            total++;
            try {
                Group group = VoiceService.getOrCreateGroup(g.getName());
                if (group != null) {
                    store.recordVoiceId(group.getId(), g);
                    ok++;
                }
            } catch (Throwable t) {
                botc.LOGGER.warn("VoiceOrchestrator preload failed for {}: {}", g.getName(), t.toString());
            }
        }
        botc.LOGGER.info("VoiceOrchestrator: preloaded {}/{} persistent voice groups", ok, total);
    }

    /**
     * Called when a BOTC map is opened. Ensures per-map groups exist and materializes region groups.
     */
    public void onMapOpen(Identifier mapId) {
        if (mapId == null || !VoiceService.isAvailable()) return;
        try {
            VoiceRegionService.writeDefaultConfigIfMissing(mapId);

            // Ensure groups defined in the map's voice group config exist in SVC
            VoiceGroupManager gm = VoiceGroupManager.forServer(this.server, mapId);
            for (PersistentGroup pg : gm.list()) {
                try {
                    if (pg.getName().isEmpty()) continue;
                    Group group = VoiceService.getOrCreateGroup(pg.getName());
                    if (group != null) store.recordVoiceId(group.getId(), pg);
                } catch (Throwable t) {
                    botc.LOGGER.debug("VoiceOrchestrator: group materialize error for '{}': {}", pg.getName(), t.toString());
                }
            }

            // Ensure each voice region's group exists and record its UUID
            VoiceRegionManager active = VoiceRegionService.getActiveManager();
            if (active == null || !mapId.equals(active.getMapId())) return;
            for (VoiceRegion r : active.list()) {
                try {
                    if (r.groupName() == null || r.groupName().isEmpty()) continue;
                    Group group = VoiceService.getOrCreateGroup(r.groupName());
                    if (group != null) active.updateGroupId(r.id(), group.getId().toString());
                } catch (Throwable t) {
                    botc.LOGGER.warn("VoiceOrchestrator: region materialize error for {}: {}", r.id(), t.toString());
                }
            }
        } catch (Throwable t) {
            botc.LOGGER.warn("VoiceOrchestrator.onMapOpen failed for {}: {}", mapId, t.toString());
        }
    }
}
