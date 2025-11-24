package golden.botc_mc.botc_mc.game.voice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import golden.botc_mc.botc_mc.botc;

/**
 * Reflection integration bridge for the Simple Voice Chat mod. Discovers runtime classes & methods,
 * exposes minimal higher-level helpers (group create/join/leave, password clearing, presence checks)
 * while avoiding a hard compile-time dependency. All operations are best-effort and fail soft.
 * <p>
 * <b>Caution:</b> Because this relies on reflection across multiple potential versions of the
 * voice chat mod, callers must tolerate null/false returns. Every public method below documents
 * its parameters and return semantics explicitly.
 */
public final class SvcBridge {
    private static boolean available = false;
    private static boolean initializing = false;
    // NEW: flag to mark that the voice chat mod is not present; prevents repeated init spam
    private static boolean permanentlyMissing = false;
    private static int initAttempts = 0;
    // Added missing version field
    private static String detectedVoicechatVersion = null;

    private static Object serverGroupManager;           // de.maxhenkel.voicechat.voice.server.ServerGroupManager instance
    private static Object playerStateManager;           // de.maxhenkel.voicechat.voice.server.PlayerStateManager instance

    private static Method gmGetGroups;                  // ServerGroupManager.getGroups()
    private static Method gmAddGroup;                   // ServerGroupManager.addGroup(Group, ServerPlayerEntity)
    private static Method gmJoinGroup;                  // ServerGroupManager.joinGroup(Group, ServerPlayerEntity, String)
    private static Method gmLeaveGroup;                 // ServerGroupManager.leaveGroup(ServerPlayerEntity)
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
    private static Field groupNameField; // reintroduced for naming groups reflectively

    private static final Map<String, UUID> aliasGroups = new HashMap<>();
    private static boolean groupCreationUnavailable = false;
    // removed unused groupCreationWarned
    private static final Set<String> failedCreationNames = new HashSet<>(); // names for which creation already failed

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
    }

    /** Utility class; no instances. */
    private SvcBridge() {}

    /** Attempt lazy initialization and report availability.
     * @return true if integration ready after attempting init
     */
    public static boolean isAvailableRuntime() {
        if (permanentlyMissing) return false;
        if (!available) attemptInit();
        return available;
    }

    /** Indicates whether player's voice state appears connected.
     * @param player server player entity (nullable)
     * @return true if considered connected or unknown (never blocks gameplay)
     */
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
                    if ((n.contains("disconnect") || n.equals("disconnected")) && isBooleanType(f.getType())) {
                        Object v = f.get(state);
                        if (v instanceof Boolean b) return !b;
                    }
                    if (n.contains("disabled") && isBooleanType(f.getType())) {
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
                FabricLoader.getInstance().getModContainer("voicechat").ifPresent(mc -> detectedVoicechatVersion = mc.getMetadata().getVersion().getFriendlyString());
            } catch (Throwable ignored) {}
            if (detectedVoicechatVersion == null) detectedVoicechatVersion = "unknown";
            botc.LOGGER.info("[Voice] Simple Voice Chat mod detected (version={}). Initializing integration...", detectedVoicechatVersion);

            Class<?> voicechatCls = Class.forName("de.maxhenkel.voicechat.Voicechat");
            Field serverField = voicechatCls.getField("SERVER");
            // de.maxhenkel.voicechat.voice.server.ServerVoiceEvents instance
            Object serverVoiceEvents = serverField.get(null);
            if (serverVoiceEvents == null) {
                diag("SvcBridge: Voicechat.SERVER field is null");
                botc.LOGGER.warn("[Voice] Mod detected (version={}) but SERVER field was null; integration disabled.", detectedVoicechatVersion);
                return;
            }
            Method getServer = serverVoiceEvents.getClass().getMethod("getServer");
            // de.maxhenkel.voicechat.voice.server.Server instance
            Object voiceServer = getServer.invoke(serverVoiceEvents);
            if (voiceServer == null) {
                diag("SvcBridge: getServer() returned null");
                botc.LOGGER.warn("[Voice] Mod detected (version={}) but getServer() returned null; integration disabled.", detectedVoicechatVersion);
                return;
            }

            Method getGroupManager = voiceServer.getClass().getMethod("getGroupManager");
            serverGroupManager = getGroupManager.invoke(voiceServer);
            if (serverGroupManager == null) {
                diag("SvcBridge: getGroupManager() returned null");
                botc.LOGGER.warn("[Voice] Mod detected (version={}) but group manager missing; integration disabled.", detectedVoicechatVersion);
                return;
            }

            Method getPlayerStateManager = voiceServer.getClass().getMethod("getPlayerStateManager");
            playerStateManager = getPlayerStateManager.invoke(voiceServer);
            if (playerStateManager == null) {
                diag("SvcBridge: getPlayerStateManager() returned null");
                botc.LOGGER.warn("[Voice] Mod detected (version={}) but player state manager missing; integration disabled.", detectedVoicechatVersion);
                return;
            }

            // Resolve group manager methods
            for (Method m : serverGroupManager.getClass().getMethods()) {
                switch (m.getName()) {
                    case "getGroups" -> gmGetGroups = m;
                    case "addGroup" -> { if (m.getParameterCount()==2) gmAddGroup = m; }
                    case "joinGroup" -> { if (m.getParameterCount()==3) gmJoinGroup = m; }
                    case "leaveGroup" -> { if (m.getParameterCount()==1) gmLeaveGroup = m; }
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
                botc.LOGGER.info("[Voice] Simple Voice Chat integration enabled (version={}).", detectedVoicechatVersion);
            } else {
                botc.LOGGER.warn("[Voice] Simple Voice Chat detected (version={}) but required methods missing; voice features disabled.", detectedVoicechatVersion);
            }
            diag("SvcBridge: initialization " + (available ? "succeeded" : "incomplete"));
        } catch (Throwable t) {
            diag("SvcBridge: initialization error: " + t);
            botc.LOGGER.warn("[Voice] Initialization error; voice features disabled: {}", t.getMessage());
        } finally {
            initializing = false;
        }
    }

    private static boolean voiceConfigLoaded = false;

    private static java.util.Properties loadVoiceConfigProperties(java.io.File file) throws java.io.IOException {
        java.util.Properties props = new java.util.Properties();
        if (file.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                props.load(in);
            }
        } else {
            props.setProperty("suppress_group_creation_warn", "true");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                props.store(out, "BOTC voice integration settings");
            }
        }
        return props;
    }

    private static void loadVoiceConfigIfNeeded() {
        if (voiceConfigLoaded) return;
        voiceConfigLoaded = true;
        try {
            java.nio.file.Path botcCfg = VoiceRegionService.botcConfigRoot();
            java.nio.file.Path voiceCfgDir = botcCfg.resolve("voice");
            java.nio.file.Files.createDirectories(voiceCfgDir);
            java.io.File f = voiceCfgDir.resolve("botc-voice.properties").toFile();
            java.util.Properties ignoredProps = loadVoiceConfigProperties(f); // renamed to avoid unused warning
            // suppressGroupCreationWarn = Boolean.parseBoolean(props.getProperty("suppress_group_creation_warn", "true"));
        } catch (Throwable t) {
            botc.LOGGER.debug("[Voice] Failed to load voice config: {}", t.toString());
        }
    }

    private static void markGroupCreationUnavailable() {
        loadVoiceConfigIfNeeded();
        if (groupCreationUnavailable) return;
        groupCreationUnavailable = true;
        botc.LOGGER.info("[Voice] Group auto-creation disabled (no compatible constructor)");
    }

    private static String describeGroup(Object group) {
        if (group == null) return "<null>";
        String name = null;
        try { if (groupGetName != null) { Object n = groupGetName.invoke(group); if (n != null) name = n.toString(); } } catch (Throwable ignored) {}
        if (name == null) { try { var m = group.getClass().getMethod("getName"); Object n = m.invoke(group); if (n != null) name = n.toString(); } catch (Throwable ignored) {} }
        UUID id = getGroupId(group);
        if (name != null && id != null) return name + "/" + id;
        if (name != null) return name;
        if (id != null) return id.toString();
        return group.getClass().getSimpleName();
    }
    private static UUID getGroupId(Object group) {
        if (group == null) return null;
        try {
            if (groupGetId != null) {
                Object r = groupGetId.invoke(group);
                if (r instanceof UUID u) return u;
            }
        } catch (Throwable ignored) {}
        return coerceUuid(group);
    }
    private static void setGroupNameIfPossible(Object group, String desiredName) {
        if (group == null || desiredName == null || desiredName.isEmpty()) return;
        if (groupNameField != null) {
            try { groupNameField.setAccessible(true); groupNameField.set(group, desiredName); return; } catch (Throwable ignored) {}
        }
        try { Method m = group.getClass().getMethod("setName", String.class); m.invoke(group, desiredName); return; } catch (Throwable ignored) {}
        for (Field f : group.getClass().getDeclaredFields()) {
            if (f.getType() == String.class && f.getName().toLowerCase().contains("name")) {
                try { f.setAccessible(true); f.set(group, desiredName); return; } catch (Throwable ignored) {}
            }
        }
    }

    private static void clearPasswordAndOpen(Object group) {
        if (group == null) return;
        boolean passwordCleared = false;
        boolean openForced = false;
        boolean hiddenCleared = false;
        boolean persistentForced = false;
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
                    } else if (isBooleanType(ft) && fname.contains("open")) {
                        f.set(group, true);
                        openForced = true;
                    } else if (isBooleanType(ft) && fname.contains("hidden")) {
                        f.set(group, false);
                        hiddenCleared = true;
                    } else if (isBooleanType(ft) && fname.contains("persist")) {
                        f.set(group, true);
                        persistentForced = true;
                    }
                } catch (Throwable ignored) {}
            }
            for (Method m : group.getClass().getMethods()) {
                try {
                    String name = m.getName().toLowerCase();
                    if (name.contains("setopen") && m.getParameterCount()==1 && isBooleanType(m.getParameterTypes()[0])) {
                        m.invoke(group, true);
                        openForced = true;
                    } else if (name.contains("password") && m.getParameterCount()==1 && m.getParameterTypes()[0]==String.class) {
                        m.invoke(group, (Object) null);
                        passwordCleared = true;
                    } else if (name.contains("hidden") && m.getParameterCount()==1 && isBooleanType(m.getParameterTypes()[0])) {
                        m.invoke(group, false);
                        hiddenCleared = true;
                    } else if (name.contains("persist") && m.getParameterCount()==1 && isBooleanType(m.getParameterTypes()[0])) {
                        m.invoke(group, true);
                        persistentForced = true;
                    }
                } catch (Throwable ignored) {}
            }
            if (groupPersistentField != null) {
                try {
                    groupPersistentField.setAccessible(true);
                    groupPersistentField.set(group, true);
                    persistentForced = true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            diag("SvcBridge: clearPasswordAndOpen failed: " + t.getMessage());
            return;
        }
        if (passwordCleared || openForced || hiddenCleared || persistentForced) {
            botc.LOGGER.debug("[Voice] Sanitized group {} (pwCleared={}, open={}, hidden={}, persistent={})",
                    describeGroup(group), passwordCleared, openForced, hiddenCleared, persistentForced);
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

    /** Get group by UUID via reflected manager.
     * @param id group id
     * @return group object or null if not found/unavailable
     */
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

    /** Mark group persistent best-effort.
     * @param group group instance (ignored if null)
     */
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

    // Find group by name
    private static Object findGroupByName(String name) {
        if (name == null) return null;
        for (Object g : rawGroups().values()) {
            String found = extractGroupName(g);
            if (found != null && found.equalsIgnoreCase(name)) return g;
        }
        return null;
    }
    /**
     * Create the voice chat group with the given name if absent.
     * @param desiredName desired group name (non-empty)
     * @return UUID of created or existing group, or null on failure/unavailable
     */
    public static UUID createOrGetGroup(String desiredName) {
        if (desiredName == null || desiredName.isEmpty() || !isAvailableRuntime()) return null;
        Object existingFast = findGroupByName(desiredName);
        if (existingFast != null) return getGroupId(existingFast);
        if (aliasGroups.containsKey(desiredName)) {
            UUID mapped = aliasGroups.get(desiredName);
            if (getGroupById(mapped) != null) return mapped;
        }
        if (groupCreationUnavailable || failedCreationNames.contains(desiredName)) return null;
        try {
            Class<?> serverGroupCls = Class.forName("de.maxhenkel.voicechat.voice.server.Group");
            for (var ctor : serverGroupCls.getDeclaredConstructors()) {
                try {
                    Class<?>[] params = ctor.getParameterTypes();
                    Object[] args = new Object[params.length];
                    UUID newId = UUID.randomUUID();
                    int stringCount=0, booleanCount=0;
                    for (int i=0;i<params.length;i++) {
                        Class<?> pt = params[i];
                        if (UUID.class.isAssignableFrom(pt)) args[i]=newId;
                        else if (pt==String.class) { args[i] = (stringCount==0? desiredName : null); stringCount++; }
                        else if (pt.isEnum()) { Object[] ec = pt.getEnumConstants(); args[i] = (ec!=null && ec.length>0? ec[0]:null); }
                        else if (pt==boolean.class || pt==Boolean.class) { args[i] = (booleanCount==0); booleanCount++; }
                        else if (pt.isPrimitive() || Number.class.isAssignableFrom(pt)) args[i]=0; else args[i]=null;
                    }
                    ctor.setAccessible(true);
                    Object groupObj = ctor.newInstance(args);
                    if (gmAddGroup != null) {
                        setGroupNameIfPossible(groupObj, desiredName);
                        markPersistent(groupObj);
                        clearPasswordAndOpen(groupObj);
                        gmAddGroup.invoke(serverGroupManager, groupObj, (Object) null);
                        Object after = findGroupByName(desiredName);
                        if (after != null) return getGroupId(after);
                        UUID fb = getGroupId(groupObj);
                        if (fb != null) { aliasGroups.put(desiredName, fb); return fb; }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) { diag("SvcBridge: constructor fallback failed: " + t); }
        failedCreationNames.add(desiredName);
        markGroupCreationUnavailable();
        return null;
    }

    /** Join (or create then join) a group by name.
     * @param player player attempting to join
     * @param groupName target group name
     * @return true on best-effort success
     */
    public static boolean joinGroupByName(ServerPlayerEntity player, String groupName) {
        if (player == null || groupName == null || groupName.isEmpty()) return false;
        if (!isAvailableRuntime()) return false;
        Object group = findGroupByName(groupName);
        if (group == null && aliasGroups.containsKey(groupName)) group = getGroupById(aliasGroups.get(groupName));
        UUID gid = group == null ? null : getGroupId(group);
        // Attempt creation if missing
        if (group == null && !groupCreationUnavailable) {
            UUID created = createOrGetGroup(groupName);
            if (created != null) {
                group = getGroupById(created);
                gid = created;
            }
        }
        if (group == null || gid == null) {
            return false; // group still unresolved
        }
        boolean joined = false;
        if (gmJoinGroup != null) {
            try {
                gmJoinGroup.invoke(serverGroupManager, group, player, (Object) null);
                joined = true;
            } catch (Throwable ignored) {}
        }
        UUID current = getPlayerGroupId(player);
        if (!gid.equals(current) && psSetGroup != null) {
            try {
                psSetGroup.invoke(playerStateManager, player, gid);
                joined = true;
            } catch (Throwable ignored) {}
        }
        return joined;
    }

     /**
      * Request that the given player leave their current voice chat group. This method attempts several
      * strategies (group manager call, state clearing, broadcast) and includes retry logic to work
      * around transient state races in the voice chat server.
      * @param player target player
      * @return true if leave considered successful
      */
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

    /**
     * Clear password and force a group open by name, if integration is available. No-op if the
     * group cannot be found.
     *
     * @param groupName voice group name
     */
    public static void clearPasswordAndOpenByName(String groupName) {
        if (!isAvailableRuntime()) return;
        Object g = findGroupByName(groupName);
        if (g == null && aliasGroups.containsKey(groupName)) g = getGroupById(aliasGroups.get(groupName));
        if (g == null) return;
        clearPasswordAndOpen(g);
    }
    /** Clear password and open by UUID string.
     * @param idStr UUID string
     * @return true if group found & sanitized
     */
    public static boolean clearPasswordAndOpenByIdString(String idStr) {
        if (idStr == null) return false;
        try { return clearPasswordAndOpenById(UUID.fromString(idStr)); } catch (Throwable ignored) { return false; }
    }
    /** Clear password and open by UUID.
     * @param id group id
     * @return true if operation succeeded
     */
    public static boolean clearPasswordAndOpenById(UUID id) {
        if (!isAvailableRuntime() || id == null) return false;
        Object g = getGroupById(id);
        if (g == null) return false;
        clearPasswordAndOpen(g);
        return true;
    }

    /** Check if a group exists by name or alias.
     * @param name group name
     * @return true if present
     */
    public static boolean isGroupPresent(String name) {
        if (name == null || name.isEmpty() || !isAvailableRuntime()) return false;
        if (findGroupByName(name) != null) return true;
        UUID alias = aliasGroups.get(name);
        return alias != null && getGroupById(alias) != null;
    }
    /** Whether automatic voice group creation is disabled.
     * @return true if auto-creation disabled
     */
    public static boolean isGroupCreationDisabled() { return groupCreationUnavailable; }

    private static UUID coerceUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID u) return u;
        try {
            // If we have a cached getId method, try that first
            if (groupGetId != null) {
                try {
                    Object res = groupGetId.invoke(obj);
                    if (res instanceof UUID u2) return u2;
                } catch (Throwable ignored) {}
            }
            // Generic getId() method
            try {
                Method m = obj.getClass().getMethod("getId");
                Object res = m.invoke(obj);
                if (res instanceof UUID u3) return u3;
            } catch (Throwable ignored) {}
            // Fallback: scan fields for a UUID value
            for (Field f : obj.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof UUID u4) return u4;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // Extract a group UUID from a PlayerState-like object via getGroup() or a 'group' field.
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
                        Object v = f.get(group);
                        if (v != null) return v.toString();
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    /** Get the UUID of the group that the player is currently in, if any.
     * @param player target player (nullable)
     * @return group UUID or null if none/unavailable
     */
    public static UUID getPlayerGroupId(ServerPlayerEntity player) {
        if (!isAvailableRuntime() || player == null) return null;
        try {
            if (gmGetPlayerGroup != null) {
                Object g = gmGetPlayerGroup.invoke(serverGroupManager, player);
                UUID id = coerceUuid(g);
                if (id != null) return id;
            }
            if (psGetState != null) {
                Object state = psGetState.invoke(playerStateManager, player.getUuid());
                if (state != null) {
                    // try method getGroup()
                    try {
                        Method m = state.getClass().getMethod("getGroup");
                        Object r = m.invoke(state);
                        UUID coerced = coerceUuid(r);
                        if (coerced != null) return coerced;
                    } catch (Throwable ignored) {}
                    // scan fields named group or groupId
                    for (Field f : state.getClass().getDeclaredFields()) {
                        String n = f.getName().toLowerCase();
                        if (n.contains("group")) {
                            try { f.setAccessible(true); Object v = f.get(state); UUID coerced = coerceUuid(v); if (coerced != null) return coerced; } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable t) { diag("SvcBridge: getPlayerGroupId error: " + t.getMessage()); }
        return null;
    }

    private static boolean isBooleanType(Class<?> c) { return c == boolean.class || c == Boolean.class; }
}
