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

public class NPCCharacterSelectGUI extends AbstractSelectionGUI<botcCharacter> {
    protected final Script script;
    protected final botcSeatManager seatManager;

    public NPCCharacterSelectGUI(ServerPlayerEntity player, Script script, botcSeatManager seatManager,
                                Function<botcCharacter, ?> onSelectCharacter, Runnable onCancel, int page) {
        super(player, getRoles(script, seatManager, List.of(Team.FABLED, Team.LORIC)), onSelectCharacter, onCancel, page);
        this.setTitle(Text.of("Select Character"));

        this.script = script;
        this.seatManager = seatManager;
    }

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
