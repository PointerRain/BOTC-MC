package golden.botc_mc.botc_mc.game.voice;

import java.util.UUID;

/**
 * Models a single, long-lived Simple Voice Chat group as BOTC understands it.
 * <p>
 * These objects are persisted to disk and may be deserialized by Gson (using a
 * registered adapter). They are intentionally small: {@code name} is immutable
 * while {@code voicechatId} remains mutable so runtime code can cache/update
 * the associated UUID.
 */
public final class PersistentGroup {
    /** Runtime Simple Voice Chat assigned UUID for the group (nullable until created). */
    private UUID voicechatId;
    private static final String DEFAULT_TYPE = "NORMAL";

    // Group display name (may be empty). Made final for immutability; deserialized via adapter.
    private final String name;

    /**
     * Construct a persistent group descriptor with the given name and (optional) voicechat id.
     * @param name persisted display name (may be empty, non-null)
     * @param voicechatId runtime UUID or null
     */
    public PersistentGroup(String name, UUID voicechatId) {
        this.name = name == null ? "" : name;
        this.voicechatId = voicechatId;
    }

    /** Get the persisted group name (never null).
     * @return group name (empty string when not set)
     */
    public String getName() { return name; }

    /** Get the runtime UUID assigned by the voice mod, or {@code null} if not assigned.
     * @return runtime UUID or {@code null}
     */
    public UUID getVoicechatId() { return voicechatId; }

    /** Update the runtime UUID assigned to this persistent group.
     * @param id new runtime UUID or {@code null} to clear
     */
    public void setVoicechatId(UUID id) { this.voicechatId = id; }

    @Override
    public String toString() {
        return "PersistentGroup[name=" + getName() + ",id=" + voicechatId + ",type=" + DEFAULT_TYPE + "]";
    }
}
