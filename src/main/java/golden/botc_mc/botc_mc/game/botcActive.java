package golden.botc_mc.botc_mc.game;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import golden.botc_mc.botc_mc.game.map.botcMap;
import golden.botc_mc.botc_mc.game.state.BotcGameState;
import golden.botc_mc.botc_mc.game.state.GameLifecycleStatus;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class botcActive {
    private static final Logger LOG = LoggerFactory.getLogger("botc-mc");

    private final botcConfig config;

    public final GameSpace gameSpace;
    private final botcMap gameMap;

    private final Object2ObjectMap<PlayerRef, botcPlayer> participants;
    private final botcSpawnLogic spawnLogic;
    private final botcItemManager itemManager;
    private final botcStageManager stageManager;
    private final boolean ignoreWinState;
    private final botcTimerBar timerBar;
    private final ServerWorld world;

    private GameLifecycleStatus lifecycleStatus = GameLifecycleStatus.STOPPED;
    private boolean startingLogged = false;

    private botcActive(GameSpace gameSpace, ServerWorld world, botcMap map, GlobalWidgets widgets, botcConfig config, Set<PlayerRef> participants) {
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new botcSpawnLogic(gameSpace, world, map);
        this.itemManager = new botcItemManager(); // HERE 
        this.participants = new Object2ObjectOpenHashMap<>();
        this.world = world;

        for (PlayerRef player : participants) {
            this.participants.put(player, new botcPlayer());
        }

        this.stageManager = new botcStageManager();
        this.ignoreWinState = this.participants.size() <= 1;
        this.timerBar = new botcTimerBar(widgets);
    }

    public static void open(GameSpace gameSpace, ServerWorld world, botcMap map, botcConfig config) {
        gameSpace.setActivity(game -> {
            Set<PlayerRef> participants = gameSpace.getPlayers().participants().stream()
                    .map(PlayerRef::of)
                    .collect(Collectors.toSet());
            GlobalWidgets widgets = GlobalWidgets.addTo(game);
            botcActive active = new botcActive(gameSpace, world, map, widgets, config, participants);

            game.setRule(GameRuleType.CRAFTING, EventResult.DENY);
            game.setRule(GameRuleType.PORTALS, EventResult.DENY);
            game.setRule(GameRuleType.PVP, EventResult.DENY);
            game.setRule(GameRuleType.HUNGER, EventResult.DENY);
            game.setRule(GameRuleType.FALL_DAMAGE, EventResult.DENY);
            // game.setRule(GameRuleType.INTERACTION, EventResult.DENY);
            game.setRule(GameRuleType.BLOCK_DROPS, EventResult.DENY);
            game.setRule(GameRuleType.THROW_ITEMS, EventResult.DENY);
            game.setRule(GameRuleType.UNSTABLE_TNT, EventResult.DENY);

            game.listen(GameActivityEvents.ENABLE, active::onOpen);
            game.listen(GameActivityEvents.DISABLE, active::onClose);
            game.listen(GameActivityEvents.STATE_UPDATE, state -> state.canPlay(false));

            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
            game.listen(GamePlayerEvents.ACCEPT, joinAcceptor -> joinAcceptor.teleport(world, Vec3d.ZERO));
            game.listen(GamePlayerEvents.ADD, active::addPlayer);
            game.listen(GamePlayerEvents.REMOVE, active::removePlayer);

            game.listen(GameActivityEvents.TICK, active::tick);

            game.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        for (var participant : this.gameSpace.getPlayers().participants()) {
            this.spawnParticipant(participant);
        }
        for (var spectator : this.gameSpace.getPlayers().spectators()) {
            this.spawnSpectator(spectator);
        }

        this.stageManager.attachContext(this.gameSpace, this.config);
        this.stageManager.markPlayersPresent(!this.gameSpace.getPlayers().participants().isEmpty());
        this.stageManager.onOpen(this.world.getTime(), this.config);
        // Lifecycle transition and game-start logic will be triggered during the first tick() when the
        // stageManager updates lifecycle state.
    }

    private void onClose() {
        // TODO teardown logic
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(PlayerRef.of(player)) || this.gameSpace.getPlayers().spectators().contains(player)) {
            this.spawnSpectator(player);
        }
    }

    private void removePlayer(ServerPlayerEntity player) {
        this.participants.remove(PlayerRef.of(player));
    }

    private EventResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        // TODO handle damage
        this.spawnParticipant(player);
        return EventResult.DENY;
    }

    private EventResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        // TODO handle death
        this.spawnParticipant(player);
        return EventResult.DENY;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    private void tick() {
        long time = this.world.getTime();

        botcStageManager.IdleTickResult result = this.stageManager.tick(time, gameSpace);

        GameLifecycleStatus currentLifecycle = this.stageManager.getLifecycleStatus();
        if (currentLifecycle != this.lifecycleStatus) {
            this.lifecycleStatus = currentLifecycle;
            onLifecycleStateChanged();
        }

        switch (result) {
            case CONTINUE_TICK -> { /* keep ticking */ }
            case TICK_FINISHED -> { return; }
            case GAME_FINISHED -> {
                this.lifecycleStatus = GameLifecycleStatus.STOPPING;
                onLifecycleStateChanged();
                this.broadcastWin(this.checkWinResult());
                return;
            }
            case GAME_CLOSED -> {
                this.lifecycleStatus = GameLifecycleStatus.STOPPED;
                onLifecycleStateChanged();
                this.gameSpace.close(GameCloseReason.FINISHED);
                return;
            }
        }

        long remaining = this.stageManager.getStateTicksRemaining();
        long total = this.stageManager.getStateDuration();
        this.timerBar.updatePhase(this.stageManager.getCurrentState(), remaining, total);

        // TODO tick logic per state
    }

    private void broadcastWin(WinResult result) {
        ServerPlayerEntity winningPlayer = result.getWinningPlayer();

        Text message;
        if (winningPlayer != null) {
            message = winningPlayer.getDisplayName().copy().append(" has won the game!").formatted(Formatting.GOLD);
        } else {
            message = Text.literal("The game ended, but nobody won!").formatted(Formatting.GOLD);
        }

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.playSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.ignoreWinState) {
            return WinResult.no();
        }

        ServerPlayerEntity winningPlayer = null;

        // TODO win result logic
        return WinResult.no();
    }

    private void onLifecycleStateChanged() {
        // Log every lifecycle transition and run hooks
        LOG.info("Lifecycle changed to {}", this.lifecycleStatus);
        switch (this.lifecycleStatus) {
            case STARTING -> {
                handleGameStarting();
            }
            case RUNNING -> {
                // Additional running-state logging if desired
                LOG.info("Game is now RUNNING");
            }
            case STOPPING -> {
                LOG.info("Game is STOPPING");
            }
            case STOPPED -> {
                LOG.info("Game is STOPPED");
            }
        }
    }

    // private void giveStarterItems() {
    //     // MinecraftServer server = world.getServer(); // I still need to confirm this will just select the current minigame sub-server not the entire server


    //     ItemStack stack = new ItemStack(Items.DIAMOND, 1);
    //     stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Hello"));
    //     LoreComponent lore = new LoreComponent(
    //         List.of(
    //             Text.literal("Line 1"),
    //             Text.literal("Line 2")
    //         )
    //     );
    //     stack.set(DataComponentTypes.LORE, lore);

        
        
    //     for (ServerPlayerEntity player : this.gameSpace.getPlayers().participants()) {
    //         // Force-set item into slot 5 (0–35 = main inventory, 36–44 = armour/offhand)
    //         player.getInventory().setStack(7, stack.copy());
    //         player.getInventory().setStack(8, stack.copy());

    //         // Sync to client
    //         player.currentScreenHandler.sendContentUpdates();
    //     }
    // }

    private void handleGameStarting() {
        if (startingLogged) {
            return; // Already logged starting logic
        }
        startingLogged = true;
        // Print a concise console line when the game begins
        int participantCount = this.gameSpace.getPlayers().participants().size();
        LOG.info("Game STARTING at tick {} with {} participant(s)", this.world.getTime(), participantCount);
        // giveStarterItems();
        itemManager.giveStarterItems(this.gameSpace);
    }

    static class WinResult {
        final ServerPlayerEntity winningPlayer;
        final boolean win;

        private WinResult(ServerPlayerEntity winningPlayer, boolean win) {
            this.winningPlayer = winningPlayer;
            this.win = win;
        }

        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(ServerPlayerEntity player) {
            return new WinResult(player, true);
        }

        public boolean isWin() {
            return this.win;
        }

        public ServerPlayerEntity getWinningPlayer() {
            return this.winningPlayer;
        }
    }
}
