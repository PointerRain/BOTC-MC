package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.gui.SignGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import net.minecraft.block.Blocks;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ReminderSelectGUI extends SimpleGui {
    private final Function<? super botcCharacter.ReminderToken, ?> onSelectReminder;

    public ReminderSelectGUI(ServerPlayerEntity player, Script script, botcSeatManager seatManager,
                             Function<? super botcCharacter.ReminderToken, ?> onSelectReminder) {
        super(getScreenSize(script, seatManager), player, false);
        this.setTitle(Text.of("Select Reminder"));

        this.onSelectReminder = onSelectReminder;

        for (botcCharacter.ReminderToken token : getReminderTokens(script, seatManager)) {
            this.addSlot(TokenItemStack.of(token), (index, clickType, slotActionType, gui) ->
                    tokenSelectCallback(token));
        }
        this.addSlot(TokenItemStack.of(botcCharacter.ReminderToken.CUSTOM), (index, clickType, slotActionType, gui) ->
                customTokenBox(player));
    }

    private static List<botcCharacter.ReminderToken> getReminderTokens(Script script, botcSeatManager seatManager) {
        List<botcCharacter.ReminderToken> tokens = new ArrayList<>();
        for (int n = 1; n <= seatManager.getSeatCount(); n++) {
            botcCharacter character = seatManager.getSeatFromNumber(n).getCharacter();
            if (character != null && character != botcCharacter.EMPTY) {
                tokens.addAll(character.reminderTokens());
            }
        }
        for (botcCharacter character : script.characters()) {
            tokens.addAll(character.globalReminderTokens());
        }
        return tokens;
    }

    private static int countRows(Script script, botcSeatManager seatManager) {
        int count = getReminderTokens(script, seatManager).size();
        return (int) Math.ceil(count / 9.0);
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> getScreenSize(Script script,
                                                                                  botcSeatManager seatManager) {
        return GrimoireGUI.getScreenSizeOfRows(countRows(script, seatManager));
    }

    void tokenSelectCallback(botcCharacter.ReminderToken token) {
        this.onSelectReminder.apply(token);
        this.close();
    }

    void customTokenBox(ServerPlayerEntity player) {
        CustomTokenBox box = new CustomTokenBox(player, this.onSelectReminder);
        box.open();
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
            String text = String.join(" ",
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
