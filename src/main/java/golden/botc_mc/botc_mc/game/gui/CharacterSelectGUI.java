package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.gui.SimpleGui;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Function;

public class CharacterSelectGUI extends SimpleGui {
    private final Script script;
    private final int rows;
    private final boolean includesTravellers;
    private final Function<? super botcCharacter, ?> onSelectCharacter;

    public CharacterSelectGUI(ServerPlayerEntity player, Script script, Function<? super botcCharacter, ?> onSelectCharacter) {
        super(getRecommendedSize(script), player, false);
        this.setTitle(Text.of("Select Character"));

        this.script = script;
        this.rows = countRows(script);
        this.includesTravellers = shouldIncludeTravellers(script);
        this.onSelectCharacter = onSelectCharacter;

        for (botcCharacter character : script.getCharactersByTeam(Team.TOWNSFOLK)) {
            this.setSlot(this.getFirstEmptySlot(), TokenItemStack.of(character), (index, clickType, slotActionType, gui) ->
                characterSelectCallback(character));
        }
        for (botcCharacter character : script.getCharactersByTeam(Team.OUTSIDER)) {
            this.setSlot(this.getFirstEmptySlot(), TokenItemStack.of(character), (index, clickType, slotActionType, gui) ->
                characterSelectCallback(character));
        }
        for (botcCharacter character : script.getCharactersByTeam(Team.MINION)) {
            this.setSlot(this.getFirstEmptySlot(), TokenItemStack.of(character), (index, clickType, slotActionType, gui) ->
                characterSelectCallback(character));
        }
        for (botcCharacter character : script.getCharactersByTeam(Team.DEMON)) {
            this.setSlot(this.getFirstEmptySlot(), TokenItemStack.of(character), (index, clickType, slotActionType, gui) ->
                characterSelectCallback(character));
        }
        if (this.includesTravellers) {
            for (botcCharacter character : script.getCharactersByTeam(Team.TRAVELLER)) {
                this.setSlot(this.getFirstEmptySlot(), TokenItemStack.of(character), (index, clickType, slotActionType, gui) ->
                characterSelectCallback(character));
            }
        }
    }

    void characterSelectCallback(botcCharacter character) {
        this.onSelectCharacter.apply(character);
        this.close();
    }

    private static int getRoleCount(Script script, boolean includesTravellers) {
        int count = script.getCharactersByTeam(Team.TOWNSFOLK).size() +
                    script.getCharactersByTeam(Team.OUTSIDER).size() +
                    script.getCharactersByTeam(Team.MINION).size() +
                    script.getCharactersByTeam(Team.DEMON).size();
        if (includesTravellers) {
            count += script.getCharactersByTeam(Team.TRAVELLER).size();
        }
        return count;
    }

    private static int countRows(Script script) {
        int count = getRoleCount(script, shouldIncludeTravellers(script));
        return (int) Math.ceil(count / 9.0);
    }

    private static boolean shouldIncludeTravellers(Script script) {
        return !script.getCharactersByTeam(Team.TRAVELLER).isEmpty() && getRoleCount(script, true) <= 54;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> getRecommendedSize(Script script) {
        int rows = countRows(script);
        if (rows <= 3) {
            return ScreenHandlerType.GENERIC_9X3;
        } else if (rows == 4) {
            return ScreenHandlerType.GENERIC_9X4;
        } else if (rows == 5) {
            return ScreenHandlerType.GENERIC_9X5;
        } else {
            return ScreenHandlerType.GENERIC_9X6; // Fallback to the largest size
        }
    }
}
