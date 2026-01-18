package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Selection GUI for NPC characters.
 */
public class NPCCharacterSelectGUI extends AbstractSelectionGUI<botcCharacter> {
    protected final Script script;
    protected final botcSeatManager seatManager;

    /**
     * Constructor for NPCCharacterSelectGUI.
     * @param player The player for whom the GUI is being created.
     * @param script The game script containing character information.
     * @param seatManager The seat manager managing NPC assignments.
     * @param onSelectCharacter A function to call when a character is selected.
     * @param onCancel A runnable to call when the selection is cancelled.
     * @param page The current page number (0-indexed).
     */
    public NPCCharacterSelectGUI(ServerPlayerEntity player, Script script, botcSeatManager seatManager,
                                Function<botcCharacter, ?> onSelectCharacter, Runnable onCancel, int page) {
        super(player, getRoles(script, seatManager, List.of(Team.FABLED, Team.LORIC)), onSelectCharacter, onCancel, page);
        this.setTitle(Text.of("Select Character"));

        this.script = script;
        this.seatManager = seatManager;
    }

    /**
     * Get a list of NPC roles based on the provided teams.
     * @param script The game script containing character information.
     * @param seatManager The seat manager managing NPC assignments.
     * @param teams The collection of teams to filter characters by.
     * @return A list of NPC characters.
     */
    protected static List<botcCharacter> getRoles(Script script, botcSeatManager seatManager, Collection<Team> teams) {
        List<botcCharacter> roles = new ArrayList<>();
        for (Team team : teams) {
            for (botcCharacter character : script.getCharactersByTeam(team)) {
                if (!seatManager.getNPCs().contains(character)) {
                    roles.add(character);
                }
            }
        }
        return roles;
    }

    @Override
    protected AbstractSelectionGUI<botcCharacter> newInstance(ServerPlayerEntity player, int page) {
        return new NPCCharacterSelectGUI(this.player, this.script, this.seatManager,
                this.onSelectItem, this.onCancel, page);
    }

    @Override
    protected ItemStack getItemStack(botcCharacter item) {
        return TokenItemStack.of(item);
    }
}
