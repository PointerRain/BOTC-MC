package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.TitleUtil;
import golden.botc_mc.botc_mc.game.items.TokenItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RoleAssignment {

    private static final int CHARACTER_REVEAL_DELAY_TICKS = 80; // 4 seconds at 20 ticks per second

    /**
     * Send a character reveal announcement to a player.
     * @param player The player to send the announcement to.
     * @param character The character to send the announcement to.
     */
    public static void sendCharacter(ServerPlayerEntity player, botcCharacter character) {

        MutableText titleText = Text.translatable("gui.botc-mc.role_announcement").formatted(character.team().getColour(false), Formatting.BOLD);
        TitleUtil.showSubtitle(player, titleText, 10, CHARACTER_REVEAL_DELAY_TICKS-20, 5);

        CompletableFuture.delayedExecutor(CHARACTER_REVEAL_DELAY_TICKS * 50L, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    TitleUtil.showTotemEffect(player, TokenItemStack.of(character));
                    TitleUtil.showTitle(player, character.toFormattedText(false, true, false, false), 20, 140, 40);
                });
    }
}