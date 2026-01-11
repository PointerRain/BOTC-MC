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

public class CharacterSelectGUI extends AbstractSelectionGUI<botcCharacter> {
    protected final Script script;
    private final Collection<Team> teams;

    public CharacterSelectGUI(ServerPlayerEntity player, Script script, Collection<Team> teams,
                              Function<botcCharacter, ?> onSelectCharacter, int page) {
        super(player, getRoles(script, teams), onSelectCharacter, page);
        this.setTitle(Text.of("Select Character"));

        this.script = script;
        this.teams = teams;
    }

    private static List<botcCharacter> getRoles(Script script, Collection<Team> teams) {
        List<botcCharacter> roles = new ArrayList<>();
        roles.add(botcCharacter.EMPTY);
        for (Team team : teams) {
            roles.addAll(script.getCharactersByTeam(team));
        }
        return roles;
    }

    @Override
    protected AbstractSelectionGUI<botcCharacter> newInstance(ServerPlayerEntity player, int page) {
        return new CharacterSelectGUI(player, this.script, this.teams, this.onSelectItem, page);
    }

    @Override
    protected ItemStack getItemStack(botcCharacter item) {
        return TokenItemStack.of(item);
    }
}
