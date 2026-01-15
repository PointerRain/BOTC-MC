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

public class ReminderSelectGUI extends AbstractSelectionGUI<botcCharacter.ReminderToken> {

    private final Script script;
    private final botcSeatManager seatManager;
    private final boolean seeAll;

    public ReminderSelectGUI(ServerPlayerEntity player, Script script, botcSeatManager seatManager,
                             Function<botcCharacter.ReminderToken, ?> onSelectItem, Runnable onCancel,
                             boolean seeAll, int page) {
        super(player, getReminderTokens(script, seatManager, seeAll), onSelectItem, onCancel, page);
        this.setTitle(Text.of("Select Reminder"));

        this.script = script;
        this.seatManager = seatManager;
        this.seeAll = seeAll;

        // Custom token button
        GuiElementInterface.ClickCallback customTokenCallback = (i, c, a, g) ->
                customTokenBox(player);
        this.setSlot(9 * this.getHeight() - 7, TokenItemStack.of(botcCharacter.ReminderToken.CUSTOM), customTokenCallback);

        // Toggle see all/in play button
        if (!seeAll) {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("See All"),
                    (i, c, a, g) -> new ReminderSelectGUI(player, script, seatManager, onSelectItem, onCancel, true, 0).open()));
        } else {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("See In Play"),
                    (i, c, a, g) -> new ReminderSelectGUI(player, script, seatManager, onSelectItem, onCancel, false, 0).open()));
        }
    }

    private static List<botcCharacter.ReminderToken> getReminderTokens(Script script, botcSeatManager seatManager, boolean seeAll) {
        LinkedHashSet<botcCharacter.ReminderToken> tokens = new LinkedHashSet<>();
        if (!seeAll) {
            for (int n = 1; n <= seatManager.getSeatCount(); n++) {
                botcCharacter character = seatManager.getSeatFromNumber(n).getCharacter();
                if (character != null && character != botcCharacter.EMPTY) {
                    tokens.addAll(character.reminderTokens());
                }
            }
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

    static class CustomTokenBox extends SignGui {
        private final Function<? super botcCharacter.ReminderToken, ?> onEnterReminder;

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
