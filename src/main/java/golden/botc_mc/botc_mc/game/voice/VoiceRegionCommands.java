package golden.botc_mc.botc_mc.game.voice;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Administrative commands for inspecting and reloading voice region configuration at runtime.
 * <p>
 * Registered under the {@code /botc voice} sub-tree, currently providing:
 * <ul>
 *   <li>{@code /botc voice info} – print the active manager, config path, and a list of regions.</li>
 *   <li>{@code /botc voice reload} – reload regions from disk for the active manager or fallback.</li>
 * </ul>
 * These commands are intended for operators (permission level ≥ 2).
 */
public final class VoiceRegionCommands {
    private static boolean registered = false; // prevent duplicate registration

    /** Prevent instantiation. */
    private VoiceRegionCommands() {}

    /**
     * Register the {@code /botc voice} commands with Fabric's command dispatcher.
     * @param fallback fallback manager if no active manager is set
     */
    public static void register(VoiceRegionManager fallback) {
        if (registered) {
            golden.botc_mc.botc_mc.botc.LOGGER.debug("VoiceRegionCommands.register() called more than once; ignoring subsequent call");
            return;
        }
        registered = true;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> botcRoot = literal("botc");
            LiteralArgumentBuilder<ServerCommandSource> voiceRoot = literal("voice").requires(src -> src.hasPermissionLevel(2));

            VoiceRegionManager mgrOrFallback = VoiceRegionService.getActiveManager();
            final VoiceRegionManager mgr = (mgrOrFallback != null) ? mgrOrFallback : fallback;

            // Info: /botc voice info
            voiceRoot.then(literal("info").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                VoiceRegionManager active = VoiceRegionService.getActiveManager();
                VoiceRegionManager used = active != null ? active : mgr;
                StringBuilder sb = new StringBuilder();
                sb.append("VoiceRegion info\n");
                sb.append("Config path: ").append(used.getConfigPath()).append("\n");
                sb.append("Map bound: ").append(used.getMapId()==null?"<none>":used.getMapId()).append("\n");
                sb.append("Regions: ").append(used.list().size()).append("\n");
                for (VoiceRegion r : used.list()) {
                    sb.append(" - ").append(r.id()).append(" -> ").append(r.groupName()).append(" (gid=").append(r.groupId()).append(") bounds=").append(r.boundsDebug()).append("\n");
                }
                ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal(sb.toString()), false);
                return used.list().size();
            }));

            // Reload: /botc voice reload
            voiceRoot.then(literal("reload").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                VoiceRegionManager active = VoiceRegionService.getActiveManager();
                VoiceRegionManager used = active != null ? active : mgr;
                int before = used.list().size();
                int after = used.reload();
                ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("Reloaded voice regions: " + before + " -> " + after), false);
                return after;
            }));

            botcRoot.then(voiceRoot);
            dispatcher.register(botcRoot);
        });
    }
}
