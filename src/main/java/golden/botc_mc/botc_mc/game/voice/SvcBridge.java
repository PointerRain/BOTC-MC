package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.fabricmc.loader.api.FabricLoader; // added for mod version lookup
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
    // NEW: flag to mark that the voice chat mod is not present; prevents repeated init spam
    private static boolean permanentlyMissing = false;
    private static int initAttempts = 0;
    // Added missing version field
    private static String detectedVoicechatVersion = null;

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
    private static final Map<String, UUID> aliasGroups = new HashMap<>();
    private static boolean groupCreationUnavailable = false;
    private static boolean groupCreationWarned = false;
    private static final Set<String> failedCreationNames = new java.util.HashSet<>(); // names for which creation already failed

    private static final Map<String, Integer> unresolvedGroupAttempts = new HashMap<>();
    private static final Set<String> suppressedGroups = new HashSet<>();
    private static final int SUPPRESS_THRESHOLD = 2; // log first attempt, second, then suppress
    private static final int SUPPRESS_RELOG_INTERVAL = 200; // attempts between periodic log (approx ticks) when suppressed
    private static final Map<String, Long> nextJoinAttemptMs = new HashMap<>();
    private static final long BACKOFF_INITIAL_MS = 1000; // 1s after suppression
    private static final long BACKOFF_MAX_MS = 15000; // cap backoff at 15s
    private static final Map<String, Long> currentBackoffMs = new HashMap<>();

    private static void diag(String msg) {
        // Lower verbosity: use debug unless first attempt or important
        if (!permanentlyMissing) {
            if (initAttempts <= 1) {
                botc.LOGGER.info(msg);
            } else {
                botc.LOGGER.debug(msg);
            }
        } else {
            // When permanently missing, suppress routine diagnostics
            botc.LOGGER.debug(msg);
        }
        diagnostics.add(msg);
    }

    /** Quick check whether the external voice chat mod was detected at least once. */
    public static boolean isModPresent() { return !permanentlyMissing; }
    /** Returns true if features are disabled due to missing mod. */
    public static boolean isDisabledNoMod() { return permanentlyMissing; }

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
        if (permanentlyMissing) return false; // hard disabled
        if (!available) attemptInit();
        return available;
    }

    private static synchronized void attemptInit() {
        if (permanentlyMissing || available || initializing) return;
        initializing = true;
        initAttempts++;
        try {
            // Detect mod presence early; if class not found, mark missing and do not spam
            try {
                Class.forName("de.maxhenkel.voicechat.Voicechat");
            } catch (ClassNotFoundException e) {
                permanentlyMissing = true;
                diag("SvcBridge: voice chat mod not detected; disabling voice features (no further attempts). ");
                botc.LOGGER.info("[Voice] Simple Voice Chat mod not found; voice features disabled.");
                return;
            }

            // Resolve version from Fabric loader if available
            try {
                FabricLoader.getInstance().getModContainer("voicechat").ifPresent(mc -> {
                    detectedVoicechatVersion = mc.getMetadata().getVersion().getFriendlyString();
                });
            } catch (Throwable ignored) {}
            if (detectedVoicechatVersion == null) detectedVoicechatVersion = "unknown";
            botc.LOGGER.info("[Voice] Simple Voice Chat mod detected (version=" + detectedVoicechatVersion + "). Initializing integration...");

            Class<?> voicechatCls = Class.forName("de.maxhenkel.voicechat.Voicechat");
            Field serverField = voicechatCls.getField("SERVER");
            serverVoiceEvents = serverField.get(null);
            if (serverVoiceEvents == null) {
                diag("SvcBridge: Voicechat.SERVER field is null");
                botc.LOGGER.warn("[Voice] Mod detected (version=" + detectedVoicechatVersion + ") but SERVER field was null; integration disabled.");
                return;
            }
            Method getServer = serverVoiceEvents.getClass().getMethod("getServer");
            voiceServer = getServer.invoke(serverVoiceEvents);
            if (voiceServer == null) {
                diag("SvcBridge: getServer() returned null");
                botc.LOGGER.warn("[Voice] Mod detected (version=" + detectedVoicechatVersion + ") but getServer() returned null; integration disabled.");
                return;
            }

            Method getGroupManager = voiceServer.getClass().getMethod("getGroupManager");
            serverGroupManager = getGroupManager.invoke(voiceServer);
            if (serverGroupManager == null) {
                diag("SvcBridge: getGroupManager() returned null");
                botc.LOGGER.warn("[Voice] Mod detected (version=" + detectedVoicechatVersion + ") but group manager missing; integration disabled.");
                return;
            }

            Method getPlayerStateManager = voiceServer.getClass().getMethod("getPlayerStateManager");
            playerStateManager = getPlayerStateManager.invoke(voiceServer);
            if (playerStateManager == null) {
                diag("SvcBridge: getPlayerStateManager() returned null");
                botc.LOGGER.warn("[Voice] Mod detected (version=" + detectedVoicechatVersion + ") but player state manager missing; integration disabled.");
                return;
            }

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

            // Player state methods
            for (Method m : playerStateManager.getClass().getMethods()) {
                if (m.getName().equals("getState") && m.getParameterCount()==1) psGetState = m;
                if (m.getName().equals("setGroup") && m.getParameterCount()==2) psSetGroup = m;
                if (m.getName().equals("broadcastState") && m.getParameterCount()==2) psBroadcastState = m;
                if (m.getName().equals("broadcastRemoveState") && m.getParameterCount()==1) psBroadcastRemoveState = m;
                if (m.getName().equals("defaultDisconnectedState") && m.getParameterCount()==1) psDefaultDisconnectedState = m;
            }

            // Determine availability: minimal requirement is getGroups present
            available = gmGetGroups != null;
            if (available) {
                botc.LOGGER.info("[Voice] Simple Voice Chat integration enabled (version=" + detectedVoicechatVersion + ").");
            } else {
                botc.LOGGER.warn("[Voice] Simple Voice Chat detected (version=" + detectedVoicechatVersion + ") but required methods missing; voice features disabled.");
            }
            diag("SvcBridge: initialization " + (available ? "succeeded" : "incomplete"));
        } catch (Throwable t) {
            diag("SvcBridge: initialization error: " + t);
            botc.LOGGER.warn("[Voice] Initialization error; voice features disabled: " + t.getMessage());
        } finally {
            initializing = false;
        }
    }

    private static boolean voiceConfigLoaded = false;
    private static boolean suppressGroupCreationWarn = false;

    private static void loadVoiceConfigIfNeeded() {
        if (voiceConfigLoaded) return;
        voiceConfigLoaded = true;
        try {
            java.nio.file.Path botcCfg = VoiceRegionService.botcConfigRoot();
            java.nio.file.Path voiceCfgDir = botcCfg.resolve("voice");
            java.nio.file.Files.createDirectories(voiceCfgDir);
            java.io.File f = voiceCfgDir.resolve("botc-voice.properties").toFile();
            java.util.Properties props = new java.util.Properties();
            if (f.exists()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                    props.load(in);
                }
            } else {
                // create with default values
                props.setProperty("suppress_group_creation_warn", "true");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    props.store(out, "BOTC voice integration settings");
                }
            }
            suppressGroupCreationWarn = Boolean.parseBoolean(props.getProperty("suppress_group_creation_warn", "true"));
        } catch (Throwable t) {
            botc.LOGGER.debug("[Voice] Failed to load voice config: {}", t.toString());
        }
    }

    private static void markGroupCreationUnavailable(String reason) {
        loadVoiceConfigIfNeeded();
        if (groupCreationUnavailable) return;
        groupCreationUnavailable = true;
        if (!groupCreationWarned) {
            groupCreationWarned = true;
            if (suppressGroupCreationWarn) {
                botc.LOGGER.info("[Voice] Group auto-creation disabled (suppressed warn): {}", reason);
            } else {
                botc.LOGGER.warn("[Voice] Unable to create voice chat groups automatically: {}", reason);
            }
            diagnostics.add("SvcBridge: group creation disabled (" + reason + ")");
        }
    }

    private static void clearPasswordAndOpen(Object group) {
        if (group == null) return;
        boolean passwordCleared = false;
        boolean openForced = false;
        boolean hiddenCleared = false;
        boolean persistentForced = false;
        boolean typeForced = false;
        try {
            if (groupSetOpen != null) {
                try { groupSetOpen.invoke(group, true); openForced = true; } catch (Throwable ignored) {}
            }
            if (groupSetPassword != null) {
                try { groupSetPassword.invoke(group, (Object) null); passwordCleared = true; } catch (Throwable ignored) {}
            }
            Class<?> cls = group.getClass();
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    String fname = f.getName().toLowerCase();
                    Class<?> ft = f.getType();
                    if (ft == String.class && (fname.contains("pass") || fname.contains("password") || fname.contains("pwd"))) {
                        f.set(group, null);
                        passwordCleared = true;
                    } else if ((ft == boolean.class || ft == Boolean.class) && fname.contains("open")) {
                        f.set(group, true);
                        openForced = true;
                    } else if ((ft == boolean.class || ft == Boolean.class) && fname.contains("hidden")) {
                        f.set(group, false);
                        hiddenCleared = true;
                    } else if ((ft == boolean.class || ft == Boolean.class) && fname.contains("persist")) {
                        f.set(group, true);
                        persistentForced = true;
                    } else if (groupTypeField != null && f.equals(groupTypeField) && groupTypeOpenConstant != null) {
                        f.set(group, groupTypeOpenConstant);
                        typeForced = true;
                    }
                } catch (Throwable ignored) {}
            }
            for (Method m : group.getClass().getMethods()) {
                try {
                    String name = m.getName().toLowerCase();
                    if (name.contains("setopen") && m.getParameterCount()==1 && (m.getParameterTypes()[0]==boolean.class || m.getParameterTypes()[0]==Boolean.class)) {
                        m.invoke(group, true);
                        openForced = true;
                    } else if (name.contains("password") && m.getParameterCount()==1 && m.getParameterTypes()[0]==String.class) {
                        m.invoke(group, (Object) null);
                        passwordCleared = true;
                    } else if (name.contains("hidden") && m.getParameterCount()==1 && (m.getParameterTypes()[0]==boolean.class || m.getParameterTypes()[0]==Boolean.class)) {
                        m.invoke(group, false);
                        hiddenCleared = true;
                    } else if (name.contains("persist") && m.getParameterCount()==1 && (m.getParameterTypes()[0]==boolean.class || m.getParameterTypes()[0]==Boolean.class)) {
                        m.invoke(group, true);
                        persistentForced = true;
                    }
                } catch (Throwable ignored) {}
            }
            if (groupTypeField != null && groupTypeOpenConstant != null && !typeForced) {
                try {
                    groupTypeField.setAccessible(true);
                    groupTypeField.set(group, groupTypeOpenConstant);
                    typeForced = true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            diag("SvcBridge: clearPasswordAndOpen failed: " + t.getMessage());
            return;
        }
        if (passwordCleared || openForced || hiddenCleared || persistentForced || typeForced) {
            botc.LOGGER.debug("[Voice] Sanitized group {} (pwCleared={}, open={}, hidden={}, persistent={}, typeOpen={})",
                    describeGroup(group), passwordCleared, openForced, hiddenCleared, persistentForced, typeForced);
        }
    }

    private static boolean groupIntrospectionReady = false;

    private static void ensureGroupIntrospection(Object sampleGroup) {
        if (groupIntrospectionReady || sampleGroup == null) return;
        Class<?> cls = sampleGroup.getClass();
        try {
            if (groupGetName == null) {
                try { groupGetName = cls.getMethod("getName"); } catch (Throwable ignored) {}
                if (groupGetName == null) {
                    try { groupGetName = cls.getMethod("name"); } catch (Throwable ignored) {}
                }
            }
            if (groupGetId == null) {
                try { groupGetId = cls.getMethod("getId"); } catch (Throwable ignored) {}
            }
            if (groupPersistentField == null) {
                try { groupPersistentField = cls.getDeclaredField("persistent"); groupPersistentField.setAccessible(true); } catch (Throwable ignored) {}
            }
            if (groupGetPassword == null) {
                try { groupGetPassword = cls.getMethod("getPassword"); } catch (Throwable ignored) {}
            }
            if (groupSetPassword == null) {
                try { groupSetPassword = cls.getMethod("setPassword", String.class); } catch (Throwable ignored) {}
            }
            if (groupSetOpen == null) {
                try { groupSetOpen = cls.getMethod("setOpen", boolean.class); } catch (Throwable ignored) {}
            }
            if (groupNameField == null) {
                try { Field f = cls.getDeclaredField("name"); f.setAccessible(true); groupNameField = f; } catch (Throwable ignored) {}
            }
            groupIntrospectionReady = (groupGetName != null && groupGetId != null) || groupNameField != null;
        } catch (Throwable t) {
            diag("SvcBridge: ensureGroupIntrospection error: " + t.getMessage());
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
                    if (e.getKey() instanceof UUID u) {
                        Object group = e.getValue();
                        if (group != null) ensureGroupIntrospection(group);
                        out.put(u, group);
                    }
                }
                return out;
            }
        } catch (Throwable t) { diag("SvcBridge: rawGroups error: " + t); }
        return Collections.emptyMap();
    }

    public static Object getGroupById(UUID id) {
        if (gmGetGroup == null || id == null) return null;
        try {
            Object group = gmGetGroup.invoke(serverGroupManager, id);
            if (group != null) ensureGroupIntrospection(group);
            return group;
        } catch (Throwable t) {
            diag("SvcBridge: getGroupById error: " + t.getMessage());
            return null;
        }
    }

    public static void markPersistent(Object group) {
        if (group == null) return;
        if (groupPersistentField != null) {
            try {
                groupPersistentField.set(group, true);
                return;
            } catch (Throwable ignored) {}
        }
        for (Method m : group.getClass().getMethods()) {
            if (m.getParameterCount() == 1 && (m.getName().equalsIgnoreCase("setPersistent") || m.getName().equalsIgnoreCase("persistent"))) {
                try {
                    m.invoke(group, true);
                    return;
                } catch (Throwable ignored) {}
            }
        }
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
        if (name == null) return null;
        for (Object g : rawGroups().values()) {
            String found = extractGroupName(g);
            if (found != null && found.equalsIgnoreCase(name)) return g;
        }
        return null;
    }
    /** Public helper to resolve an existing group's UUID by its name, or null if not found/voice unavailable. */
    public static java.util.UUID resolveGroupIdByName(String name) {
        if (name == null || name.isEmpty() || !isAvailableRuntime()) return null;
        Object g = findGroupByName(name);
        if (g == null && aliasGroups.containsKey(name)) {
            return aliasGroups.get(name);
        }
        return g == null ? null : getGroupId(g);
    }
    private static java.util.UUID getGroupId(Object group) {
        if (group == null) return null;
        try {
            if (groupGetId != null) {
                Object res = groupGetId.invoke(group);
                if (res instanceof UUID u) return u;
            }
        } catch (Throwable ignored) {}
        return coerceUuid(group);
    }

    private static String describeGroup(Object group) {
        if (group == null) return "<null>";
        String name = extractGroupName(group);
        UUID id = getGroupId(group);
        if (name != null && id != null) return name + "/" + id;
        if (name != null) return name;
        if (id != null) return id.toString();
        return group.getClass().getSimpleName();
    }

    private static void setGroupNameIfPossible(Object group, String desiredName) {
        if (group == null || desiredName == null || desiredName.isEmpty()) return;
        if (groupNameField != null) {
            try { groupNameField.setAccessible(true); groupNameField.set(group, desiredName); return; } catch (Throwable ignored) {}
        }
        try {
            Method setter = group.getClass().getMethod("setName", String.class);
            setter.invoke(group, desiredName);
            return;
        } catch (Throwable ignored) {}
        for (Field f : group.getClass().getDeclaredFields()) {
            if (f.getType() == String.class && f.getName().toLowerCase().contains("name")) {
                try {
                    f.setAccessible(true);
                    f.set(group, desiredName);
                    return;
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Creates a group if missing, returning UUID (or null on failure). */
    public static UUID createOrGetGroup(String desiredName, ServerPlayerEntity creator) {
        if (desiredName == null || desiredName.isEmpty()) return null;
        if (!isAvailableRuntime()) return null;
        // Fast path: already exists
        Object existingFast = findGroupByName(desiredName);
        if (existingFast != null) return getGroupId(existingFast);
        if (aliasGroups.containsKey(desiredName)) {
            UUID mapped = aliasGroups.get(desiredName);
            Object aliasGroup = getGroupById(mapped);
            if (aliasGroup != null) return mapped;
        }
        if (groupCreationUnavailable || failedCreationNames.contains(desiredName)) return null;

        // Snapshot BEFORE we attempt anything for later diff salvage
        Map<UUID, Object> before = rawGroups();

        // --- Attempt via GroupImpl path (unchanged) ---
        if (creator != null && groupImplCreate != null && psGetState != null) {
            try {
                Object state = psGetState.invoke(playerStateManager, creator.getUuid());
                if (state != null) {
                    Object groupImpl = groupImplCreate.invoke(null, state);
                    if (groupImpl != null && groupImplGetGroup != null) {
                        Object group = groupImplGetGroup.invoke(groupImpl);
                        if (group != null) {
                            // try to name group
                            try { for (Field f : group.getClass().getDeclaredFields()) { if (f.getType()==String.class && f.getName().equalsIgnoreCase("name")) { f.setAccessible(true); f.set(group, desiredName); break; } } } catch (Throwable ignored) {}
                            markPersistent(group);
                            clearPasswordAndOpen(group);
                            if (gmAddGroup != null) gmAddGroup.invoke(serverGroupManager, group, (Object) null);
                            Object after = findGroupByName(desiredName);
                            if (after != null) return getGroupId(after);
                        }
                    }
                }
            } catch (Throwable t) { diag("SvcBridge: group creation via GroupImpl failed: " + t); }
        }

        // --- Constructor fallback ---
        try {
            Class<?> serverGroupCls = Class.forName("de.maxhenkel.voicechat.voice.server.Group");
            for (var ctor : serverGroupCls.getDeclaredConstructors()) {
                try {
                    Class<?>[] params = ctor.getParameterTypes();
                    Object[] args = new Object[params.length];
                    UUID newId = UUID.randomUUID();
                    int stringCount = 0; int booleanCount = 0;
                    for (int i=0;i<params.length;i++) {
                        Class<?> pt = params[i];
                        if (UUID.class.isAssignableFrom(pt)) args[i] = newId;
                        else if (pt==String.class) { args[i] = (stringCount==0? desiredName : null); stringCount++; }
                        else if (pt.isEnum()) { Object[] ec = pt.getEnumConstants(); args[i] = (ec!=null && ec.length>0)? ec[0] : null; }
                        else if (pt==boolean.class || pt==Boolean.class) { args[i] = (booleanCount==0); booleanCount++; }
                        else if (Number.class.isAssignableFrom(pt) || pt.isPrimitive()) { args[i] = 0; }
                        else args[i] = null;
                    }
                    ctor.setAccessible(true);
                    Object groupObj = ctor.newInstance(args);
                    if (groupObj != null && gmAddGroup != null) {
                        setGroupNameIfPossible(groupObj, desiredName);
                        markPersistent(groupObj);
                        clearPasswordAndOpen(groupObj);
                        gmAddGroup.invoke(serverGroupManager, groupObj, (Object) null);
                        Object after = findGroupByName(desiredName);
                        if (after != null) return getGroupId(after);
                        UUID fallback = getGroupId(groupObj);
                        if (fallback != null) { aliasGroups.put(desiredName, fallback); return fallback; }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) { diag("SvcBridge: constructor fallback failed: " + t); }

        // --- Salvage Phase: check if any new groups appeared even though we didn't find by name ---
        Map<UUID, Object> afterAll = rawGroups();
        List<UUID> newIds = new ArrayList<>();
        for (UUID id : afterAll.keySet()) if (!before.containsKey(id)) newIds.add(id);
        if (!newIds.isEmpty()) {
            // Keep the first, remove extras to prevent 4 duplicate groups
            UUID keep = newIds.get(0);
            for (int i=1;i<newIds.size();i++) removeGroup(newIds.get(i));
            Object keptGroup = getGroupById(keep);
            // Attempt to rename kept group to desiredName if possible
            if (keptGroup != null) {
                setGroupNameIfPossible(keptGroup, desiredName);
                markPersistent(keptGroup); clearPasswordAndOpen(keptGroup);
            }
            aliasGroups.put(desiredName, keep);
            diag("SvcBridge: salvaged newly created group for '"+desiredName+"' -> " + keep + " (deduped " + newIds.size() + " total)");
            return keep;
        }

        // Final failure
        diag("SvcBridge: unable to create group '" + desiredName + "' (no builder available)");
        failedCreationNames.add(desiredName);
        markGroupCreationUnavailable("no compatible builder/constructor (voicechat " + (detectedVoicechatVersion==null?"unknown":detectedVoicechatVersion) + ")");
        return null;
    }

    /** Join group by name (create if absent). */
    public static boolean joinGroupByName(ServerPlayerEntity player, String groupName) {
        if (player == null || groupName == null || groupName.isEmpty()) return false;
        if (!isAvailableRuntime()) return false;
        if (shouldSkipJoinAttempt(groupName)) return false; // honor backoff silently
        Object group = findGroupByName(groupName);
        if (group == null && aliasGroups.containsKey(groupName)) {
            group = getGroupById(aliasGroups.get(groupName));
        }
        UUID gid = group == null ? null : getGroupId(group);

        // If suppressed but now exists, unsuppress to allow join
        if (suppressedGroups.contains(groupName) && group != null) {
            unsuppressGroup(groupName);
        }

        // If group missing and creation disabled or previously failed, attempt fallback alias first
        if (group == null && (groupCreationUnavailable || failedCreationNames.contains(groupName))) {
            Map<UUID,Object> groups = rawGroups();
            for (Map.Entry<UUID,Object> e : groups.entrySet()) {
                group = e.getValue();
                gid = e.getKey();
                aliasGroups.put(groupName, gid);
                diag("SvcBridge: fallback aliased '" + groupName + "' -> existing group UUID=" + gid);
                break;
            }
        }
        // Attempt creation if still missing and creation not disabled
        if (group == null && !groupCreationUnavailable) {
            UUID created = createOrGetGroup(groupName, player);
            if (created != null) {
                group = getGroupById(created);
                gid = created;
            }
        }
        if (group == null || gid == null) {
            recordUnresolved(groupName);
            return false;
        }
        // Try authoritative join
        boolean joined = false;
        if (gmJoinGroup != null) {
            try {
                ensureAuthoritativeCleared(gid, player);
                gmJoinGroup.invoke(serverGroupManager, group, player, (Object) null);
                joined = true;
            } catch (Throwable t) {
                diag("SvcBridge: gmJoinGroup fallback failed for '" + groupName + "': " + t.getMessage());
            }
        }
        UUID current = getPlayerGroupId(player);
        if (!gid.equals(current)) {
            try {
                if (psSetGroup != null) {
                    psSetGroup.invoke(playerStateManager, player, gid);
                    if (psGetState != null && psBroadcastState != null) {
                        Object state = psGetState.invoke(playerStateManager, player.getUuid());
                        if (state != null) psBroadcastState.invoke(playerStateManager, player, state);
                    }
                    joined = true;
                    diag("SvcBridge: psSetGroup fallback applied for '" + groupName + "' -> " + gid);
                }
            } catch (Throwable t) {
                diag("SvcBridge: psSetGroup fallback error: " + t.getMessage());
            }
        }
        return joined;
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
             String n = extractGroupName(g);
             if (n != null) names.add(n);
         }
         for (Map.Entry<String,UUID> e : aliasGroups.entrySet()) {
             Object g = SvcBridge.getGroupById(e.getValue());
             String realName = extractGroupName(g);
             if (realName == null || !realName.equalsIgnoreCase(e.getKey())) {
                 names.add(e.getKey() + " (alias)");
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

    // Helper: remove a group by UUID via reflected manager (used in salvage dedup)
    private static boolean removeGroup(UUID id) {
        if (id == null || gmRemoveGroup == null || !isAvailableRuntime()) return false;
        try {
            gmRemoveGroup.invoke(serverGroupManager, id);
            return true;
        } catch (Throwable t) {
            diag("SvcBridge: removeGroup failed during salvage for " + id + ": " + t.getMessage());
            return false;
        }
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

    public static List<String> getDiagnostics() { return java.util.List.copyOf(diagnostics); }

    public static boolean groupExists(String name) {
        if (name == null || name.isEmpty()) return false;
        // Quick check via raw groups
        if (!isAvailableRuntime()) return false;
        // Try name or alias
        try {
            Object g = findGroupByName(name);
            if (g != null) return true;
            if (aliasGroups.containsKey(name) && getGroupById(aliasGroups.get(name)) != null) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isGroupCreationDisabled() { return groupCreationUnavailable; }

    public static String getVoicechatVersion() { return detectedVoicechatVersion == null ? "unknown" : detectedVoicechatVersion; }

    public static boolean isGroupSuppressed(String name) { return suppressedGroups.contains(name); }
    public static void unsuppressGroup(String name) { suppressedGroups.remove(name); unresolvedGroupAttempts.remove(name); currentBackoffMs.remove(name); nextJoinAttemptMs.remove(name); }

    public static boolean shouldSkipJoinAttempt(String groupName) {
        if (!suppressedGroups.contains(groupName)) return false;
        long now = System.currentTimeMillis();
        Long next = nextJoinAttemptMs.get(groupName);
        return next != null && now < next;
    }

    private static void scheduleNextAttempt(String groupName) {
        long prev = currentBackoffMs.getOrDefault(groupName, BACKOFF_INITIAL_MS);
        long nextDelay = Math.min(prev * 2L, BACKOFF_MAX_MS); // exponential backoff
        currentBackoffMs.put(groupName, nextDelay);
        nextJoinAttemptMs.put(groupName, System.currentTimeMillis() + nextDelay);
    }

    private static void recordUnresolved(String groupName) {
        int n = unresolvedGroupAttempts.getOrDefault(groupName, 0) + 1;
        unresolvedGroupAttempts.put(groupName, n);
        if (suppressedGroups.contains(groupName)) {
            // Only log periodically; extend backoff each unresolved attempt beyond schedule
            if (!shouldSkipJoinAttempt(groupName)) {
                if (n % SUPPRESS_RELOG_INTERVAL == 0) {
                    diag("SvcBridge: group '" + groupName + "' still unresolved after " + n + " attempts (suppressed, backoff=" + currentBackoffMs.getOrDefault(groupName, 0L) + "ms)");
                }
                scheduleNextAttempt(groupName);
            }
            return;
        }
        if (n <= SUPPRESS_THRESHOLD) {
            diag("SvcBridge: joinGroupByName unable to resolve group '" + groupName + "' (attempt=" + n + ")");
        } else {
            suppressedGroups.add(groupName);
            scheduleNextAttempt(groupName);
            diag("SvcBridge: suppressing further unresolved logs for group '" + groupName + "' (initial backoff=" + BACKOFF_INITIAL_MS + "ms)");
        }
    }

    private static String extractGroupName(Object group) {
        if (group == null) return null;
        try {
            if (groupGetName != null) {
                Object n = groupGetName.invoke(group);
                if (n != null) return n.toString();
            }
        } catch (Throwable ignored) {}
        try {
            Method m = group.getClass().getMethod("getName");
            Object n = m.invoke(group);
            if (n != null) return n.toString();
        } catch (Throwable ignored) {}
        try {
            Method m = group.getClass().getMethod("name");
            Object n = m.invoke(group);
            if (n != null) return n.toString();
        } catch (Throwable ignored) {}
        for (Field f : group.getClass().getDeclaredFields()) {
            if (f.getType() == String.class) {
                String lower = f.getName().toLowerCase();
                if (lower.contains("name") || lower.contains("label") || lower.contains("title")) {
                    try {
                        f.setAccessible(true);
                        Object n = f.get(group);
                        if (n != null) return n.toString();
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }
 }
