package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically checks players and assigns them to voice groups when they are inside regions.
 */
public class VoiceRegionTask implements Runnable {
    private MinecraftServer server; // restored mutable server reference
    private final VoiceRegionManager manager;
    private final Map<UUID, String> current = new HashMap<>();
    private final Map<UUID, Integer> pendingCleanup = new HashMap<>();
    private final Map<UUID, Integer> joinRetries = new HashMap<>();
    private final Map<UUID, Long> lastActionMs = new HashMap<>();
    private static final int MAX_PENDING_ATTEMPTS = 6;
    private static final int MAX_JOIN_ATTEMPTS = 4;
    private static final long ACTION_COOLDOWN_MS = 300;
    private static final long REGION_STABLE_MS = 500; // require region to be stable for smoother experience
    private final Map<UUID, String> lastDetectedRegion = new HashMap<>();
    private final Map<UUID, Long> lastDetectedRegionMs = new HashMap<>();
    private static volatile boolean DEBUG_TASK = false; // toggle via command later (default off)
    private static volatile boolean REQUIRE_STABILITY = true; // can disable to make more snappy
    private static volatile VoiceRegionTask INSTANCE; // singleton reference
    private static volatile boolean AUTOJOIN_ENABLED = true; // global toggle
    private static final Set<UUID> WATCH_PLAYERS = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>()); // players for verbose position/region watch

    public static void setDebugTask(boolean enabled) { DEBUG_TASK = enabled; }
    public static void setRequireStability(boolean enabled) { REQUIRE_STABILITY = enabled; }
    public static VoiceRegionTask getInstance() { return INSTANCE; }
    public static void setAutojoinEnabled(boolean enabled) { AUTOJOIN_ENABLED = enabled; }
    public static boolean isAutojoinEnabled() { return AUTOJOIN_ENABLED; }
    public static boolean addWatcher(UUID u) { return WATCH_PLAYERS.add(u); }
    public static boolean removeWatcher(UUID u) { return WATCH_PLAYERS.remove(u); }
    public static boolean isWatching(UUID u) { return WATCH_PLAYERS.contains(u); }
    public static Set<UUID> watchers() { return java.util.Collections.unmodifiableSet(WATCH_PLAYERS); }

    public VoiceRegionTask(MinecraftServer server, VoiceRegionManager manager) {
        this.server = server; // assign incoming server
        this.manager = manager;
        INSTANCE = this;
    }

    public void setServer(MinecraftServer srv) {
        this.server = srv;
    }

    public void resetAllTracking() {
        current.clear();
        pendingCleanup.clear();
        joinRetries.clear();
        lastActionMs.clear();
        lastDetectedRegion.clear();
        lastDetectedRegionMs.clear();
    }

    @Override
    public void run() {
        long nowMs = System.currentTimeMillis();
        if (server == null) return; // safety
        // Resolve the current manager: prefer active per-map, fallback to the constructed one
        VoiceRegionManager resolved = VoiceRegionService.getActiveManager();
        final VoiceRegionManager mgr = (resolved != null) ? resolved : this.manager;

        server.getPlayerManager().getPlayerList().forEach(p -> {
            try {
                UUID pu = p.getUuid();
                boolean watching = WATCH_PLAYERS.contains(pu);
                Long last = lastActionMs.get(pu);
                if (!watching && last != null && (nowMs - last) < ACTION_COOLDOWN_MS) {
                    if (DEBUG_TASK) botc.LOGGER.trace("VoiceRegionTask: cooldown skip for {} ({}ms)", p.getName().getString(), nowMs - last);
                    return;
                }

                if (SvcBridge.isAvailableRuntime() && !SvcBridge.isPlayerConnected(p)) {
                    if (DEBUG_TASK) botc.LOGGER.trace("VoiceRegionTask: player {} not yet connected to voice", p.getName().getString());
                    return;
                }

                VoiceRegion detected = mgr.regionForPlayer(p); // may log internally
                if (watching) {
                    // Build detailed watch log regardless of region presence
                    StringBuilder sb = new StringBuilder();
                    sb.append("WATCH player=").append(p.getName().getString())
                      .append(" pos=").append(p.getBlockX()).append(',').append(p.getBlockY()).append(',').append(p.getBlockZ());
                    if (detected != null) {
                        sb.append(" region=").append(detected.id).append(" group=").append(detected.groupName)
                          .append(" bounds=").append(detected.boundsDebug());
                    } else {
                        sb.append(" region=<none>");
                    }
                    sb.append(" allBounds=");
                    boolean first = true;
                    for (VoiceRegion r : mgr.list()) {
                        if (!first) sb.append(' '); else first = false;
                        sb.append(r.id).append(':').append(r.boundsDebug());
                    }
                    botc.LOGGER.info(sb.toString());
                }
                // If no region matched and debug enabled log once every few ticks (rate limit by cooldown logic already)
                if (detected == null && DEBUG_TASK && !watching) {
                    botc.LOGGER.trace("VoiceRegionTask: player {} in no voice region (blockPos={},{} ,{})", p.getName().getString(), p.getBlockX(), p.getBlockY(), p.getBlockZ());
                }
                String detectedName = detected == null ? null : detected.groupName;
                String previousDetected = lastDetectedRegion.get(pu);
                if ((previousDetected == null && detectedName != null) || (previousDetected != null && !previousDetected.equals(detectedName))) {
                    lastDetectedRegion.put(pu, detectedName);
                    lastDetectedRegionMs.put(pu, nowMs);
                    if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: raw region change {} -> {} for player {}", previousDetected, detectedName, p.getName().getString());
                }
                if (detectedName != null && REQUIRE_STABILITY) {
                    Long firstSeenMs = lastDetectedRegionMs.get(pu);
                    if (firstSeenMs == null || (nowMs - firstSeenMs) < REGION_STABLE_MS) {
                        if (DEBUG_TASK && !watching) botc.LOGGER.trace("VoiceRegionTask: waiting stability window for {} in region {} ({}ms)", p.getName().getString(), detectedName, firstSeenMs == null ? 0 : nowMs - firstSeenMs);
                        return;
                    }
                }

                if (pendingCleanup.containsKey(pu)) {
                    try {
                        UUID still = SvcBridge.getPlayerGroupId(p);
                        if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: pendingCleanup active for {} stillGroup={}", p.getName().getString(), still);
                        if (still == null) {
                            pendingCleanup.remove(pu);
                            if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: cleanup resolved for {}", p.getName().getString());
                        } else {
                            int att = pendingCleanup.getOrDefault(pu, 0);
                            if (att >= MAX_PENDING_ATTEMPTS) {
                                botc.LOGGER.warn("VoiceRegionTask: cleanup attempts exceeded for {} (group {})", p.getName().getString(), still);
                                pendingCleanup.remove(pu);
                            } else {
                                boolean left = SvcBridge.leaveGroup(p);
                                pendingCleanup.put(pu, att + 1);
                                lastActionMs.put(pu, System.currentTimeMillis());
                                if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: retry leave {} attempt={} success={}", p.getName().getString(), att + 1, left);
                            }
                        }
                    } catch (Throwable t) {
                        if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: pending cleanup error {}", t.toString());
                    }
                }

                String targetGroupName = detectedName;
                String previous = current.get(pu);
                UUID currentSvcGroup = SvcBridge.isAvailableRuntime() ? SvcBridge.getPlayerGroupId(p) : null;
                if (DEBUG_TASK && !watching) botc.LOGGER.trace("VoiceRegionTask: state player={} region={} trackedPrev={} svcCurrent={}", p.getName().getString(), targetGroupName, previous, currentSvcGroup);

                if (targetGroupName == null && SvcBridge.isAvailableRuntime()) {
                    try {
                        if (currentSvcGroup != null) {
                            if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: leaving stale group for {} groupId={}", p.getName().getString(), currentSvcGroup);
                            boolean leftForced = SvcBridge.leaveGroup(p);
                            lastActionMs.put(pu, System.currentTimeMillis());
                            if (leftForced) {
                                current.remove(pu);
                            } else {
                                pendingCleanup.put(pu, 1);
                            }
                            return;
                        }
                    } catch (Throwable t) {
                        if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: stale leave error {}", t.toString());
                    }
                }

                if (previous == null && targetGroupName != null) {
                    if (!AUTOJOIN_ENABLED) {
                        if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: autojoin disabled, skipping join for {} -> {}", p.getName().getString(), targetGroupName);
                        current.put(pu, targetGroupName); // track presence even if not joining
                        return;
                    }
                    if (SvcBridge.isAvailableRuntime()) {
                        int attempts = joinRetries.getOrDefault(pu, 0);
                        if (attempts >= MAX_JOIN_ATTEMPTS) {
                            try { p.sendMessage(Text.literal("Voice region join failed repeatedly for " + targetGroupName), false); } catch (Throwable ignored) {}
                            return;
                        }
                        boolean joined = false;
                        try {
                            if (detected != null && detected.groupId != null) SvcBridge.clearPasswordAndOpenByIdString(detected.groupId);
                            else SvcBridge.clearPasswordAndOpenByName(targetGroupName);
                            joined = SvcBridge.joinGroupByName(p, targetGroupName);
                        } catch (Throwable t) {
                            botc.LOGGER.warn("VoiceRegionTask: join error {}", t.toString());
                        }
                        lastActionMs.put(pu, System.currentTimeMillis());
                        if (joined) {
                            current.put(pu, targetGroupName);
                            joinRetries.remove(pu);
                            if (DEBUG_TASK || watching) botc.LOGGER.info("VoiceRegionTask: JOIN success player={} group={}", p.getName().getString(), targetGroupName);
                        } else {
                            joinRetries.put(pu, attempts + 1);
                            if (DEBUG_TASK || watching) botc.LOGGER.info("VoiceRegionTask: JOIN failed player={} group={} attempt={}", p.getName().getString(), targetGroupName, attempts + 1);
                        }
                    } else {
                        current.put(pu, targetGroupName);
                    }
                    return;
                }

                if (previous != null && (targetGroupName == null || !targetGroupName.equals(previous))) {
                    if (!AUTOJOIN_ENABLED && targetGroupName != null) {
                        current.put(pu, targetGroupName);
                        if (DEBUG_TASK) botc.LOGGER.debug("VoiceRegionTask: autojoin disabled, tracking switch {} -> {} only", previous, targetGroupName);
                        return;
                    }
                    if (SvcBridge.isAvailableRuntime()) {
                        int attempts = pendingCleanup.getOrDefault(pu, 0);
                        if (attempts >= MAX_PENDING_ATTEMPTS) {
                            botc.LOGGER.warn("VoiceRegionTask: abandoning leave for {} after {} attempts", p.getName().getString(), attempts);
                            current.remove(pu);
                            return;
                        }
                        boolean left = false;
                        try { left = SvcBridge.leaveGroup(p); } catch (Throwable t) { botc.LOGGER.warn("VoiceRegionTask: leave error {}", t.toString()); }
                        lastActionMs.put(pu, System.currentTimeMillis());
                        if (DEBUG_TASK || watching) botc.LOGGER.info("VoiceRegionTask: LEAVE player={} prev={} target={} success={}", p.getName().getString(), previous, targetGroupName, left);
                        if (!left) pendingCleanup.put(pu, attempts + 1);
                        current.remove(pu);
                        if (targetGroupName != null) {
                            int jAttempts = joinRetries.getOrDefault(pu, 0);
                            if (jAttempts >= MAX_JOIN_ATTEMPTS) return;
                            boolean joined = false;
                            try {
                                if (detected != null && detected.groupId != null) SvcBridge.clearPasswordAndOpenByIdString(detected.groupId);
                                else SvcBridge.clearPasswordAndOpenByName(targetGroupName);
                                joined = SvcBridge.joinGroupByName(p, targetGroupName);
                            } catch (Throwable t) { botc.LOGGER.warn("VoiceRegionTask: switch join error {}", t.toString()); }
                            if (joined) {
                                current.put(pu, targetGroupName);
                                joinRetries.remove(pu);
                                if (DEBUG_TASK || watching) botc.LOGGER.info("VoiceRegionTask: SWITCH success player={} group={}", p.getName().getString(), targetGroupName);
                            } else {
                                joinRetries.put(pu, jAttempts + 1);
                                if (DEBUG_TASK || watching) botc.LOGGER.info("VoiceRegionTask: SWITCH join failed player={} group={} attempt={}", p.getName().getString(), targetGroupName, jAttempts + 1);
                            }
                        }
                    } else {
                        if (targetGroupName != null) current.put(pu, targetGroupName); else current.remove(pu);
                    }
                }
            } catch (Throwable t) {
                botc.LOGGER.warn("VoiceRegionTask error: {}", t.toString());
            }
        });
    }
}