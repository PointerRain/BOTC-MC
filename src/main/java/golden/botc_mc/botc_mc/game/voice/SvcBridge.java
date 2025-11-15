package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Simplified reflection bridge for Simple Voice Chat.
 * Strategy:
 *  1. Fetch de.maxhenkel.voicechat.Voicechat static field SERVER (ServerVoiceEvents).
 *  2. Call getServer() -> Server (voice server thread object).
 *  3. From Server call getGroupManager() and getPlayerStateManager().
 *  4. Use ServerGroupManager methods: getGroups(), addGroup(Group, ServerPlayerEntity), joinGroup(Group, ServerPlayerEntity, String), leaveGroup(ServerPlayerEntity).
 *  5. For creating a new group: attempt to find a builder or use GroupImpl.create(PlayerState) then rename via reflection if possible.
 *
 * If any step fails, diagnostics are recorded and available returns false. Re-attempt occurs on each runtime check until successful.
 */
public final class SvcBridge {
    private static boolean available = false;
    private static boolean initializing = false;

    private static Object serverVoiceEvents;            // de.maxhenkel.voicechat.voice.server.ServerVoiceEvents instance
    private static Object voiceServer;                  // de.maxhenkel.voicechat.voice.server.Server instance
    private static Object serverGroupManager;           // de.maxhenkel.voicechat.voice.server.ServerGroupManager instance
    private static Object playerStateManager;           // de.maxhenkel.voicechat.voice.server.PlayerStateManager instance

    private static Method gmGetGroups;                  // ServerGroupManager.getGroups()
    private static Method gmAddGroup;                   // ServerGroupManager.addGroup(Group, ServerPlayerEntity)
    private static Method gmJoinGroup;                  // ServerGroupManager.joinGroup(Group, ServerPlayerEntity, String)
    private static Method gmLeaveGroup;                 // ServerGroupManager.leaveGroup(ServerPlayerEntity)
    private static Method gmRemoveGroup;                // ServerGroupManager.removeGroup(UUID)
    private static Method gmGetGroup;                  // ServerGroupManager.getGroup(UUID)
    private static Method gmGetPlayerGroup; // ServerGroupManager.getPlayerGroup(ServerPlayerEntity)

    private static Method psGetState;                   // PlayerStateManager.getState(UUID)
    private static Method psSetGroup;                   // PlayerStateManager.setGroup(ServerPlayerEntity, UUID)
    private static Method psBroadcastState;            // PlayerStateManager.broadcastState(ServerPlayerEntity, PlayerState)
    private static Method psBroadcastRemoveState;      // PlayerStateManager.broadcastRemoveState(ServerPlayerEntity)
    private static Method psDefaultDisconnectedState; // PlayerStateManager.defaultDisconnectedState(ServerPlayerEntity)

    private static Method groupGetId;                   // Group.getId()
    private static Method groupGetName;                 // Group.getName()
    private static Field groupPersistentField;
    private static Method groupGetPassword; // Group.getPassword()
    private static Method groupSetPassword; // potential setter
    private static Method groupSetOpen; // potential setter for open
    private static Field groupTypeField;               // reference to Group.type
    private static Class<?> groupTypeClass;            // de.maxhenkel.voicechat.api.Group$Type
    private static Object groupTypeOpenConstant;       // constant for OPEN type

    private static Method groupImplCreate;              // GroupImpl.create(PlayerState)
    private static Method groupImplGetGroup;            // GroupImpl.getGroup()
    private static Field groupNameField;                // attempt to set private name

    // NetManager/JoinedGroupPacket reflection fallback
    private static Method netSendToClient; // de.maxhenkel.voicechat.net.NetManager.sendToClient(ServerPlayerEntity, Packet)
    private static Class<?> joinedGroupPacketClass;
    private static java.lang.reflect.Constructor<?> joinedGroupPacketCtor;

    private static final List<String> diagnostics = new ArrayList<>();
    // Alias mapping: desired logical name -> actual group UUID (when we cannot set name)
    private static final Map<String, UUID> aliasGroups = new HashMap<>();

    private static void diag(String msg) {
        diagnostics.add(msg);
        botc.LOGGER.info(msg);
    }

    public static List<String> getDiagnostics() { return new ArrayList<>(diagnostics); }
    public static boolean isAvailable() { return available; }

    /** Returns true if the players voice state indicates they are connected (not disconnected). If unknown, returns true. */
    public static boolean isPlayerConnected(ServerPlayerEntity player) {
        if (!isAvailableRuntime() || player == null) return true; // default permissive
        try {
            if (psGetState == null) return true;
            Object state = psGetState.invoke(playerStateManager, player.getUuid());
            if (state == null) return false;
            // Try boolean accessor methods first
            try {
                Method m = state.getClass().getMethod("isDisconnected");
                Object r = m.invoke(state);
                if (r instanceof Boolean b) return !b;
            } catch (Throwable ignored) {}
            try {
                Method m = state.getClass().getMethod("isDisabled");
                Object r = m.invoke(state);
                if (r instanceof Boolean b && b) return false;
            } catch (Throwable ignored) {}
            // Inspect common fields
            for (Field f : state.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    String n = f.getName().toLowerCase();
                    if ((n.contains("disconnect") || n.equals("disconnected")) && (f.getType() == boolean.class || f.getType() == Boolean.class)) {
                        Object v = f.get(state);
                        if (v instanceof Boolean b) return !b;
                    }
                    if (n.contains("disabled") && (f.getType() == boolean.class || f.getType() == Boolean.class)) {
                        Object v = f.get(state);
                        if (v instanceof Boolean b && b) return false;
                    }
                } catch (Throwable ignored) {}
            }
            // If we cant determine, assume connected
            return true;
        } catch (Throwable t) {
            diag("SvcBridge: isPlayerConnected error: " + t);
            return true; // dont block joins if reflection fails
        }
    }

    /** Runtime check with lazy initialization */
    public static boolean isAvailableRuntime() {
        if (!available) attemptInit();
        return available;
    }

    private static synchronized void attemptInit() {
        if (available || initializing) return;
        initializing = true;
        try {
            Class<?> voicechatCls = Class.forName("de.maxhenkel.voicechat.Voicechat");
            Field serverField = voicechatCls.getField("SERVER");
            serverVoiceEvents = serverField.get(null);
            if (serverVoiceEvents == null) {
                diag("SvcBridge: Voicechat.SERVER field is null");
                return;
            }
            Method getServer = serverVoiceEvents.getClass().getMethod("getServer");
            voiceServer = getServer.invoke(serverVoiceEvents);
            if (voiceServer == null) { diag("SvcBridge: getServer() returned null"); return; }

            Method getGroupManager = voiceServer.getClass().getMethod("getGroupManager");
            serverGroupManager = getGroupManager.invoke(voiceServer);
            if (serverGroupManager == null) { diag("SvcBridge: getGroupManager() returned null"); return; }

            Method getPlayerStateManager = voiceServer.getClass().getMethod("getPlayerStateManager");
            playerStateManager = getPlayerStateManager.invoke(voiceServer);
            if (playerStateManager == null) { diag("SvcBridge: getPlayerStateManager() returned null"); return; }

            // Resolve group manager methods
            for (Method m : serverGroupManager.getClass().getMethods()) {
                switch (m.getName()) {
                    case "getGroups" -> gmGetGroups = m;
                    case "addGroup" -> { if (m.getParameterCount()==2) gmAddGroup = m; }
                    case "joinGroup" -> { if (m.getParameterCount()==3) gmJoinGroup = m; }
                    case "leaveGroup" -> { if (m.getParameterCount()==1) gmLeaveGroup = m; }
                    case "removeGroup" -> { if (m.getParameterCount()==1) gmRemoveGroup = m; }
                    case "getGroup" -> { if (m.getParameterCount()==1 && m.getParameterTypes()[0]==UUID.class) gmGetGroup = m; }
                    case "getPlayerGroup" -> { if (m.getParameterCount()==1) gmGetPlayerGroup = m; }
                }
            }
            if (gmGetGroups == null) diag("SvcBridge: getGroups not found");
            if (gmAddGroup == null) diag("SvcBridge: addGroup not found");
            if (gmJoinGroup == null) diag("SvcBridge: joinGroup not found");
            if (gmLeaveGroup == null) diag("SvcBridge: leaveGroup not found");
            if (gmRemoveGroup == null) diag("SvcBridge: removeGroup(UUID) not found (alias lookup limited)");
            if (gmGetGroup == null) diag("SvcBridge: getGroup(UUID) not found (alias lookup limited)");
            if (gmGetPlayerGroup == null) diag("SvcBridge: getPlayerGroup(ServerPlayerEntity) not found (will use state fallback)");

            // Player state methods
            for (Method m : playerStateManager.getClass().getMethods()) {
                if (m.getName().equals("getState") && m.getParameterCount()==1) psGetState = m;
                if (m.getName().equals("setGroup") && m.getParameterCount()==2) psSetGroup = m;
                if (m.getName().equals("broadcastState") && m.getParameterCount()==2) psBroadcastState = m;
                if (m.getName().equals("broadcastRemoveState") && m.getParameterCount()==1) psBroadcastRemoveState = m;
                if (m.getName().equals("defaultDisconnectedState") && m.getParameterCount()==1) psDefaultDisconnectedState = m;
            }
            if (psGetState == null) diag("SvcBridge: PlayerStateManager.getState(UUID) not found");
            if (psSetGroup == null) diag("SvcBridge: PlayerStateManager.setGroup(ServerPlayerEntity,UUID) not found (will try gm methods)");
            if (psBroadcastState == null) diag("SvcBridge: PlayerStateManager.broadcastState not found (client UI may not update)");
            if (psBroadcastRemoveState == null) diag("SvcBridge: PlayerStateManager.broadcastRemoveState not found (client UI may not clear)");
            if (psDefaultDisconnectedState == null) diag("SvcBridge: PlayerStateManager.defaultDisconnectedState not found (will use broadcastState fallback)");

            // Group class metadata (we will inspect one element later when available)
            Class<?> groupClass = Class.forName("de.maxhenkel.voicechat.voice.server.Group");
            if (groupTypeClass == null) {
                try {
                    groupTypeClass = Class.forName("de.maxhenkel.voicechat.api.Group$Type");
                    for (Field f : groupTypeClass.getFields()) {
                        if (f.getName().equalsIgnoreCase("open")) {
                            groupTypeOpenConstant = f.get(null);
                            diag("SvcBridge: found Group$Type.OPEN constant");
                            break;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    diag("SvcBridge: Group$Type class not found");
                } catch (Throwable t) {
                    diag("SvcBridge: Group$Type reflection error: " + t);
                }
            }
            for (Method m : groupClass.getMethods()) {
                if (m.getName().equals("getId") && m.getParameterCount()==0) groupGetId = m;
                if (m.getName().equals("getName") && m.getParameterCount()==0) groupGetName = m;
                if (m.getName().equals("getPassword") && m.getParameterCount()==0) groupGetPassword = m;
                if ((m.getName().equalsIgnoreCase("setPassword") || m.getName().equalsIgnoreCase("password")) && m.getParameterCount()==1 && m.getParameterTypes()[0]==String.class) groupSetPassword = m;
                if ((m.getName().equalsIgnoreCase("setOpen") || m.getName().equalsIgnoreCase("open") || m.getName().equalsIgnoreCase("setIsOpen")) && m.getParameterCount()==1 && (m.getParameterTypes()[0]==boolean.class || m.getParameterTypes()[0]==Boolean.class)) groupSetOpen = m;
            }
            if (groupGetId == null) diag("SvcBridge: Group.getId not found");
            if (groupGetName == null) diag("SvcBridge: Group.getName not found");
            // Try to find a persistence boolean field
            for (Field f : groupClass.getDeclaredFields()) {
                if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                    String n = f.getName().toLowerCase();
                    if (n.contains("persist")) {
                        f.setAccessible(true);
                        groupPersistentField = f;
                        diag("SvcBridge: found persistent field candidate: " + f.getName());
                        break;
                    }
                }
                if (groupTypeField == null && groupTypeClass != null && groupTypeClass.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    groupTypeField = f;
                    diag("SvcBridge: found type field: " + f.getName());
                }
            }

            // GroupImpl path (optional)
            try {
                Class<?> groupImplCls = Class.forName("de.maxhenkel.voicechat.plugins.impl.GroupImpl");
                for (Method m : groupImplCls.getMethods()) {
                    if (m.getName().equals("create") && m.getParameterCount()==1) groupImplCreate = m;
                    if (m.getName().equals("getGroup") && m.getParameterCount()==0) groupImplGetGroup = m;
                }
                // Attempt to find a name field to override
                for (Field f : groupImplCls.getDeclaredFields()) {
                    if (f.getName().equalsIgnoreCase("name") || f.getType()==String.class) {
                        f.setAccessible(true);
                        groupNameField = f;
                        break;
                    }
                }
                if (groupImplCreate != null) diag("SvcBridge: GroupImpl.create found");
                else diag("SvcBridge: GroupImpl.create not found (group creation fallback limited)");
            } catch (ClassNotFoundException e) {
                diag("SvcBridge: GroupImpl class not found; will not attempt reflective creation");
            }

            // Try to locate NetManager.sendToClient and JoinedGroupPacket for fallback packet sends
            try {
                Class<?> netMgrCls = Class.forName("de.maxhenkel.voicechat.net.NetManager");
                for (Method m : netMgrCls.getMethods()) {
                    if (m.getName().equals("sendToClient") && m.getParameterCount()==2) {
                        netSendToClient = m; // static
                        break;
                    }
                }
                try {
                    joinedGroupPacketClass = Class.forName("de.maxhenkel.voicechat.net.JoinedGroupPacket");
                    for (var ctor : joinedGroupPacketClass.getDeclaredConstructors()) {
                        Class<?>[] pts = ctor.getParameterTypes();
                        if (pts.length==2 && (UUID.class.isAssignableFrom(pts[0]) || Object.class.isAssignableFrom(pts[0]))) {
                            // expect (UUID, boolean) or similar
                            ctor.setAccessible(true);
                            joinedGroupPacketCtor = ctor;
                            break;
                        }
                    }
                } catch (ClassNotFoundException ignored) {}
            } catch (ClassNotFoundException ignored) {}

            available = gmGetGroups != null; // minimal requirement
            diag("SvcBridge: initialization " + (available ? "succeeded" : "incomplete"));
        } catch (Throwable t) {
            diag("SvcBridge: initialization error: " + t);
        } finally {
            initializing = false;
        }
    }

    // Utility: fetch groups map
    @SuppressWarnings("unchecked")
    private static Map<UUID,Object> rawGroups() {
        if (!isAvailableRuntime() || gmGetGroups == null) return Collections.emptyMap();
        try {
            Object mapObj = gmGetGroups.invoke(serverGroupManager);
            if (mapObj instanceof Map<?,?> m) {
                Map<UUID,Object> out = new HashMap<>();
                for (Map.Entry<?,?> e : m.entrySet()) {
                    if (e.getKey() instanceof UUID u) out.put(u, e.getValue());
                }
                return out;
            }
        } catch (Throwable t) { diag("SvcBridge: rawGroups error: " + t); }
        return Collections.emptyMap();
    }

    // Attempt to send a JoinedGroupPacket to a player via NetManager (best-effort fallback)
    private static boolean sendJoinedGroupPacket(ServerPlayerEntity player, UUID gid, boolean passwordError) {
        if (netSendToClient != null && joinedGroupPacketCtor != null && gid != null && player != null) {
            try {
                Object pkt = joinedGroupPacketCtor.newInstance(gid, passwordError);
                netSendToClient.invoke(null, player, pkt);
                diag("SvcBridge: sent JoinedGroupPacket to " + player.getUuid() + " for " + gid);
                return true;
            } catch (Throwable t) {
                diag("SvcBridge: sendJoinedGroupPacket failed: " + t);
            }
        }
        return false;
    }

    // Find group by name
    private static Object findGroupByName(String name) {
        for (Object g : rawGroups().values()) {
            try {
                if (groupGetName != null) {
                    Object n = groupGetName.invoke(g);
                    if (n != null && n.toString().equalsIgnoreCase(name)) return g;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static UUID getGroupId(Object group) {
        try { return (UUID) groupGetId.invoke(group); } catch (Throwable ignored) { return null; }
    }

    // Helper to fetch a group object by UUID via ServerGroupManager.getGroup(UUID)
    public static Object getGroupById(UUID id) {
        if (gmGetGroup == null || id == null) return null;
        try { return gmGetGroup.invoke(serverGroupManager, id); } catch (Throwable ignored) { return null; }
    }

    private static void markPersistent(Object group) {
        if (group == null) return;
        if (groupPersistentField != null) {
            try {
                groupPersistentField.set(group, true);
                diag("SvcBridge: marked group persistent via field " + groupPersistentField.getName());
                return;
            } catch (Throwable t) {
                diag("SvcBridge: failed to set persistent field: " + t.getMessage());
            }
        }
        // attempt method-based setters if available
        for (Method m : group.getClass().getMethods()) {
            if (m.getParameterCount()==1 && (m.getName().equalsIgnoreCase("setPersistent") || m.getName().equalsIgnoreCase("persistent"))) {
                try { m.invoke(group, true); diag("SvcBridge: marked group persistent via method " + m.getName()); return; } catch (Throwable ignored) {}
            }
        }
    }

    private static void clearPasswordAndOpen(Object group) {
        if (group == null) return;
        try {
            // Try explicit method-based open/password setters first
            if (groupSetOpen != null) {
                try { groupSetOpen.invoke(group, true); diag("SvcBridge: set group open via method"); } catch (Throwable ignored) {}
            }
            if (groupSetPassword != null) {
                try { groupSetPassword.invoke(group, (Object) null); diag("SvcBridge: cleared password via setter"); } catch (Throwable ignored) {}
            }

            // Inspect fields and set common names by heuristics
            Class<?> cls = group.getClass();
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    String fname = f.getName().toLowerCase();
                    Class<?> ft = f.getType();
                    if (ft == String.class && (fname.contains("pass") || fname.contains("password") || fname.contains("pwd"))) {
                        // set to null (important: empty string can trigger password check as non-null)
                        f.set(group, null);
                        diag("SvcBridge: cleared password via field " + f.getName());
                    } else if ((ft == boolean.class || ft == Boolean.class) && fname.contains("open")) {
                        f.set(group, true);
                        diag("SvcBridge: set open via field " + f.getName());
                    } else if ((ft == boolean.class || ft == Boolean.class) && fname.contains("hidden")) {
                        // ensure group is not hidden
                        f.set(group, false);
                        diag("SvcBridge: cleared hidden flag via field " + f.getName());
                    } else if ((ft == boolean.class || ft == Boolean.class) && fname.contains("persist")) {
                        f.set(group, true);
                        diag("SvcBridge: set persistent via field " + f.getName());
                    } else if (groupTypeField != null && f.equals(groupTypeField) && groupTypeOpenConstant != null) {
                        // force OPEN type
                        f.set(group, groupTypeOpenConstant);
                        diag("SvcBridge: set type OPEN via field " + f.getName());
                    }
                } catch (Throwable ignored) {}
            }

            // Also attempt method-based setters for common names
            for (Method m : group.getClass().getMethods()) {
                try {
                    String name = m.getName().toLowerCase();
                    if (name.contains("setopen") && m.getParameterCount()==1 && (m.getParameterTypes()[0]==boolean.class || m.getParameterTypes()[0]==Boolean.class)) {
                        try { m.invoke(group, true); diag("SvcBridge: set open via method " + m.getName()); } catch (Throwable ignored) {}
                    }
                    if (name.contains("password") && m.getParameterCount()==1 && m.getParameterTypes()[0]==String.class) {
                        try { m.invoke(group, (Object) null); diag("SvcBridge: cleared password via method " + m.getName()); } catch (Throwable ignored) {}
                    }
                    if (name.contains("hidden") && m.getParameterCount()==1 && (m.getParameterTypes()[0]==boolean.class || m.getParameterTypes()[0]==Boolean.class)) {
                        try { m.invoke(group, false); diag("SvcBridge: cleared hidden via method " + m.getName()); } catch (Throwable ignored) {}
                    }
                    if (name.contains("persist") && m.getParameterCount()==1 && (m.getParameterTypes()[0]==boolean.class || m.getParameterTypes()[0]==Boolean.class)) {
                        try { m.invoke(group, true); diag("SvcBridge: set persistent via method " + m.getName()); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

            // If we discovered a type field and OPEN constant separately, ensure again
            try {
                if (groupTypeField != null && groupTypeOpenConstant != null) {
                    groupTypeField.setAccessible(true);
                    groupTypeField.set(group, groupTypeOpenConstant);
                    diag("SvcBridge: enforced OPEN type via cached type field");
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            diag("SvcBridge: clearPasswordAndOpen failed: " + t.getMessage());
        }
    }

    /** Creates a group if missing, returning UUID (or null on failure). */
    public static UUID createOrGetGroup(String desiredName, ServerPlayerEntity creator) {
        if (!isAvailableRuntime()) return null;
        Object existing = findGroupByName(desiredName);
        if (existing != null) return getGroupId(existing);
        // If alias already exists, return mapped UUID (group may have different internal name)
        if (aliasGroups.containsKey(desiredName)) {
            UUID mapped = aliasGroups.get(desiredName);
            Object aliasGroup = SvcBridge.getGroupById(mapped);
            if (aliasGroup != null) return mapped; // still valid
        }

        // Attempt creation via GroupImpl if available (requires non-null creator)
        if (creator != null && groupImplCreate != null && psGetState != null) {
            try {
                // capture groups before
                Map<UUID,Object> before = rawGroups();
                Object state = psGetState.invoke(playerStateManager, creator.getUuid());
                if (state != null) {
                    Object groupImpl = groupImplCreate.invoke(null, state);
                    if (groupImpl != null && groupImplGetGroup != null) {
                        Object group = groupImplGetGroup.invoke(groupImpl);
                        // rename if possible on returned group object (preferred)
                        if (group != null) {
                            try {
                                // prefer setting name on the actual Group object
                                for (Field f : group.getClass().getDeclaredFields()) {
                                    if (f.getType() == String.class && f.getName().equalsIgnoreCase("name")) {
                                        f.setAccessible(true);
                                        f.set(group, desiredName);
                                        break;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        // fallback: try to set name on the impl if we couldn't on the group
                        if ((group == null || (groupGetName != null && !desiredName.equalsIgnoreCase((String) (groupGetName.invoke(group))))) && groupNameField != null) {
                            try { groupNameField.set(groupImpl, desiredName); } catch (Throwable ignored) {}
                        }

                        if (group != null) {
                            // Mark persistent before adding if possible
                            markPersistent(group);
                            // ensure group is open and has no password so joins don't fail
                            clearPasswordAndOpen(group);
                            if (gmAddGroup != null) gmAddGroup.invoke(serverGroupManager, group, (Object) null); // don't auto-join creator
                            Object after = findGroupByName(desiredName);
                            if (after != null) {
                                UUID resultId = getGroupId(after);
                                // ensure persisted and no password on authoritative instance
                                if (resultId != null) {
                                    clearPasswordAndOpenById(resultId);
                                    markPersistent(getGroupById(resultId));
                                }
                                return getGroupId(after);
                            }
                            // fallback: compare new groups added
                            Map<UUID,Object> afterMap = rawGroups();
                            for (UUID id : afterMap.keySet()) {
                                if (!before.containsKey(id)) {
                                    aliasGroups.put(desiredName, id);
                                    // repair the newly added group
                                    clearPasswordAndOpenById(id);
                                    markPersistent(getGroupById(id));
                                    diag("SvcBridge: aliased logical name '" + desiredName + "' to UUID " + id);
                                    return id;
                                }
                            }
                            // fallback: return id of created group
                            UUID fallbackId = getGroupId(group);
                            if (fallbackId != null) {
                                clearPasswordAndOpenById(fallbackId);
                                markPersistent(getGroupById(fallbackId));
                            }
                            return getGroupId(group);
                        }
                    }
                }
            } catch (Throwable t) {
                diag("SvcBridge: group creation via GroupImpl failed: " + t);
            }
        }

        // Constructor-based fallback: try to instantiate server Group directly
        try {
            Class<?> serverGroupCls = Class.forName("de.maxhenkel.voicechat.voice.server.Group");
            for (var ctor : serverGroupCls.getDeclaredConstructors()) {
                try {
                    Class<?>[] params = ctor.getParameterTypes();
                    Object[] args = new Object[params.length];
                    UUID newId = UUID.randomUUID();
                    int stringCount = 0;
                    int booleanCount = 0;
                    for (int i = 0; i < params.length; i++) {
                        Class<?> pt = params[i];
                        if (UUID.class.isAssignableFrom(pt)) args[i] = newId;
                        else if (pt == String.class) {
                            // only the first String param is the name; subsequent String params (e.g., password) should be null
                            if (stringCount == 0) { args[i] = desiredName; } else { args[i] = null; }
                            stringCount++;
                        }
                        else if (pt.isEnum()) {
                            Object[] ec = pt.getEnumConstants();
                            args[i] = ec != null && ec.length > 0 ? ec[0] : null;
                        } else if (pt == boolean.class || pt == Boolean.class) {
                            // heuristics: if first boolean -> persistent=true, second boolean -> hidden=false
                            args[i] = (booleanCount == 0) ? Boolean.TRUE : Boolean.FALSE;
                            booleanCount++;
                        } else if (Number.class.isAssignableFrom(pt) || pt.isPrimitive()) {
                            // numeric defaults
                            if (pt == int.class || pt == Integer.class) args[i] = 0;
                            else if (pt == long.class || pt == Long.class) args[i] = 0L;
                            else if (pt == float.class || pt == Float.class) args[i] = 0f;
                            else if (pt == double.class || pt == Double.class) args[i] = 0d;
                            else if (pt == short.class || pt == Short.class) args[i] = (short)0;
                            else if (pt == byte.class || pt == Byte.class) args[i] = (byte)0;
                            else args[i] = null;
                        } else {
                            args[i] = null; // unsupported type
                        }
                    }
                    ctor.setAccessible(true);
                    Object groupObj = ctor.newInstance(args);
                    if (groupObj != null && gmAddGroup != null) {
                        // heuristically mark persistent
                        markPersistent(groupObj);
                        clearPasswordAndOpen(groupObj);
                        gmAddGroup.invoke(serverGroupManager, groupObj, (Object) null); // don't auto-join creator
                        Object after = findGroupByName(desiredName);
                        if (after != null) {
                            UUID resultId = getGroupId(after);
                            if (resultId != null) {
                                clearPasswordAndOpenById(resultId);
                                markPersistent(getGroupById(resultId));
                            }
                            return getGroupId(after);
                        }
                        UUID id = getGroupId(groupObj);
                        if (id != null) {
                            // ensure the authoritative instance is clear/open
                            clearPasswordAndOpenById(id);
                            markPersistent(getGroupById(id));
                            aliasGroups.put(desiredName, id);
                            diag("SvcBridge: constructed Group via ctor; aliasing '" + desiredName + "' to UUID " + id);
                            return id;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            diag("SvcBridge: constructor fallback failed: " + t);
        }
        diag("SvcBridge: unable to create group '" + desiredName + "' (no builder available)");
        return null;
    }

    /** Join group by name (create if absent). */
    public static boolean joinGroupByName(ServerPlayerEntity player, String groupName) {
        if (!isAvailableRuntime()) return false;
        Object group = findGroupByName(groupName);
        if (group == null && aliasGroups.containsKey(groupName)) {
            group = SvcBridge.getGroupById(aliasGroups.get(groupName));
        }
        UUID gid = null;
        if (group != null) gid = getGroupId(group);

        if (group == null) {
            UUID id = createOrGetGroup(groupName, player);
            if (id == null) {
                diag("SvcBridge: failed to create or find group '" + groupName + "'");
                return false;
            }
            gid = id;
            group = SvcBridge.getGroupById(gid);
            if (group == null) {
                // attempt to find by name as last resort
                group = findGroupByName(groupName);
                if (group == null) {
                    diag("SvcBridge: group created but authoritative instance not found for '" + groupName + "'");
                }
            }
        }

        try {
            if (gid == null) {
                gid = getGroupId(group);
                if (gid == null) { diag("SvcBridge: group has no UUID, cannot join"); return false; }
            }

            // Use authoritative instance from manager if available
            Object authGroup = SvcBridge.getGroupById(gid);
            if (authGroup != null) group = authGroup;

            // Repair authoritative group before attempting join (clear password/open, persistent)
            try {
                repairGroupById(gid, player);
            } catch (Throwable t) { diag("SvcBridge: repairGroupById threw: " + t); }

            // Try to join via ServerGroupManager.joinGroup if available
            boolean joinedAttempt = false;
            if (gmJoinGroup != null) {
                try {
                    // ensure authoritative group has no password/open
                    ensureAuthoritativeCleared(gid, player);
                    // pass null password to indicate no-password join
                    gmJoinGroup.invoke(serverGroupManager, group, player, (Object) null);
                    joinedAttempt = true;
                    diag("SvcBridge: invoked gmJoinGroup for '" + groupName + "' (id=" + gid + ")");
                } catch (Throwable t) {
                    diag("SvcBridge: gmJoinGroup failed for '" + groupName + "': " + t);
                }
            }

            // Verify membership with snappier retries (to allow SVC internal update)
            final int MAX_RETRY = 15;
            final long RETRY_SLEEP_MS = 20; // snappier but slightly longer total
            for (int i = 0; i < MAX_RETRY; i++) {
                UUID now = getPlayerGroupId(player);
                if (now != null && now.equals(gid)) {
                    return true;
                }
                try { Thread.sleep(RETRY_SLEEP_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }

            // Fallbacks: try to directly notify client and set player state
            boolean fallbackOk = false;
            try {
                // try to send JoinedGroupPacket via NetManager if available
                if (sendJoinedGroupPacket(player, gid, false)) {
                    // wait briefly for state to sync
                    for (int i = 0; i < 10; i++) {
                        UUID now = getPlayerGroupId(player);
                        if (now != null && now.equals(gid)) return true;
                        try { Thread.sleep(25); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                    fallbackOk = true;
                }
            } catch (Throwable ignored) {}

            if (!fallbackOk) {
                try {
                    // Last-resort: set player state directly and broadcast state so client updates (may not fully enable audio path)
                    if (psSetGroup != null) {
                        psSetGroup.invoke(playerStateManager, player, gid);
                        // broadcast the updated state
                        if (psGetState != null && psBroadcastState != null) {
                            Object state = psGetState.invoke(playerStateManager, player.getUuid());
                            if (state != null) psBroadcastState.invoke(playerStateManager, player, state);
                        }
                        // verification wait
                        for (int i = 0; i < 8; i++) {
                            UUID now = getPlayerGroupId(player);
                            if (now != null && now.equals(gid)) return true;
                            try { Thread.sleep(30); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        }
                        fallbackOk = true;
                    }
                } catch (Throwable t) { diag("SvcBridge: psSetGroup fallback failed: " + t); }
            }

            diag("SvcBridge: joinGroupByName final verification failed for '" + groupName + "' (id=" + gid + ")");
            return joinedAttempt || fallbackOk; // true if we at least requested join or applied fallback
        } catch (Throwable t) {
            diag("SvcBridge: joinGroupByName error: " + t);
            return false;
        }
    }

     public static boolean leaveGroup(ServerPlayerEntity player) {
         if (!isAvailableRuntime()) return false;
         boolean ok = false;
         // Attempt server group leave if available
         if (gmLeaveGroup != null) {
             try { gmLeaveGroup.invoke(serverGroupManager, player); ok = true; diag("SvcBridge: invoked gmLeaveGroup for player " + player.getUuid()); } catch (Throwable t) { diag("SvcBridge: leaveGroup error: " + t); }
         } else {
             diag("SvcBridge: leaveGroup method not found (will attempt to clear player state instead)");
         }
         // ensure player's state is cleared server-side (fallback only if gmLeaveGroup missing)
         try {
             if (!ok && psSetGroup != null) {
                 psSetGroup.invoke(playerStateManager, player, (Object) null);
                 ok = true;
                 diag("SvcBridge: cleared player state via psSetGroup for " + player.getUuid());
             }
         } catch (Throwable t) { diag("SvcBridge: failed to clear player state: " + t); }
         // broadcast removal to clients so UI updates
         try {
            if (psBroadcastRemoveState != null) {
                psBroadcastRemoveState.invoke(playerStateManager, player);
                ok = true;
                diag("SvcBridge: broadcasted remove state for " + player.getUuid());
            } else if (psBroadcastState != null && psDefaultDisconnectedState != null) {
                Object defaultState = psDefaultDisconnectedState.invoke(playerStateManager, player);
                if (defaultState != null) { psBroadcastState.invoke(playerStateManager, player, defaultState); ok = true; diag("SvcBridge: broadcasted default disconnected state for " + player.getUuid()); }
            }
        } catch (Throwable t) { diag("SvcBridge: failed to broadcast remove state: " + t); }

        // Verify with retries and re-apply if necessary
        try {
            final int MAX_RETRY = 6;
            final long RETRY_SLEEP_MS = 30;
            for (int i = 0; i < MAX_RETRY; i++) {
                java.util.UUID now = getPlayerGroupId(player);
                if (now == null) return ok;
                // try again clearing operations
                try {
                    if (psSetGroup != null) psSetGroup.invoke(playerStateManager, player, (Object) null);
                    if (psBroadcastRemoveState != null) psBroadcastRemoveState.invoke(playerStateManager, player);
                } catch (Throwable ignored) {}
                try { Thread.sleep(RETRY_SLEEP_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            java.util.UUID still = getPlayerGroupId(player);
            if (still != null) {
                diag("SvcBridge: leaveGroup attempted but player still in group: " + still);
                return ok;
            }
            return ok;
        } catch (Throwable t) {
            diag("SvcBridge: error verifying leaveGroup success: " + t);
            return ok; // best effort
        }
     }

    /** Clear password and mark open for a group found by name. */
    public static boolean clearPasswordAndOpenByName(String groupName) {
        if (!isAvailableRuntime()) return false;
        Object g = findGroupByName(groupName);
        if (g == null && aliasGroups.containsKey(groupName)) g = SvcBridge.getGroupById(aliasGroups.get(groupName));
        if (g == null) return false;
        clearPasswordAndOpen(g);
        return true;
    }

    /** Clear password and mark open for a group found by UUID string. */
    public static boolean clearPasswordAndOpenByIdString(String idStr) {
        if (idStr == null) return false;
        try { return clearPasswordAndOpenById(UUID.fromString(idStr)); } catch (Throwable t) { return false; }
    }

    /** Clear password and mark open for a group found by UUID. */
    public static boolean clearPasswordAndOpenById(UUID id) {
        if (!isAvailableRuntime() || id == null) return false;
        Object g = SvcBridge.getGroupById(id);
        if (g == null) return false;
        clearPasswordAndOpen(g);
        return true;
    }

     /** List existing group names for tab completion or debugging. */
     public static List<String> listGroupNames() {
         if (!isAvailableRuntime()) return Collections.emptyList();
         List<String> names = new ArrayList<>();
         for (Object g : rawGroups().values()) {
             try {
                 Object n = groupGetName.invoke(g);
                 if (n != null) names.add(n.toString());
             } catch (Throwable ignored) {}
         }
         // include alias logical names (mark them if they don't correspond to actual name)
         for (Map.Entry<String,UUID> e : aliasGroups.entrySet()) {
             Object g = SvcBridge.getGroupById(e.getValue());
             String realName = null;
             try { if (g != null) realName = (String) groupGetName.invoke(g); } catch (Throwable ignored) {}
             if (realName == null || !realName.equalsIgnoreCase(e.getKey())) {
                 names.add(e.getKey() + " (alias)" );
             }
         }
         return names;
     }

    /** Attempt to repair a group identified by UUID: clear password/open, mark persistent and optionally recreate if a creator is supplied and repairs fail. Returns the (possibly new) UUID of the repaired group or null on failure. */
    public static UUID repairGroupById(UUID id, ServerPlayerEntity creator) {
        if (!isAvailableRuntime() || id == null) return null;
        Object g = SvcBridge.getGroupById(id);
        if (g == null) return null;
        try {
            clearPasswordAndOpen(g);
            markPersistent(g);
            // verify password cleared
            try {
                if (groupGetPassword != null) {
                    Object pw = groupGetPassword.invoke(g);
                    if (pw != null && pw.toString().length() > 0) {
                        diag("SvcBridge: group " + id + " still has password after clear attempt");
                        // try remove & recreate if allowed and creator provided
                        if (creator != null && gmRemoveGroup != null) {
                            try {
                                gmRemoveGroup.invoke(serverGroupManager, id);
                                diag("SvcBridge: removed group " + id + " during repair");
                            } catch (Throwable rem) { diag("SvcBridge: removeGroup failed during repair: " + rem); }
                            UUID newId = createOrGetGroup((groupGetName != null ? (String)groupGetName.invoke(g) : ""), creator);
                            return newId;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return id;
        } catch (Throwable t) { diag("SvcBridge: repairGroupById failed: " + t); return null; }
    }

    /** Repair by group name; tries to find existing group by name then delegates to repairGroupById. */
    public static UUID repairGroupByName(String name, ServerPlayerEntity creator) {
        if (!isAvailableRuntime() || name == null) return null;
        Object g = findGroupByName(name);
        if (g == null && aliasGroups.containsKey(name)) g = SvcBridge.getGroupById(aliasGroups.get(name));
        if (g == null) return null;
        UUID id = getGroupId(g);
        return repairGroupById(id, creator);
    }

    /** Returns the UUID of the group the player is currently in according to the server group manager/state, or null. */
    public static UUID getPlayerGroupId(ServerPlayerEntity player) {
        if (!isAvailableRuntime() || player == null) return null;
        try {
            if (gmGetPlayerGroup != null) {
                Object g = gmGetPlayerGroup.invoke(serverGroupManager, player);
                UUID id = coerceUuid(g);
                if (id != null) return id;
             }
             // fallback: try PlayerStateManager.getState and inspect potential group id field
             if (psGetState != null) {
                 Object state = psGetState.invoke(playerStateManager, player.getUuid());
                 UUID id = extractGroupIdFromStateObject(state);
                 if (id != null) return id;
             }
         } catch (Throwable t) { diag("SvcBridge: getPlayerGroupId error: " + t); }
         return null;
     }

    /** Try to coerce various reflected objects (Group, UUID wrapper, etc.) into a UUID. */
    private static UUID coerceUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID) return (UUID) obj;
        // If we have groupGetId and the object looks like a group, try that first
        try {
            if (groupGetId != null) {
                try {
                    Object res = groupGetId.invoke(obj);
                    if (res instanceof UUID) return (UUID) res;
                } catch (Throwable ignored) {}
            }
            // Try a generic getId() method
            try {
                Method m = obj.getClass().getMethod("getId");
                Object res = m.invoke(obj);
                if (res instanceof UUID) return (UUID) res;
            } catch (Throwable ignored) {}
            // Try common field names
            for (Field f : obj.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof UUID) return (UUID) v;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Extract a group UUID from a PlayerState-like object via getGroup() or a 'group' field. */
    private static UUID extractGroupIdFromStateObject(Object state) {
        if (state == null) return null;
        // Try method getGroup()
        try {
            try {
                Method gm = state.getClass().getMethod("getGroup");
                Object r = gm.invoke(state);
                if (r instanceof UUID) return (UUID) r;
                // sometimes it could return a Group object
                UUID maybe = coerceUuid(r);
                if (maybe != null) return maybe;
            } catch (Throwable ignored) {}
            // Try fields named 'group' or 'groupId'
            for (Field f : state.getClass().getDeclaredFields()) {
                try {
                    String n = f.getName().toLowerCase();
                    if (n.contains("group")) {
                        f.setAccessible(true);
                        Object v = f.get(state);
                        if (v instanceof UUID) return (UUID) v;
                        UUID maybe = coerceUuid(v);
                        if (maybe != null) return maybe;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Aggressively ensure the authoritative group instance is clear (no password), open and persistent. Returns true if OK. */
    private static boolean ensureAuthoritativeCleared(UUID id, ServerPlayerEntity creator) {
        if (!isAvailableRuntime() || id == null) return false;
        final int ATTEMPTS = 5;
        final long SLEEP_MS = 40;
        for (int i = 0; i < ATTEMPTS; i++) {
            Object g = SvcBridge.getGroupById(id);
            if (g == null) {
                try { Thread.sleep(SLEEP_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                continue;
            }
            try {
                clearPasswordAndOpen(g);
                markPersistent(g);
                // verify password cleared
                if (groupGetPassword != null) {
                    Object pw = groupGetPassword.invoke(g);
                    if (pw == null || pw.toString().isEmpty()) return true;
                } else {
                    // no getter available - assume we succeeded
                    return true;
                }
            } catch (Throwable t) {
                diag("SvcBridge: ensureAuthoritativeCleared attempt failed: " + t);
            }
            try { Thread.sleep(SLEEP_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        // Last resort: remove & recreate if allowed
        if (gmRemoveGroup != null && creator != null) {
            try {
                gmRemoveGroup.invoke(serverGroupManager, id);
                diag("SvcBridge: removed group " + id + " as last-resort repair");
            } catch (Throwable t) { diag("SvcBridge: removeGroup last-resort failed: " + t); }
            UUID newId = createOrGetGroup((""), creator); // attempt recreation with empty logical name (caller will reconcile)
            if (newId != null) {
                diag("SvcBridge: recreated group as " + newId + " during last-resort repair");
                return true;
            }
        }
        return false;
    }

    /** Best-effort: leave group for all given players to avoid stale state before wiping groups. */
    public static void leaveAllPlayers(Collection<ServerPlayerEntity> players) {
        if (players == null) return;
        for (ServerPlayerEntity p : players) {
            try { leaveGroup(p); } catch (Throwable ignored) {}
        }
    }

    /** Remove all voice chat groups from the server manager, returning how many were removed. */
    public static int removeAllGroupsBestEffort() {
        if (!isAvailableRuntime()) return 0;
        int removed = 0;
        try {
            Map<UUID,Object> groups = rawGroups();
            // Create stable list of ids to remove
            List<UUID> ids = new ArrayList<>(groups.keySet());
            for (UUID id : ids) {
                try {
                    if (gmRemoveGroup != null) {
                        gmRemoveGroup.invoke(serverGroupManager, id);
                        removed++;
                    }
                } catch (Throwable t) {
                    diag("SvcBridge: removeGroup failed for " + id + ": " + t);
                }
            }
        } catch (Throwable t) {
            diag("SvcBridge: removeAllGroupsBestEffort error: " + t);
        }
        try { aliasGroups.clear(); } catch (Throwable ignored) {}
        return removed;
    }
 }