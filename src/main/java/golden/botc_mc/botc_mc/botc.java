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
import golden.botc_mc.botc_mc.game.voice.VoiceRegionService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

public class botc implements ModInitializer {

    public static final String ID = "botc-mc";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static final GameType<botcConfig> TYPE = GameType.register(
            Identifier.of(ID, "game"),
            botcConfig.MAP_CODEC,
            botcWaiting::open
    );

    private VoiceRegionTask voiceRegionTask;
    private VoiceRegionManager voiceRegionManager;

    @Override
    public void onInitialize() {
        LOGGER.info("GameType is present during onInitialize: {}", Identifier.of(ID, "game"));

        botcCommands.register();
        // Migrate legacy region file path if needed (double run directory)
        VoiceRegionService.migrateLegacyGlobalRegionsIfNeeded();
        voiceRegionManager = new VoiceRegionManager(VoiceRegionService.legacyGlobalConfigPath());

        try {
            Class<?> cmdCls = Class.forName("golden.botc_mc.botc_mc.game.voice.VoiceRegionCommands");
            Method reg = cmdCls.getMethod("register", VoiceRegionManager.class);
            reg.invoke(null, voiceRegionManager);
            LOGGER.info("Registered VoiceRegionCommands via reflection");
        } catch (Throwable t) {
            LOGGER.warn("Failed to register VoiceRegionCommands reflectively: {}", t.toString());
        }

        voiceRegionTask = new VoiceRegionTask(null, voiceRegionManager);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { preloadOnce(server); } catch (Throwable ignored) {}
            if (voiceRegionTask != null) {
                try {
                    voiceRegionTask.setServer(server);
                } catch (Throwable ignored) {}
                try {
                    voiceRegionTask.run();
                } catch (Throwable ex) {
                    LOGGER.warn("VoiceRegionTask tick error: {}", ex.toString());
                }
            }
        });
    }

    private static volatile boolean PRELOADED = false;
    private static void preloadOnce(MinecraftServer server) {
        if (PRELOADED) return;
        try {
            Class<?> bridgeCls = Class.forName("golden.botc_mc.botc_mc.game.voice.SvcBridge");
            Method avail = bridgeCls.getMethod("isAvailableRuntime");
            Object available = avail.invoke(null);
            if (!(available instanceof Boolean) || !((Boolean) available)) {
                PRELOADED = true; // do not spam per-tick
                return;
            }
            Class<?> pluginCls = Class.forName("golden.botc_mc.botc_mc.game.voice.BotcVoicechatPlugin");
            Method gi = pluginCls.getMethod("getInstance", MinecraftServer.class);
            Object plugin = gi.invoke(null, server);
            Method preload = pluginCls.getMethod("preload");
            preload.invoke(plugin);
            PRELOADED = true;
        } catch (Throwable t) {
            // keep silent; voice is optional
            PRELOADED = true;
        }
    }
}