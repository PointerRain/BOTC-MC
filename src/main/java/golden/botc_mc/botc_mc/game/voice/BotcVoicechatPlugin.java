package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.MinecraftServer;

import net.minecraft.util.Identifier;

public class BotcVoicechatPlugin {
    private static BotcVoicechatPlugin INSTANCE = null;

    private final PersistentGroupStore store;
    private final MinecraftServer server;

    public static synchronized BotcVoicechatPlugin getInstance(MinecraftServer server) {
        if (INSTANCE == null) {
            INSTANCE = new BotcVoicechatPlugin(server);
        }
        return INSTANCE;
    }

    public BotcVoicechatPlugin(MinecraftServer server) {
        this.server = server;
        this.store = new PersistentGroupStore();
    }

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
                        botc.LOGGER.warn("BotcVoicechatPlugin: region materialize error for {}: {}", r.id(), t.toString());
                    }
                }
            } else {
                botc.LOGGER.debug("BotcVoicechatPlugin: active manager missing or mapId mismatch for materialization (mapId={})", mapId);
            }
        } catch (Throwable t) {
            botc.LOGGER.warn("BotcVoicechatPlugin.onMapOpen failed for {}: {}", mapId, t.toString());
        }
    }
}
