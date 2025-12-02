package golden.botc_mc.botc_mc.game;

<<<<<<< HEAD
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.map.botcMapConfig;
import net.minecraft.block.Blocks;
=======
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
>>>>>>> main

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
 * <p>
 * The settings are stored at run/config/botc/config/botc.properties relative to the project root.
 */
public final class botcSettings {
    private static final Path CONFIG_PATH = VoiceRegionService.botcConfigRoot().resolve(Paths.get("config", "botc.properties"));

    /** Mutable settings loaded from disk for customizing a session prior to start. */
    public botcSettings() {}

    /** Max time limit for lobby or overall session (seconds). */
    public int timeLimitSecs = 300;
    /** Target player count. */
    public int players = 8;
    /** Day discussion duration seconds. */
    public int dayDiscussionSecs = 120;
    /** Nomination phase duration seconds. */
    public int nominationSecs = 45;
    /** Execution phase duration seconds. */
    public int executionSecs = 20;
    /** Night phase duration seconds. */
    public int nightSecs = 60;
    /** Map identifier string used to resolve resources. */
    public String mapId = "botc-mc:test";
    /** Fallback spawn position if map lacks defined spawn. */
    public BlockPos fallbackSpawn = new BlockPos(0, 65, 0);

    /** Load settings from disk or create defaults if the file is missing.
     * @return loaded settings or defaults if file missing.
     */
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

    /** Persist current settings to disk.
     * @throws IOException if writing the properties file fails
     */
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

    /**
     * Apply these mutable settings to an immutable config.
     * @param base base config to merge
     * @return new config incorporating dynamic settings
     */
    public botcConfig applyTo(botcConfig base) {
        Identifier selectedMap = base != null && base.mapId() != null ? base.mapId() : Identifier.of(this.mapId);
        return new botcConfig(selectedMap);
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
        Identifier selectedMap = base != null && base.mapId() != null ? base.mapId() : Identifier.of(this.mapId);
        int players = this.players > 0 ? this.players : (base == null ? 8 : base.players());
        int timeLimit = this.timeLimitSecs > 0 ? this.timeLimitSecs : (base == null ? 300 : base.timeLimitSecs());

        botcPhaseDurations durations = new botcPhaseDurations(this.dayDiscussionSecs, this.nominationSecs, this.executionSecs, this.nightSecs);

        Script script = Script.MISSING;
        String scriptId = "trouble_brewing";
        if (base != null)  {
            script = base.script() != Script.MISSING ? base.script() : Script.fromId(base.scriptId());
            scriptId = base.scriptId();
        }

        botc.LOGGER.info("Resolved script: {}", script);

        return botcConfig.of(selectedMap, players, timeLimit, durations, scriptId, script);
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
}