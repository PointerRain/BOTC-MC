package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Paths;
import java.util.UUID;
import net.minecraft.util.Identifier;

public class BotcVoicechatPlugin {
    private static BotcVoicechatPlugin INSTANCE = null;

    private final PersistentGroupStore store;
    private final MinecraftServer server;
    // Use Object so compilation doesn't require voicechat API on classpath
    private Object api = null;

    public static synchronized BotcVoicechatPlugin getInstance(MinecraftServer server) {
        if (INSTANCE == null) {
            INSTANCE = new BotcVoicechatPlugin(server);
        }
        return INSTANCE;
    }

    public BotcVoicechatPlugin(MinecraftServer server) {
        this.server = server;
        this.store = new PersistentGroupStore(server);
    }

    public PersistentGroupStore getStore() { return store; }
    public MinecraftServer getServer() { return server; }

    /**
     * Best-effort preload/repair of persisted groups into Simple Voice Chat on server start.
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
            for (PersistentGroup g : store.getGroups()) {
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
                } catch (Throwable t) { botc.LOGGER.warn("BotcVoicechatPlugin preload failed for {}: {}", g.name, t.toString()); }
            }
            try {
                VoiceRegionManager vrm = new VoiceRegionManager(VoiceRegionService.legacyGlobalConfigPath());
                for (VoiceRegion r : vrm.list()) {
                    try {
                        total++;
                        boolean repaired = false;
                        if (r.groupId != null && !r.groupId.isEmpty()) {
                            repaired = SvcBridge.clearPasswordAndOpenByIdString(r.groupId);
                        }
                        if (!repaired && !creationDisabled && r.groupName != null && !r.groupName.isEmpty() && !SvcBridge.groupExists(r.groupName)) {
                            java.util.UUID id = SvcBridge.createOrGetGroup(r.groupName, null);
                            if (id != null) {
                                SvcBridge.clearPasswordAndOpenById(id);
                                store.getByName(r.groupName).ifPresentOrElse(g -> store.addCached(id, g), () -> {
                                    PersistentGroup pg = new PersistentGroup(r.groupName);
                                    pg.voicechatId = id; store.addGroup(pg);
                                });
                                ok++;
                            }
                        } else if (repaired) {
                            ok++;
                        }
                    } catch (Throwable t) { botc.LOGGER.warn("Region preload error for {}: {}", r.id, t.toString()); }
                }
            } catch (Throwable t) {
                botc.LOGGER.debug("VoiceRegionManager not available during preload: {}", t.toString());
            }
            botc.LOGGER.info("BotcVoicechatPlugin: preloaded {}/{} voice groups (store+regions)", ok, total);
        } catch (Throwable t) {
            botc.LOGGER.warn("BotcVoicechatPlugin preload error: {}", t.toString());
        }
    }

    /**
     * Called when a map is opened. Ensure per-map defaults exist and attempt to preload any groups.
     */
    public void onMapOpen(Identifier mapId) {
        if (mapId == null) return;
        try {
            // Ensure a default per-map config exists
            VoiceRegionService.writeDefaultConfigIfMissing(mapId);

            // Load voice groups from embedded or override and ensure groups exist in SVC (best-effort)
            VoiceGroupManager gm = VoiceGroupManager.forServer(this.server, mapId);
            for (PersistentGroup pg : gm.list()) {
                try {
                    if (pg.voicechatId != null) {
                        SvcBridge.clearPasswordAndOpenById(pg.voicechatId);
                    } else if (pg.name != null && !pg.name.isEmpty() && !SvcBridge.groupExists(pg.name)) {
                        java.util.UUID id = SvcBridge.createOrGetGroup(pg.name, null);
                        if (id != null) {
                            // best-effort: mark persistent/open
                            SvcBridge.clearPasswordAndOpenById(id);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            botc.LOGGER.warn("BotcVoicechatPlugin.onMapOpen failed for {}: {}", mapId, t.toString());
        }
    }
}
