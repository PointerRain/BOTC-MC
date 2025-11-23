package golden.botc_mc.botc_mc.game;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import golden.botc_mc.botc_mc.game.voice.VoiceRegionService;

/**
 * Simple file-backed settings for the BOTC game. This provides a single place to edit
 * common values (time limits, per-phase durations, player limits) without rebuilding the mod.
 *
 * The settings are stored at run/config/botc/config/botc.properties relative to the project root.
 */
public final class botcSettings {
    private static final Path CONFIG_PATH = VoiceRegionService.botcConfigRoot().resolve(Paths.get("config", "botc.properties"));

    public int timeLimitSecs = 300;
    public int players = 8;
    public int dayDiscussionSecs = 120;
    public int nominationSecs = 45;
    public int executionSecs = 20;
    public int nightSecs = 60;
    public String mapId = "botc-mc:test";
    public BlockPos fallbackSpawn = new BlockPos(0, 65, 0);

    private botcSettings() {}

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
                s.mapId = p.getProperty("mapId", s.mapId);
                s.fallbackSpawn = parseBlockPos(p.getProperty("fallbackSpawn"), s.fallbackSpawn);
            } else {
                // ensure parent directory exists and write defaults
                if (CONFIG_PATH.getParent() != null) Files.createDirectories(CONFIG_PATH.getParent());
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
        p.setProperty("mapId", this.mapId);
        p.setProperty("fallbackSpawn", formatBlockPos(this.fallbackSpawn));

        if (CONFIG_PATH.getParent() != null) Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            p.store(out, "BOTC game configuration (edit to change defaults)");
        }
    }

    private static int parseInt(String v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ex) { return fallback; }
    }

    private static BlockPos parseBlockPos(String value, BlockPos fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return fallback;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * Merge these settings with an existing botcConfig coming from the datapack/game open context.
     * Currently this only updates the selected map id based on the settings file; timing and player
     * values are managed independently from the map config.
     */
    public botcConfig applyTo(botcConfig base) {
        Identifier selectedMap = base != null && base.mapId() != null ? base.mapId() : Identifier.of(this.mapId);
        return new botcConfig(selectedMap);
    }
}