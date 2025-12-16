package golden.botc_mc.botc_mc;

import golden.botc_mc.botc_mc.game.Character;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.botcActive;
import golden.botc_mc.botc_mc.game.botcCommands;
import golden.botc_mc.botc_mc.game.botcConfig;
import golden.botc_mc.botc_mc.game.botcPlayer;
import golden.botc_mc.botc_mc.game.botcWaiting;
import golden.botc_mc.botc_mc.game.seat.Seat;
import golden.botc_mc.botc_mc.game.voice.VoiceRegionManager;
import golden.botc_mc.botc_mc.game.voice.VoiceRegionService;
import golden.botc_mc.botc_mc.game.voice.VoiceRegionTask;
import golden.botc_mc.botc_mc.game.voice.VoicechatPlugin;
import golden.botc_mc.botc_mc.game.voice.SvcBridge;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.api.game.GameType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Primary mod entrypoint and game type registration for BOTC.
 * Responsible for creating the Plasmid game type, wiring lifecycle hooks and commands.
 * <p>
 * This class is instantiated by the Fabric runtime and its {@link #onInitialize()} method is
 * used to register commands, resource reloaders and any optional integrations (voice chat).
 */
public class botc implements ModInitializer {
    /** Mod id namespace used for resource and game type registration. */
    public static final String ID = "botc-mc";
    /** Structured logger for BOTC mod operations. */
    public static final Logger LOGGER = LogManager.getLogger(ID);
    /**
     * Loaded script definitions keyed by their resource identifier string (for example
     * "botc-mc:scripts/trouble_brewing.json"). Populated by the resource reload listener
     * during server startup and when datapacks are reloaded.
     */
    public static final Map<String, Script> scripts = new HashMap<>();

    private VoiceRegionTask voiceRegionTask;
    private static volatile boolean REGIONS_MATERIALIZED = false;

    private static final List<botcActive> activeGames = new ArrayList<>();

    /**
     * Explicit no-arg constructor. Present to provide a documented construction point for
     * static analysis tools which warn on use of the implicit default constructor.
     */
    public botc() {
        // intentionally empty; initialization occurs in onInitialize()
    }

    /**
     * Register the Plasmid GameType for BOTC reflectively. This uses reflection to call an
     * older register signature when available so the mod remains compatible with multiple
     * Plasmid/Plasmid-like versions.
     */
    private void registerGameType() {
        // Register under legacy id to support existing datapacks: botc-mc:botc-mc
        Identifier id = Identifier.of(ID, "botc-mc");
        try {
            // Reflectively obtain (possibly deprecated) register method: register(Identifier, MapCodec, GameType.Open)
            java.lang.reflect.Method registerMethod = GameType.class.getDeclaredMethod(
                    "register",
                    Identifier.class,
                    com.mojang.serialization.MapCodec.class,
                    GameType.Open.class
            );
            registerMethod.setAccessible(true);
            GameType.Open<botcConfig> openFn = botcWaiting::open;
            registerMethod.invoke(null, id, botcConfig.MAP_CODEC, openFn);
            LOGGER.info("Registered BOTC GameType reflectively (id={} ).", id);
        } catch (Throwable t) {
            LOGGER.error("Failed to register BOTC GameType reflectively: {}", t.toString());
        }
    }


    /**
     * Fabric entrypoint called when the mod initializes.
     * Registers reload listeners, commands and optional voicechat hooks.
     */
    @Override
    public void onInitialize() {
        registerGameType();
        LOGGER.debug("botcConfig CODEC loaded: {}", botcConfig.CODEC);

        botcCommands.register();

        // Register resource loader for characters and scripts
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(botc.ID, "character_data_loader");
            }

            @Override
            public void reload(ResourceManager manager) {
                Resource baseCharacters = manager.getResource(Identifier.of("botc-mc:character_data/base_characters" +
                        ".json")).orElse(null);
                if (baseCharacters != null) {
                    golden.botc_mc.botc_mc.game.Character.registerBaseCharacters(baseCharacters);
                    // Log some character data to verify loading
                    LOGGER.debug(new golden.botc_mc.botc_mc.game.Character("washerwoman"));
                    LOGGER.debug(new Character("pithag"));
                } else {
                    LOGGER.error("Error reading base_characters.json");
                }

                for (Identifier id :
                        manager.findResources("scripts", path -> path.toString().endsWith(".json")).keySet()) {
                    LOGGER.info("Loading {}...", String.valueOf(id));
                    manager.getResource(id).ifPresent(script -> scripts.put(String.valueOf(id), Script.fromResource(script)));
                }
                LOGGER.info("Loaded {} scripts", scripts.size());
                LOGGER.debug(scripts.get("botc-mc:scripts/trouble_brewing.json"));
                LOGGER.debug(scripts.get("botc-mc:scripts/separation_church_state.json").getJinxes());
            }
        });

        // Initialize voice region system
        VoiceRegionManager voiceRegionManager = new VoiceRegionManager(VoiceRegionService.botcConfigRoot().resolve("voice/global.json"));

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
                try { voiceRegionTask.setServer(server); } catch (Throwable ignored) {}
                try { voiceRegionTask.run(); } catch (Throwable ex) { LOGGER.warn("VoiceRegionTask tick error: {}", ex.toString()); }
            }
            // Deferred region materialization: ensure map-based regions create/open groups once voice chat available
            try {
                if (!REGIONS_MATERIALIZED && SvcBridge.isAvailableRuntime()) {
                    VoiceRegionManager active = VoiceRegionService.getActiveManager();
                    if (active != null) {
                        VoicechatPlugin plugin = VoicechatPlugin.getInstance(server);
                        plugin.onMapOpen(active.getMapId()); // reuse logic; it will materialize regions
                        REGIONS_MATERIALIZED = true;
                        LOGGER.info("Deferred voice region materialization complete for map {}", active.getMapId());
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("Deferred region materialization error: {}", t.toString());
            }
        });
    }

    private static volatile boolean PRELOADED = false;
    /**
     * One-time preload hook: attempts to initialize voice chat integration reflectively if the mod is present.
     * Silently disables voice features if detection fails.
     * @param server the active Minecraft server instance
     */
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
            tryInitVoiceReflective(server);
            PRELOADED = true;
        } catch (Throwable t) {
            // keep silent; voice is optional
            PRELOADED = true;
        }
    }

    /**
     * Attempt to initialize the VoicechatPlugin singleton reflectively without hard dependency linkage.
     * @param server server used to scope plugin instance
     */
    private static void tryInitVoiceReflective(MinecraftServer server) {
        try {
            Class<?> pluginCls = Class.forName("golden.botc_mc.botc_mc.game.voice.VoicechatPlugin");
            java.lang.reflect.Method getInstance = pluginCls.getMethod("getInstance", MinecraftServer.class);
            Object plugin = getInstance.invoke(null, server);
            // Call preload() if present
            try {
                java.lang.reflect.Method preload = pluginCls.getMethod("preload");
                preload.invoke(plugin);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }


    /**
     * Add the active game to the list of active games
     * TODO: There is probably a preexisting way to manage active games in Plasmid
     */
    public static void addGame(botcActive game) {
        LOGGER.info("Adding the game " + game);
        activeGames.add(game);
    }
    /**
     * Remove the active game from the list of active games
     * TODO: There is probably a preexisting way to manage active games in Plasmid
     */
    public static void removeGame(botcActive game) {
        LOGGER.info("Removing the game " + game);
        activeGames.remove(game);
    }

    /**
     * Get the list of active games
     * @return The list of active games
     */
    public static List<botcActive> getActiveGames() {
        return activeGames;
    }

    /**
     * Get the Seat object for the given ServerPlayerEntity
     * @param player The player to get the botcPlayer object for
     * @return The Seat object, or null if the player is not in an active game
     */
    public static Seat getSeatFromPlayer(ServerPlayerEntity player) {
        botcActive activeGame = getActiveGameFromPlayer(player);
        if (activeGame != null) {
            return activeGame.getSeatManager().getSeatFromPlayer(player);
        }
        return null;
    }

    /**
     * Get the active game that the given player is in
     * @param player The player to get the active game for
     * @return The active game, or null if the player is not in an active game
     */
    public static botcActive getActiveGameFromPlayer(ServerPlayerEntity player) {
        for (botcActive activeGame : activeGames) {
            LOGGER.info("Getting active game from seats in " + activeGame);
            if (activeGame.getSeatManager().getSeatFromPlayer(player) != null) {
                LOGGER.info("Found active game " + activeGame);
                return activeGame;
            }
        }
        for (botcActive activeGame : activeGames) {
            LOGGER.info("Getting active game from botcPlayers in " + activeGame);
            Object2ObjectMap<PlayerRef, botcPlayer> players = activeGame.getParticipants();
            if (players.containsKey(PlayerRef.of(player))) {
                LOGGER.info("Found active game " + activeGame);
                return activeGame;
            }
        }
        return null;
    }
}