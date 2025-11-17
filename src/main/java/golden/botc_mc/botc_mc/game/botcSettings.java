package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.game.map.botcMapConfig;
import net.minecraft.block.Blocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Simple file-backed settings for the BOTC game. This provides a single place to edit
 * common values (time limits, per-phase durations, player limits) without rebuilding the mod.
 *
 * The settings are stored at run/config/botc.properties relative to the project root.
 */
public final class botcSettings {
    private static final Path CONFIG_PATH = Paths.get("run", "config", "botc.properties");

    public int timeLimitSecs = 300;
    public int players = 8;
    public int dayDiscussionSecs = 120;
    public int nominationSecs = 45;
    public int executionSecs = 20;
    public int nightSecs = 60;

    // persisted default map id (optional)
    public String mapId = null;

    private botcSettings() {}

    private static botcSettings settingsInstance = null;

    public static synchronized botcSettings get() {
        if (settingsInstance == null) settingsInstance = botcSettings.load();
        return settingsInstance;
    }

    public static botcSettings load() {
        botcSettings s = new botcSettings();

        try {
            if (Files.exists(CONFIG_PATH)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                    p.load(in);
                }

                s.timeLimitSecs = parseInt(p.getProperty("timeLimitSecs"), s.timeLimitSecs);
                s.players = parseInt(p.getProperty("players"), s.players);
                s.dayDiscussionSecs = parseInt(p.getProperty("dayDiscussionSecs"), s.dayDiscussionSecs);
                s.nominationSecs = parseInt(p.getProperty("nominationSecs"), s.nominationSecs);
                s.executionSecs = parseInt(p.getProperty("executionSecs"), s.executionSecs);
                s.nightSecs = parseInt(p.getProperty("nightSecs"), s.nightSecs);
                s.mapId = p.getProperty("mapId", null);
                if (s.mapId != null && !s.mapId.contains(":")) {
                    s.mapId = "botc:" + s.mapId;
                }
                s.save();
            }
        } catch (IOException e) {
            // best-effort: if loading fails, return defaults
            System.err.println("[BOTC] Failed to load botc.properties: " + e.getMessage());
        }

        return s;
    }

    public void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("timeLimitSecs", Integer.toString(this.timeLimitSecs));
        p.setProperty("players", Integer.toString(this.players));
        p.setProperty("dayDiscussionSecs", Integer.toString(this.dayDiscussionSecs));
        p.setProperty("nominationSecs", Integer.toString(this.nominationSecs));
        p.setProperty("executionSecs", Integer.toString(this.executionSecs));
        p.setProperty("nightSecs", Integer.toString(this.nightSecs));
        if (this.mapId != null) p.setProperty("mapId", this.mapId);

        if (CONFIG_PATH.getParent() != null) Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            p.store(out, "BOTC game configuration (edit to change defaults)");
        }
    }

    private static int parseInt(String v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ex) { return fallback; }
    }

    /**
     * Merge these settings with an existing botcConfig coming from the datapack/game open context.
     * Values from the settings file override the provided config's time- and player-related settings.
     */
    public botcConfig applyTo(botcConfig base) {
        // Default map config uses stone spawn block and default spawn coordinates (0,65,0)
        botcMapConfig mapCfg = (base != null && base.mapConfig() != null) ? base.mapConfig() : new botcMapConfig(Blocks.STONE.getDefaultState(), 0, 65, 0);
        int players = this.players > 0 ? this.players : (base == null ? 8 : base.players());
        int timeLimit = this.timeLimitSecs > 0 ? this.timeLimitSecs : (base == null ? 300 : base.timeLimitSecs());

        botcPhaseDurations durations = new botcPhaseDurations(this.dayDiscussionSecs, this.nominationSecs, this.executionSecs, this.nightSecs);

        return botcConfig.of(mapCfg, players, timeLimit, durations);
    }

    // helpers for external code to get/set persisted map id
    public static synchronized String getMapId() {
        return get().mapId;
    }

    public static synchronized void setMapId(String id) {
        botcSettings s = get();
        s.mapId = id;
    }
}
