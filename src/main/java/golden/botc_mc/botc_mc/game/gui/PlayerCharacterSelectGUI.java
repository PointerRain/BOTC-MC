package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Selection GUI for player characters.
 */
public class PlayerCharacterSelectGUI extends AbstractSingleSelectGUI<botcCharacter> {
    private final boolean seeTravellers;
    protected final Script script;

    /**
     * Constructor for PlayerCharacterSelectGUI.
     * @param player The player for whom the GUI is being created.
     * @param script The game script containing character information.
     * @param onSelectCharacter A function to call when a character is selected.
     * @param onCancel A runnable to call when the selection is cancelled.
     * @param seeTravellers Whether to include Traveller characters in the selection.
     * @param page The current page number (0-indexed).
     */
    public PlayerCharacterSelectGUI(ServerPlayerEntity player, Script script,
                                    Function<botcCharacter, ?> onSelectCharacter, Runnable onCancel,
                                    boolean seeTravellers, int page) {

        super(player, getRoles(script, !seeTravellers ? List.of(
                Team.TOWNSFOLK,
                Team.OUTSIDER,
                Team.MINION,
                Team.DEMON
        ) : List.of(Team.TRAVELLER), seeTravellers), onSelectCharacter, onCancel, page);
        this.setTitle(Text.translatable("gui.botc-mc.selection.character"));

        this.script = script;
        this.seeTravellers = seeTravellers;
    }

    @Override
    public void beforeOpen() {
        super.beforeOpen();

        if (seeTravellers) {
            this.setSlot(9 * this.getHeight() - 5, ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.selection.character.non_travellers"),
                    ButtonIcon.MORE,
                    (i, c, a, g) -> new PlayerCharacterSelectGUI(player, script, onSelectItem, onCancel,
                                                     false, 0).open()));
        } else {
            this.setSlot(9 * this.getHeight() - 5, ButtonBuilder.buildButton(
                    Text.translatable("gui.botc-mc.selection.character.travellers"),
                    ButtonIcon.LESS,
                    (i, c, a, g) -> new PlayerCharacterSelectGUI(player, script, onSelectItem, onCancel,
                                                     true, 0).open()));
        }
    }

    /**
     * Get a list of player roles based on the provided teams.
     * @param script The game script containing character information.
     * @param teams The collection of teams to filter characters by.
     * @return A list of player characters.
     */
    protected static List<botcCharacter> getRoles(Script script, Collection<Team> teams, boolean seeAll) {
        List<botcCharacter> roles = new ArrayList<>();
        roles.add(botcCharacter.EMPTY);
        for (Team team : teams) {
            roles.addAll(script.getCharactersByTeam(team, seeAll));
        }
        return roles;
    }

    @Override
    protected AbstractSelectionGUI<botcCharacter> newInstance(ServerPlayerEntity player, int page) {
        return new PlayerCharacterSelectGUI(this.player, this.script, this.onSelectItem, this.onCancel, this.seeTravellers, page);
    }

    @Override
    protected ItemStack getItemStack(botcCharacter item) {
        return TokenItemStack.of(item, this.script);
    }
}
