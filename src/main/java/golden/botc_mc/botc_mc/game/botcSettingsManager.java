package golden.botc_mc.botc_mc.game;

import java.io.IOException;

/**
 * Simple runtime manager for the file-backed settings. Loads on first use and provides
 * thread-safe getters/setters and save.
 */
public final class botcSettingsManager {
    private static botcSettings settings;

    private botcSettingsManager() {}

    public static synchronized botcSettings get() {
        if (settings == null) {
            settings = botcSettings.load();
        }
        return settings;
    }

    public static synchronized void setInt(String key, int value) {
        botcSettings s = get();
        switch (key) {
            case "timeLimitSecs" -> s.timeLimitSecs = value;
            case "players" -> s.players = value;
            case "dayDiscussionSecs" -> s.dayDiscussionSecs = value;
            case "nominationSecs" -> s.nominationSecs = value;
            case "executionSecs" -> s.executionSecs = value;
            case "nightSecs" -> s.nightSecs = value;
            default -> throw new IllegalArgumentException("Unknown key: " + key);
        }
    }

    public static synchronized int getInt(String key) {
        botcSettings s = get();
        return switch (key) {
            case "timeLimitSecs" -> s.timeLimitSecs;
            case "players" -> s.players;
            case "dayDiscussionSecs" -> s.dayDiscussionSecs;
            case "nominationSecs" -> s.nominationSecs;
            case "executionSecs" -> s.executionSecs;
            case "nightSecs" -> s.nightSecs;
            default -> throw new IllegalArgumentException("Unknown key: " + key);
        };
    }

    public static synchronized String[] keys() {
        return new String[]{"timeLimitSecs", "players", "dayDiscussionSecs", "nominationSecs", "executionSecs", "nightSecs"};
    }

    public static synchronized void save() throws IOException {
        get().save();
    }
}

