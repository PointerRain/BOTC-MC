package golden.botc_mc.botc_mc;

import net.fabricmc.api.ModInitializer;
import xyz.nucleoid.plasmid.api.game.GameType;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import golden.botc_mc.botc_mc.game.botcConfig;
import golden.botc_mc.botc_mc.game.botcWaiting;
import golden.botc_mc.botc_mc.game.botcCommands;
import golden.botc_mc.botc_mc.game.voice.VoiceRegionManager;
import golden.botc_mc.botc_mc.game.voice.VoiceRegionTask;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.nio.file.Paths;

public class botc implements ModInitializer {
    public static final String ID = "botc-mc";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    public static final GameType<botcConfig> TYPE = GameType.<botcConfig>register(
            Identifier.of(ID, "botc-mc"),
            botcConfig.MAP_CODEC,
            botcWaiting::open
    );

    private VoiceRegionTask voiceRegionTask; // persistent instance
    private VoiceRegionManager voiceRegionManager; // stored for potential future commands

    @Override
    public void onInitialize() {
        botcCommands.register();
        voiceRegionManager = new VoiceRegionManager(Paths.get("run", "config", "voice_regions.json"));

        // Register voice commands reflectively (still optional)
        try {
            Class<?> cmdCls = Class.forName("golden.botc_mc.botc_mc.game.voice.VoiceRegionCommands");
            Method reg = cmdCls.getMethod("register", VoiceRegionManager.class);
            reg.invoke(null, voiceRegionManager);
            LOGGER.info("Registered VoiceRegionCommands via reflection");
        } catch (ClassNotFoundException cnf) {
            LOGGER.info("VoiceRegionCommands not present; skipping voice commands registration");
        } catch (Throwable t) {
            LOGGER.warn("Failed to register VoiceRegionCommands reflectively: {}", t.toString());
        }

        voiceRegionTask = new VoiceRegionTask(null, voiceRegionManager); // server assigned on first tick

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { preloadOnce(server); } catch (Throwable ignored) {}
            if (voiceRegionTask != null && voiceRegionTaskServerNull()) {
                // replace reflection with setter if available
                try {
                    voiceRegionTask.setServer(server);
                    LOGGER.debug("VoiceRegionTask: server reference attached; starting region monitoring");
                } catch (Throwable t) {
                    // fallback to reflection
                    setVoiceRegionTaskServer(server);
                }
            }
            try {
                if (voiceRegionTask != null) {
                    // emit a quick debug summary if any watchers active
                    if (!golden.botc_mc.botc_mc.game.voice.VoiceRegionTask.watchers().isEmpty()) {
                        LOGGER.trace("VoiceRegionTask: active watchers=" + golden.botc_mc.botc_mc.game.voice.VoiceRegionTask.watchers().size());
                    }
                    voiceRegionTask.run();
                }
            } catch (Throwable ex) {
                LOGGER.warn("VoiceRegionTask tick error: {}", ex.toString());
            }
        });
    }

    // Reflection helper replaced with direct field update since class is present at compile time
    private boolean voiceRegionTaskServerNull() {
        try {
            java.lang.reflect.Field f = VoiceRegionTask.class.getDeclaredField("server");
            f.setAccessible(true);
            return f.get(voiceRegionTask) == null;
        } catch (Throwable ignored) { return false; }
    }
    private void setVoiceRegionTaskServer(MinecraftServer server) {
        try {
            java.lang.reflect.Field f = VoiceRegionTask.class.getDeclaredField("server");
            f.setAccessible(true);
            f.set(voiceRegionTask, server);
        } catch (Throwable t) {
            LOGGER.warn("Failed to set server on VoiceRegionTask: {}", t.toString());
        }
    }

    private static volatile boolean PRELOADED = false;
    private static void preloadOnce(MinecraftServer server) {
        if (PRELOADED) return;
        try {
            Class<?> bridgeCls = Class.forName("golden.botc_mc.botc_mc.game.voice.SvcBridge");
            Method avail = bridgeCls.getMethod("isAvailableRuntime");
            Object available = avail.invoke(null);
            if (!(available instanceof Boolean) || !((Boolean) available)) {
                return;
            }
            Class<?> pluginCls = Class.forName("golden.botc_mc.botc_mc.game.voice.BotcVoicechatPlugin");
            Method gi = pluginCls.getMethod("getInstance", MinecraftServer.class);
            Object plugin = gi.invoke(null, server);
            Method preload = pluginCls.getMethod("preload");
            preload.invoke(plugin);
            PRELOADED = true;
        } catch (ClassNotFoundException e) {
            // retry later
        } catch (Throwable t) {
            LOGGER.warn("BotcVoicechatPlugin preload failed: {}", t.toString());
        }
    }
}
