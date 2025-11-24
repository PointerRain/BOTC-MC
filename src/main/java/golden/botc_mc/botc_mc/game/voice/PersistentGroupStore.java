package golden.botc_mc.botc_mc.game.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import golden.botc_mc.botc_mc.botc;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * File-backed storage for {@link PersistentGroup} definitions.
 * <p>
 * This class provides a simple, process-wide registry of named voice chat groups that BOTC
 * should attempt to keep alive across restarts. Key behaviors:
 * <ul>
 *   <li>Stores data as JSON at {@code <gameDir>/config/botc/config/botc-persistent-groups.json}.</li>
 *   <li>On construction, transparently migrates older installs that wrote to a double {@code run/} path.</li>
 *   <li>Maintains an in-memory list and a UUID → group cache for quick lookups by Simple Voice Chat id.</li>
 *   <li>All mutating methods are {@code synchronized} to provide basic thread-safety.</li>
 * </ul>
 */
public class PersistentGroupStore {
    private final File file;
    private final Gson gson;
    private final List<PersistentGroup> groups = new ArrayList<>();
    private final Map<UUID, PersistentGroup> cache = new HashMap<>();

    /** Construct an empty store for persistent voice groups. */
    public PersistentGroupStore() {
        // Use BOTC config root: <gameDir>/config/botc/
        Path botcRoot = VoiceRegionService.botcConfigRoot();
        // Ensure root directory exists so subsequent writes don't fail.
        try { java.nio.file.Files.createDirectories(botcRoot); } catch (Throwable ignored) {}
        // Ensure voice/voice_groups exists under run/config/botc; this directory is also used by
        // other tooling and serves as a conventional home for voice-related config.
        Path voiceGroupsDir = botcRoot.resolve(Paths.get("voice", "voice_groups"));
        try { java.nio.file.Files.createDirectories(voiceGroupsDir); } catch (Throwable ignored) {}
        // Global persistent groups file: <gameDir>/config/botc/config/botc-persistent-groups.json
        Path cfgDir = botcRoot.resolve("config");
        try { java.nio.file.Files.createDirectories(cfgDir); } catch (Throwable ignored) {}
        this.file = cfgDir.resolve("botc-persistent-groups.json").toFile();
        migrateLegacyDoubleRunIfNeeded();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    /**
     * Migrates from an older layout where the file was accidentally written below {@code run/config}
     * into the stable {@code <gameDir>/config/botc/config} tree. The migration only runs if the new
     * file does not exist yet.
     */
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

    /**
     * Load all persisted {@link PersistentGroup} entries from disk into memory. If the file does not
     * exist yet, this method is a no-op and the in-memory registry remains empty.
     */
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

    /**
     * Persist the current in-memory list of groups to the JSON backing file. Any I/O problems are
     * logged but do not propagate as exceptions to callers.
     */
    public synchronized void save() {
        try (FileWriter fw = new FileWriter(file)) {
            gson.toJson(groups, fw);
        } catch (Throwable t) {
            botc.LOGGER.warn("PersistentGroupStore save failed: {}", t.toString());
        }
    }

    /**
     * Rebuild the UUID → {@link PersistentGroup} index from the current list. This is used after
     * load and after bulk modifications to ensure lookups stay in sync.
     */
    private void rebuildCache() {
        cache.clear();
        for (PersistentGroup g : groups) {
            if (g.voicechatId != null) cache.put(g.voicechatId, g);
        }
    }

    /** Immutable snapshot of all known groups.
     * @return list copy
     */
    public synchronized java.util.List<PersistentGroup> list() {
        return Collections.unmodifiableList(groups);
    }

    /** Backwards-compatible accessor returning immutable snapshot of groups.
     * @return unmodifiable list of persistent groups
     */
    public synchronized java.util.List<PersistentGroup> getGroups() { return java.util.Collections.unmodifiableList(groups); }

    /** Add a new group to the store.
     * @param g persistent group instance
     */
    public synchronized void addGroup(PersistentGroup g) {
        groups.removeIf(x->x.name.equalsIgnoreCase(g.name));
        groups.add(g);
        if (g.voicechatId != null) cache.put(g.voicechatId, g);
        save();
    }

    /** Remove a group by its name (case-insensitive).
     * @param name group name
     * @return true if removed
     */
    public synchronized boolean removeGroupByName(String name) {
        Optional<PersistentGroup> opt = groups.stream().filter(g->g.name.equalsIgnoreCase(name)).findFirst();
        if (opt.isEmpty()) return false;
        PersistentGroup g = opt.get();
        groups.remove(g);
        if (g.voicechatId != null) cache.remove(g.voicechatId);
        save();
        return true;
    }

    /** Cache a voice UUID to group mapping for quick lookup.
     * @param id voice chat UUID
     * @param g persistent group
     */
    public synchronized void addCached(UUID id, PersistentGroup g) {
        if (id == null || g == null) return;
        g.voicechatId = id;
        cache.put(id, g);
        save();
    }

    /** Lookup a group by voice UUID.
     * @param id voice chat UUID
     * @return group or null
     */
    public synchronized PersistentGroup getByVoiceId(UUID id) { return cache.get(id); }

    /** Lookup a group by name.
     * @param name group name
     * @return optional containing found group
     */
    public synchronized Optional<PersistentGroup> getByName(String name) { return groups.stream().filter(g->g.name.equalsIgnoreCase(name)).findFirst(); }

    /**
     * Remove all groups from memory and disk. Primarily useful for administrative tools or in tests.
     *
     * @return number of groups that were cleared
     */
    public synchronized int clearAll() { int s = groups.size(); groups.clear(); cache.clear(); save(); return s; }
}
