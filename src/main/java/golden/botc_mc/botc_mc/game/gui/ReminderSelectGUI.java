package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.SignGui;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Selection GUI for reminder tokens.
 */
public class ReminderSelectGUI extends AbstractSingleSelectGUI<botcCharacter.ReminderToken> {

    private final Script script;
    private final botcSeatManager seatManager;
    private final boolean seeAll;

    /**
     * Constructor for ReminderSelectGUI.
     * @param player The player for whom the GUI is being created.
     * @param script The game script containing character information.
     * @param seatManager The seat manager managing NPC assignments.
     * @param onSelectItem A function to call when a reminder token is selected.
     * @param onCancel A runnable to call when the selection is cancelled.
     * @param seeAll Whether to include all reminder tokens or only those in play.
     * @param page The current page number (0-indexed).
     */
    public ReminderSelectGUI(ServerPlayerEntity player, Script script, botcSeatManager seatManager,
                             Function<botcCharacter.ReminderToken, ?> onSelectItem, Runnable onCancel,
                             boolean seeAll, int page) {
        super(player, getReminderTokens(script, seatManager, seeAll), onSelectItem, onCancel, page);
        this.setTitle(Text.translatable("gui.botc-mc.selection.reminder"));

        this.script = script;
        this.seatManager = seatManager;
        this.seeAll = seeAll;

        // Custom token button
        GuiElementInterface.ClickCallback customTokenCallback = (i, c, a, g) ->
                customTokenBox(player);
        this.setSlot(9 * this.getHeight() - 7, TokenItemStack.of(botcCharacter.ReminderToken.CUSTOM), customTokenCallback);

        // Toggle see all/in play button
        if (!seeAll) {
            this.setSlot(9 * this.getHeight() - 5, ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.selection.reminder.see_all"),
                    ButtonIcon.MORE,
                    (i, c, a, g) -> new ReminderSelectGUI(player, script, seatManager, onSelectItem, onCancel, true, 0).open()));
        } else {
            this.setSlot(9 * this.getHeight() - 5, ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.selection.reminder.in_play"),
                    ButtonIcon.LESS,
                    (i, c, a, g) -> new ReminderSelectGUI(player, script, seatManager, onSelectItem, onCancel, false, 0).open()));
        }
    }

    /**
     * Get a list of reminder tokens based on whether to see all or only those in play.
     * @param script The game script containing character information.
     * @param seatManager The seat manager managing NPC assignments.
     * @param seeAll Whether to include all reminder tokens or only those in play.
     * @return A list of reminder tokens.
     */
    private static List<botcCharacter.ReminderToken> getReminderTokens(Script script, botcSeatManager seatManager, boolean seeAll) {
        LinkedHashSet<botcCharacter.ReminderToken> tokens = new LinkedHashSet<>();
        if (!seeAll) {
            for (int n = 1; n <= seatManager.getSeatCount(); n++) {
                botcCharacter character = seatManager.getSeatFromNumber(n).getCharacter();
                if (character != null && character != botcCharacter.EMPTY) {
                    tokens.addAll(character.reminderTokens());
                }
            }
            // TODO: Include storyteller seats
        } else {
            for (botcCharacter character : script.characters()) {
                tokens.addAll(character.reminderTokens());
            }
        }
        for (botcCharacter character : script.characters()) {
            tokens.addAll(character.globalReminderTokens());
        }
        for (botcCharacter character : seatManager.getNPCs()) {
            tokens.addAll(character.reminderTokens());
        }
        return tokens.stream().toList();
    }

    /**
     * Open a box for entering a custom reminder token.
     * @param player The player for whom to open the custom token box.
     */
    void customTokenBox(ServerPlayerEntity player) {
        CustomTokenBox box = new CustomTokenBox(player, this.onSelectItem);
        box.open();
    }

    @Override
    protected AbstractSelectionGUI<botcCharacter.ReminderToken> newInstance(ServerPlayerEntity player, int page) {
        return new ReminderSelectGUI(player, this.script, this.seatManager, this.onSelectItem, this.onCancel,
                this.seeAll, page);
    }

    @Override
    protected ItemStack getItemStack(botcCharacter.ReminderToken item) {
        return TokenItemStack.of(item);
    }

    /**
     * Custom sign GUI for entering a custom reminder token.
     */
    static class CustomTokenBox extends SignGui {
        private final Function<? super botcCharacter.ReminderToken, ?> onEnterReminder;

        /**
         * Constructor for CustomTokenBox.
         * @param player The player for whom the custom token box is being created.
         * @param onEnterReminder A function to call when a custom reminder is entered.
         */
        public CustomTokenBox(ServerPlayerEntity player,
                              Function<? super botcCharacter.ReminderToken, ?> onEnterReminder) {
            super(player);
            this.onEnterReminder = onEnterReminder;
            this.setSignType(Blocks.CRIMSON_WALL_SIGN);
            this.setColor(DyeColor.WHITE);
            this.signEntity.changeText(signText -> signText.withGlowing(true), true);
            botc.LOGGER.info("Opened custom token box for player {}", player.getName().getString());
        }

        @Override
        public void onClose() {
            super.onClose();
            String text = String.join("\n",
                    this.getLine(0).getString(),
                    this.getLine(1).getString(),
                    this.getLine(2).getString(),
                    this.getLine(3).getString()).trim();
            if (!text.isEmpty()) {
                botc.LOGGER.info("Entered custom reminder: {}", text);
                this.onEnterReminder.apply(new botcCharacter.ReminderToken(botcCharacter.EMPTY, text, false));
            } else {
                botc.LOGGER.info("No custom reminder entered.");
            }
        }
    }
}
