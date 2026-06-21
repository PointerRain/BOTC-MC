package golden.botc_mc.botc_mc.game.voice;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Periodic task that evaluates player positions and ensures they are joined to the correct
 * Simple Voice Chat group for their current {@link VoiceRegion}.
 * <p>
 * Responsibilities and algorithm:
 * <p> 1. For each online player, resolve the active {@link VoiceRegion} via the configured
 *     {@link VoiceRegionManager}. If none is matched, the player should not be in any map-linked
 *     voice group.
 * <p> 2. Apply a short stability window before acting on a region detection to avoid churn when
 *     players briefly cross region boundaries.
 * <p> 3. When a change is stable, perform join/leave actions via {@link VoiceService}. Joins are
 *     retried up to a bounded number of attempts; leaves are retried with a pending-cleanup counter.
 * <p> 4. Throttle actions per-player with a cooldown to avoid rapid repeated calls to the voice server.
 * <p> 5. Per-player tracking state is pruned each tick for players who are no longer online.
 */
public class VoiceRegionTask implements Runnable {
    private MinecraftServer server;
    private final VoiceRegionManager manager;

    private final Map<UUID, String> current = new HashMap<>();
    private final Map<UUID, Integer> pendingCleanup = new HashMap<>();
    private final Map<UUID, Integer> joinRetries = new HashMap<>();
    private final Map<UUID, Long> lastActionMs = new HashMap<>();

    private static final int MAX_PENDING_ATTEMPTS = 6;
    private static final int MAX_JOIN_ATTEMPTS = 4;
    private static final long ACTION_COOLDOWN_MS = 300;
    private static final long REGION_STABLE_MS = 500;

    private final Map<UUID, String> lastDetectedRegion = new HashMap<>();
    private final Map<UUID, Long> lastDetectedRegionMs = new HashMap<>();

    public VoiceRegionTask(MinecraftServer server, VoiceRegionManager manager) {
        this.server = server;
        this.manager = manager;
    }

    public void setServer(MinecraftServer srv) {
        this.server = srv;
    }

    public void run() {
        long nowMs = System.currentTimeMillis();
        if (server == null) return;

        VoiceRegionManager resolved = VoiceRegionService.getActiveManager();
        final VoiceRegionManager mgr = (resolved != null) ? resolved : this.manager;

        forgetOfflinePlayers();

        server.getPlayerManager().getPlayerList().forEach(p -> {
            try {
                UUID pu = p.getUuid();

                Long last = lastActionMs.get(pu);
                if (last != null && (nowMs - last) < ACTION_COOLDOWN_MS) {
                    return;
                }

                if (VoiceService.isAvailable() && !VoiceService.isPlayerConnected(p)) {
                    return;
                }

                VoiceRegion detected = mgr.regionForPlayer(p);
                final String detectedName = detected == null ? null : detected.groupName();

                String previousDetected = lastDetectedRegion.get(pu);
                if ((previousDetected == null && detectedName != null) || (previousDetected != null && !previousDetected.equals(detectedName))) {
                    lastDetectedRegion.put(pu, detectedName);
                    lastDetectedRegionMs.put(pu, nowMs);
                }

                // Require a region detection to be stable for a short window before acting on it,
                // so players briefly crossing a boundary do not cause join/leave churn.
                if (detectedName != null) {
                    Long firstSeenMs = lastDetectedRegionMs.get(pu);
                    if (firstSeenMs == null || (nowMs - firstSeenMs) < REGION_STABLE_MS) {
                        return;
                    }
                }

                if (pendingCleanup.containsKey(pu)) {
                    try {
                        UUID still = VoiceService.getPlayerGroupId(p);
                        if (still == null) {
                            pendingCleanup.remove(pu);
                        } else {
                            int att = pendingCleanup.getOrDefault(pu, 0);
                            if (att >= MAX_PENDING_ATTEMPTS) {
                                botc.LOGGER.warn("VoiceRegionTask: cleanup attempts exceeded for {} (group {})", p.getName().getString(), still);
                                pendingCleanup.remove(pu);
                            } else {
                                VoiceService.leaveGroup(p);
                                pendingCleanup.put(pu, att + 1);
                                lastActionMs.put(pu, System.currentTimeMillis());
                            }
                        }
                    } catch (Throwable t) {
                        botc.LOGGER.debug("VoiceRegionTask: pending cleanup error {}", t.toString());
                    }
                }

                String previous = current.get(pu);
                UUID currentSvcGroup = VoiceService.isAvailable() ? VoiceService.getPlayerGroupId(p) : null;

                // Player left all regions: leave any stale group they are still in.
                if (detectedName == null && VoiceService.isAvailable()) {
                    try {
                        if (currentSvcGroup != null) {
                            boolean leftForced = VoiceService.leaveGroup(p);
                            lastActionMs.put(pu, System.currentTimeMillis());
                            if (leftForced) {
                                current.remove(pu);
                            } else {
                                pendingCleanup.put(pu, 1);
                            }
                            return;
                        }
                    } catch (Throwable t) {
                        botc.LOGGER.debug("VoiceRegionTask: stale leave error {}", t.toString());
                    }
                }

                // First join into a region.
                if (previous == null && detectedName != null) {
                    if (VoiceService.isAvailable()) {
                        int attempts = joinRetries.getOrDefault(pu, 0);
                        if (attempts >= MAX_JOIN_ATTEMPTS) {
                            try { p.sendMessage(Text.literal("Voice region join failed repeatedly for " + detectedName), false); } catch (Throwable ignored) {}
                            return;
                        }
                        boolean joined = false;
                        try {
                            joined = VoiceService.joinGroup(p, detectedName);
                        } catch (Throwable t) {
                            botc.LOGGER.warn("VoiceRegionTask: join error {}", t.toString());
                        }
                        lastActionMs.put(pu, System.currentTimeMillis());
                        if (joined) {
                            current.put(pu, detectedName);
                            joinRetries.remove(pu);
                        } else {
                            joinRetries.put(pu, attempts + 1);
                        }
                    } else {
                        current.put(pu, detectedName);
                    }
                    return;
                }

                // Switch between regions (or leave on exit handled above).
                if (previous != null && (detectedName == null || !detectedName.equals(previous))) {
                    if (VoiceService.isAvailable()) {
                        int attempts = pendingCleanup.getOrDefault(pu, 0);
                        if (attempts >= MAX_PENDING_ATTEMPTS) {
                            botc.LOGGER.warn("VoiceRegionTask: abandoning leave for {} after {} attempts", p.getName().getString(), attempts);
                            current.remove(pu);
                            return;
                        }
                        boolean left = false;
                        try { left = VoiceService.leaveGroup(p); } catch (Throwable t) { botc.LOGGER.warn("VoiceRegionTask: leave error {}", t.toString()); }
                        lastActionMs.put(pu, System.currentTimeMillis());
                        if (!left) pendingCleanup.put(pu, attempts + 1);
                        current.remove(pu);
                        if (detectedName != null) {
                            int jAttempts = joinRetries.getOrDefault(pu, 0);
                            if (jAttempts >= MAX_JOIN_ATTEMPTS) return;
                            boolean joined = false;
                            try {
                                joined = VoiceService.joinGroup(p, detectedName);
                            } catch (Throwable t) { botc.LOGGER.warn("VoiceRegionTask: switch join error {}", t.toString()); }
                            if (joined) {
                                current.put(pu, detectedName);
                                joinRetries.remove(pu);
                            } else {
                                joinRetries.put(pu, jAttempts + 1);
                            }
                        }
                    } else {
                        if (detectedName != null) current.put(pu, detectedName); else current.remove(pu);
                    }
                }
            } catch (Throwable t) {
                botc.LOGGER.warn("VoiceRegionTask error: {}", t.toString());
            }
        });
    }

    /**
     * Drop all per-player tracking state for players who are no longer online, so the tracking
     * maps do not grow without bound over the lifetime of a server.
     */
    private void forgetOfflinePlayers() {
        Set<UUID> online = new java.util.HashSet<>();
        server.getPlayerManager().getPlayerList().forEach(p -> online.add(p.getUuid()));
        current.keySet().retainAll(online);
        pendingCleanup.keySet().retainAll(online);
        joinRetries.keySet().retainAll(online);
        lastActionMs.keySet().retainAll(online);
        lastDetectedRegion.keySet().retainAll(online);
        lastDetectedRegionMs.keySet().retainAll(online);
    }
}
