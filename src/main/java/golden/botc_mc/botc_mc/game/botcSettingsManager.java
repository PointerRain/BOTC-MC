package golden.botc_mc.botc_mc.game;

import java.io.IOException;

/**
 * Simple runtime manager for the file-backed settings. Loads on first use and provides
 * thread-safe getters/setters and save.
 */
public final class botcSettingsManager {
    private static botcSettings settings;

    private botcSettingsManager() {}

    /** Centralized static accessor for current mutable BOTC settings.
     * @return current settings instance (loads if necessary).
     */
    public static synchronized botcSettings get() {
        if (settings == null) {
            settings = botcSettings.load();
        }
        return settings;
    }

    /** Set an integer value in settings and auto-save.
     * @param key settings key
     * @param value integer value
     */
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

    /** Set a string value in settings.
     * @param key settings key
     * @param value string value
     */
    public static synchronized void setString(String key, String value) {
        botcSettings s = get();
        if ("mapId".equals(key)) {
            s.mapId = value;
        } else {
            throw new IllegalArgumentException("Unknown string key: " + key);
        }
    }

    /** Retrieve an integer setting value.
     * @param key settings key
     * @return integer value by key or throws if unknown
     */
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

    /** Retrieve a string setting value.
     * @param key settings key
     * @return string value by key or throws if unknown
     */
    public static synchronized String getString(String key) {
        botcSettings s = get();
        if ("mapId".equals(key)) {
            return s.mapId;
        } else {
            throw new IllegalArgumentException("Unknown string key: " + key);
        }
    }

    /** Array of recognized integer setting keys.
     * @return immutable array of keys
     */
    public static synchronized String[] keys() {
        return new String[]{"timeLimitSecs", "players", "dayDiscussionSecs", "nominationSecs", "executionSecs", "nightSecs"};
    }

    /** Array of recognized string setting keys.
     * @return immutable array of keys
     */
    public static synchronized String[] stringKeys() {
        return new String[]{"mapId"};
    }

    /** Save current settings snapshot to disk.
     * @throws IOException if an I/O error occurs
     */
    public static synchronized void save() throws IOException {
        get().save();
    }
}
