package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import golden.botc_mc.botc_mc.botc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PersistentGroupStore {
    private final File file;
    private final Gson gson;
    private final List<PersistentGroup> groups = new ArrayList<>();
    private final Map<UUID, PersistentGroup> cache = new HashMap<>();

    public PersistentGroupStore(MinecraftServer server) {
        // Use BOTC config root: <gameDir>/config/botc/
        Path botcRoot = VoiceRegionService.botcConfigRoot();
        try { java.nio.file.Files.createDirectories(botcRoot); } catch (Throwable ignored) {}
        // Ensure voice/voice_groups exists under run/config/botc
        Path voiceGroupsDir = botcRoot.resolve(Paths.get("voice", "voice_groups"));
        try { java.nio.file.Files.createDirectories(voiceGroupsDir); } catch (Throwable ignored) {}
        // Global persistent groups file: run/config/botc/config/botc-persistent-groups.json
        Path cfgDir = botcRoot.resolve("config");
        try { java.nio.file.Files.createDirectories(cfgDir); } catch (Throwable ignored) {}
        this.file = cfgDir.resolve("botc-persistent-groups.json").toFile();
        migrateLegacyDoubleRunIfNeeded();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }
    /** Migrates legacy path run/run/config/botc-persistent-groups.json -> gameDir/config/botc-persistent-groups.json */
    private void migrateLegacyDoubleRunIfNeeded() {
        try {
            File legacy = FabricLoader.getInstance().getGameDir().resolve("run/config/botc-persistent-groups.json").toFile();
            if (!file.exists() && legacy.exists()) {
                java.nio.file.Files.createDirectories(file.getParentFile().toPath());
                java.nio.file.Files.copy(legacy.toPath(), file.toPath());
                golden.botc_mc.botc_mc.botc.LOGGER.info("Migrated legacy persistent group file from double-run path.");
            }
        } catch (Throwable ignored) {}
    }

    public synchronized void load() {
        try {
            if (!file.exists()) return;
            Type listType = new TypeToken<List<PersistentGroup>>(){}.getType();
            try (FileReader fr = new FileReader(file)) {
                List<PersistentGroup> loaded = gson.fromJson(fr, listType);
                if (loaded != null) {
                    groups.clear(); groups.addAll(loaded);
                    rebuildCache();
                }
            }
        } catch (Throwable t) {
            botc.LOGGER.warn("PersistentGroupStore load failed: {}", t.toString());
        }
    }

    public synchronized void save() {
        try (FileWriter fw = new FileWriter(file)) {
            gson.toJson(groups, fw);
        } catch (Throwable t) {
            botc.LOGGER.warn("PersistentGroupStore save failed: {}", t.toString());
        }
    }

    private void rebuildCache() {
        cache.clear();
        for (PersistentGroup g : groups) {
            if (g.voicechatId != null) cache.put(g.voicechatId, g);
        }
    }

    public synchronized List<PersistentGroup> getGroups() { return Collections.unmodifiableList(groups); }

    public synchronized void addGroup(PersistentGroup g) {
        groups.removeIf(x->x.name.equalsIgnoreCase(g.name));
        groups.add(g);
        if (g.voicechatId != null) cache.put(g.voicechatId, g);
        save();
    }

    public synchronized boolean removeGroupByName(String name) {
        Optional<PersistentGroup> opt = groups.stream().filter(g->g.name.equalsIgnoreCase(name)).findFirst();
        if (opt.isEmpty()) return false;
        PersistentGroup g = opt.get();
        groups.remove(g);
        if (g.voicechatId != null) cache.remove(g.voicechatId);
        save();
        return true;
    }

    public synchronized void addCached(UUID id, PersistentGroup g) {
        if (id == null || g == null) return;
        g.voicechatId = id;
        cache.put(id, g);
        save();
    }

    public synchronized PersistentGroup getByVoiceId(UUID id) { return cache.get(id); }
    public synchronized Optional<PersistentGroup> getByName(String name) { return groups.stream().filter(g->g.name.equalsIgnoreCase(name)).findFirst(); }

    public synchronized int clearAll() { int s = groups.size(); groups.clear(); cache.clear(); save(); return s; }
}
