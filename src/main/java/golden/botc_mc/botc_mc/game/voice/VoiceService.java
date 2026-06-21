package golden.botc_mc.botc_mc.game.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.network.ServerPlayerEntity;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Type-safe facade over the Simple Voice Chat public API.
 * <p>
 * Initialized by {@link BotcVoicechatPlugin} when SVC calls its entrypoint. All methods
 * are no-ops (returning null/false) until {@link #initialize} has been called, so callers
 * do not need to guard every call site with an availability check beyond the obvious
 * "if voice chat is unavailable, skip voice work" guard in {@link VoiceRegionTask}.
 */
public final class VoiceService {

    // volatile: initialize() is called on the SVC thread; all other methods run on the MC server tick thread.
    private static volatile VoicechatServerApi api;

    private VoiceService() {}

    static void initialize(VoicechatServerApi serverApi) {
        api = serverApi;
    }

    public static void reset() {
        api = null;
    }

    public static boolean isAvailable() {
        return api != null;
    }

    /**
     * Returns an existing group with the given name (case-insensitive), creating one if absent.
     * Groups are created as persistent, non-hidden, and password-free with normal type.
     */
    @Nullable
    public static Group getOrCreateGroup(String name) {
        if (api == null || name == null || name.isEmpty()) return null;
        for (Group g : api.getGroups()) {
            if (name.equalsIgnoreCase(g.getName())) return g;
        }
        try {
            return api.groupBuilder()
                .setName(name)
                .setPersistent(true)
                .setHidden(false)
                .setType(Group.Type.NORMAL)
                .build();
        } catch (Exception e) {
            botc.LOGGER.warn("[Voice] Failed to create group '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Joins the player to the group with the given name, creating it if absent.
     *
     * @return true if the join was dispatched; false if the player is not connected to voice chat
     *         or the group could not be resolved
     */
    public static boolean joinGroup(ServerPlayerEntity player, String groupName) {
        if (api == null || player == null || groupName == null) return false;
        Group group = getOrCreateGroup(groupName);
        if (group == null) return false;
        VoicechatConnection conn = api.getConnectionOf(player.getUuid());
        if (conn == null) return false;
        conn.setGroup(group);
        return true;
    }

    /**
     * Removes the player from their current voice chat group.
     *
     * @return true if the leave was dispatched; false if the player is not connected to voice chat
     */
    public static boolean leaveGroup(ServerPlayerEntity player) {
        if (api == null || player == null) return false;
        VoicechatConnection conn = api.getConnectionOf(player.getUuid());
        if (conn == null) return false;
        conn.setGroup(null);
        return true;
    }

    /**
     * Returns the UUID of the group the player is currently in, or null if they are not in one
     * or not connected to voice chat.
     * <p>
     * Always fetches a fresh connection snapshot so the result reflects current state.
     */
    @Nullable
    public static UUID getPlayerGroupId(ServerPlayerEntity player) {
        if (api == null || player == null) return null;
        VoicechatConnection conn = api.getConnectionOf(player.getUuid());
        if (conn == null) return null;
        Group group = conn.getGroup();
        return group == null ? null : group.getId();
    }

    /**
     * Returns true if the player has an active Simple Voice Chat connection.
     * Returns true (permissive) when SVC is unavailable so voice-region logic does not block joins.
     */
    public static boolean isPlayerConnected(ServerPlayerEntity player) {
        if (api == null) return true;
        if (player == null) return false;
        return api.getConnectionOf(player.getUuid()) != null;
    }
}
