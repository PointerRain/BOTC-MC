package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.elements.GuiElementInterface;
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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ReminderSelectGUI extends SimpleGui {
    private final Function<? super botcCharacter.ReminderToken, ?> onSelectReminder;

    private static final int TOKENS_PER_PAGE = 5 * 9;

    public ReminderSelectGUI(ServerPlayerEntity player, Script script, botcSeatManager seatManager,
                             Function<? super botcCharacter.ReminderToken, ?> onSelectReminder, boolean seeAll, int page) {
        super(getScreenSize(script, seatManager, seeAll), player, false);
        this.setTitle(Text.of("Select Reminder"));

        this.onSelectReminder = onSelectReminder;

        int pages = getPageCount(script, seatManager, seeAll);

        // Add all tokens for this page
        for (botcCharacter.ReminderToken token : getReminderTokens(script, seatManager, seeAll, page)) {
            this.addSlot(TokenItemStack.of(token), (index, clickType, slotActionType, gui) ->
                    tokenSelectCallback(token));
        }

        // Custom token button
        GuiElementInterface.ClickCallback customTokenCallback = (index, clickType, slotActionType, gui) ->
                customTokenBox(player);
        this.setSlot(9 * this.getHeight() - 7, TokenItemStack.of(botcCharacter.ReminderToken.CUSTOM), customTokenCallback);

        // Toggle see all/in play button
        if (!seeAll) {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("See All"), (index, clickType, slotActionType, gui) -> {
                new ReminderSelectGUI(player, script, seatManager, onSelectReminder, true, 0).open();
            }));
        } else {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("See In Play"), (index, clickType, slotActionType, gui) -> {
                new ReminderSelectGUI(player, script, seatManager, onSelectReminder, false, 0).open();
            }));
        }

        // Pagination buttons
        if (page > 0) {
            this.setSlot(9 * this.getHeight() - 9, SeatMenuLayer.buildButton(Text.of("Previous Page"), (index, clickType, slotActionType, gui) -> {
                new ReminderSelectGUI(player, script, seatManager, onSelectReminder, seeAll, page - 1).open();
            }));
        }
        if (page < pages - 1) {
            this.setSlot(9 * this.getHeight() - 1, SeatMenuLayer.buildButton(Text.of("Next Page"), (index, clickType, slotActionType, gui) -> {
                new ReminderSelectGUI(player, script, seatManager, onSelectReminder, seeAll, page + 1).open();
            }));
        }

        // Cancel button
        this.setSlot(9 * this.getHeight() - 3, SeatMenuLayer.buildButton(Text.of("Cancel"), (index, clickType, slotActionType, gui) ->
                this.close()));
    }

    private static int getPageCount(Script script, botcSeatManager seatManager, boolean seeAll) {
        int totalTokens = getReminderTokens(script, seatManager, seeAll).size();
        return (int) Math.ceil((double) totalTokens / TOKENS_PER_PAGE);
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

    private static List<botcCharacter.ReminderToken> getReminderTokens(Script script, botcSeatManager seatManager,
                                                                      boolean seeAll, int page) {
        List<botcCharacter.ReminderToken> allTokens = getReminderTokens(script, seatManager, seeAll);
        int fromIndex = page * TOKENS_PER_PAGE;
        int toIndex = Math.min(fromIndex + TOKENS_PER_PAGE, allTokens.size());
        if (fromIndex >= allTokens.size()) {
            return List.of();
        }
        return allTokens.subList(fromIndex, toIndex);
    }

    private static int countRows(Script script, botcSeatManager seatManager, boolean seeAll) {
        int count = getReminderTokens(script, seatManager, seeAll).size(); // +1 for custom token
        return (int) Math.ceil(count / 9.0);
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> getScreenSize(Script script,
                                                                                  botcSeatManager seatManager,
                                                                                  boolean seeAll) {
        return GrimoireGUI.getScreenSizeOfRows(countRows(script, seatManager, seeAll) + 1);
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
