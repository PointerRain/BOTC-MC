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
public class PersistentGroup {
    /**
     * Human‑friendly group name. This is what players will typically see in the Simple
     * Voice Chat UI and what config authors refer to in JSON. It also serves as a logical
     * key when {@link #voicechatId} has not yet been assigned.
     */
    public String name;

    /**
     * Optional group password. In most BOTC flows this is cleared in order to make groups
     * freely joinable when they represent public map regions, but the field is preserved
     * so existing JSON can round‑trip without loss.
     */
    public String password;

    /**
     * Whether the group should be hidden from the Simple Voice Chat group list.
     * <p>
     * BOTC generally prefers visible, open groups for region‑based audio, but this flag
     * remains for completeness and potential future use.
     */
    public boolean hidden;

    /**
     * Flag mirroring the voice chat mod's notion of a "persistent" group.
     * <p>
     * Persistent groups survive server restarts; BOTC typically forces this to {@code true}
     * for any groups it manages so that region or map‑linked groups are not lost.
     */
    public boolean persistent = true;

    /**
     * Raw group type string, e.g. {@code "NORMAL"}, {@code "OPEN"}, or other
     * mode values supported by Simple Voice Chat.
     * <p>
     * The value is not interpreted by BOTC directly; it is kept as opaque metadata so
     * that round‑trips between JSON and the underlying mod are lossless where possible.
     */
    public String type = "NORMAL";

    /**
     * The concrete Simple Voice Chat group identifier once a group has been created.
     * <p>
     * This is the main bridge between persisted configuration and live voice state:
     * {@link VoicechatPlugin} and {@link SvcBridge} use it to reopen/repair existing
     * groups without creating duplicates. When {@code null}, name‑based lookup and
     * creation logic is used instead.
     */
    public UUID voicechatId;

    /**
     * Zero‑argument constructor required by Gson when reading from JSON.
     */
    public PersistentGroup() {}

    /**
     * Convenience constructor when code wants to stage a new group with only a name.
     * Other properties (such as {@link #voicechatId}) can be populated later after
     * Simple Voice Chat has created/assigned the backing group.
     *
     * @param name logical name for the group as it should appear to users
     */
    public PersistentGroup(String name) {
        this.name = name;
    }

    /**
     * Human‑readable summary useful for debug logging.
     * <p>
     * The format is compact but includes enough detail (name, id, hidden, type) to
     * understand which group entry is being referenced when printed in server logs.
     */
    @Override
    public String toString() {
        return "PersistentGroup[name=" + name + ",id=" + voicechatId + ",hidden=" + hidden + ",type=" + type + "]";
    }
}
