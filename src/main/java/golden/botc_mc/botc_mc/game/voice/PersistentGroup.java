package golden.botc_mc.botc_mc.game.voice;

import java.util.UUID;

/**
 * Models a single, long‑lived Simple Voice Chat group as BOTC understands it.
 * <p>
 * These objects are:
 * <ul>
 *   <li>Loaded and saved by {@link PersistentGroupStore} to a JSON file under the BOTC config root.</li>
 *   <li>Used by {@link VoicechatPlugin} to "preload" or repair groups when the server starts
 *       or when a map is opened (ensuring the matching Simple Voice Chat groups exist).</li>
 *   <li>Mapped to actual runtime voice chat groups via {@link SvcBridge}, which operates purely
 *       through reflection and uses {@link #voicechatId} where available.</li>
 * </ul>
 * Only a very small, stable subset of Simple Voice Chat properties is captured here so that
 * persisted data is resilient across voice chat mod updates.
 */
public final class PersistentGroup {
    private final String name; // made final
    private UUID voicechatId; // remains mutable
    private static final String DEFAULT_TYPE = "NORMAL";

    public PersistentGroup(String name, UUID voicechatId) {
        this.name = name;
        this.voicechatId = voicechatId;
    }

    public String getName() { return name; }
    public UUID getVoicechatId() { return voicechatId; }
    public void setVoicechatId(UUID id) { this.voicechatId = id; }

    /**
     * Human‑readable summary useful for debug logging.
     * <p>
     * The format is compact but includes enough detail (name, id, hidden, type) to
     * understand which group entry is being referenced when printed in server logs.
     */
    @Override
    public String toString() {
        return "PersistentGroup[name=" + name + ",id=" + voicechatId + ",type=" + DEFAULT_TYPE + "]";
    }
}
