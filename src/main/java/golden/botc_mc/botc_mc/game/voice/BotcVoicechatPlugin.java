package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Paths;
import java.util.UUID;

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
            // Try to detect voicechat API presence (optional)
            try {
                Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi");
            } catch (ClassNotFoundException ignored) {}
            // 1) Preload from our persistent store
            int ok = 0;
            int total = 0;
            for (PersistentGroup g : store.getGroups()) {
                total++;
                try {
                    if (g.voicechatId != null) {
                        // attempt to repair/open existing group id
                        boolean repaired = SvcBridge.clearPasswordAndOpenById(g.voicechatId);
                        if (!repaired) {
                            // fallback by name
                            java.util.UUID id = SvcBridge.createOrGetGroup(g.name, null);
                            if (id != null) {
                                store.addCached(id, g);
                                SvcBridge.clearPasswordAndOpenById(id);
                                ok++;
                            }
                        } else {
                            ok++;
                        }
                        continue;
                    }
                    java.util.UUID id = SvcBridge.createOrGetGroup(g.name, null);
                    if (id != null) {
                        store.addCached(id, g);
                        SvcBridge.clearPasswordAndOpenById(id);
                        ok++;
                    }
                } catch (Throwable t) { botc.LOGGER.warn("BotcVoicechatPlugin preload failed for {}: {}", g.name, t.toString()); }
            }
            // 2) Also preload from region config so previously created regions bring their groups back
            try {
                VoiceRegionManager vrm = new VoiceRegionManager(Paths.get("run", "config", "voice_regions.json"));
                for (VoiceRegion r : vrm.list()) {
                    try {
                        boolean repaired = false;
                        if (r.groupId != null && !r.groupId.isEmpty()) {
                            repaired = SvcBridge.clearPasswordAndOpenByIdString(r.groupId);
                        }
                        if (!repaired) {
                            if (r.groupName != null && !r.groupName.isEmpty()) {
                                UUID id = SvcBridge.createOrGetGroup(r.groupName, null);
                                if (id != null) {
                                    // ensure open/no password
                                    SvcBridge.clearPasswordAndOpenById(id);
                                    // persist in store so next boots rely solely on store
                                    store.getByName(r.groupName).ifPresentOrElse(g -> store.addCached(id, g), () -> {
                                        PersistentGroup pg = new PersistentGroup(r.groupName);
                                        pg.voicechatId = id; store.addGroup(pg);
                                    });
                                    ok++;
                                }
                            }
                        } else {
                            ok++;
                        }
                        total++;
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
}
