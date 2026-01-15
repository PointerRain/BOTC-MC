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

public class PlayerCharacterSelectGUI extends AbstractSelectionGUI<botcCharacter> {
    private final boolean seeTravellers;
    protected final Script script;

    public PlayerCharacterSelectGUI(ServerPlayerEntity player, Script script,
                                    Function<botcCharacter, ?> onSelectCharacter, Runnable onCancel,
                                    boolean seeTravellers, int page) {

        super(player, getRoles(script, !seeTravellers ? List.of(
                Team.TOWNSFOLK,
                Team.OUTSIDER,
                Team.MINION,
                Team.DEMON
        ) : List.of(Team.TRAVELLER)), onSelectCharacter, onCancel, page);
        this.setTitle(Text.of("Select Character"));

        this.script = script;
        this.seeTravellers = seeTravellers;

        if (seeTravellers) {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("All Characters"),
                    (i, c, a, g) ->
                            new PlayerCharacterSelectGUI(player, script, onSelectCharacter, onCancel,
                                    false, 0).open()));
        } else {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("Travellers"),
                    (i, c, a, g) ->
                            new PlayerCharacterSelectGUI(player, script, onSelectCharacter, onCancel,
                                    true, 0).open()));
        }
    }

    protected static List<botcCharacter> getRoles(Script script, Collection<Team> teams) {
        List<botcCharacter> roles = new ArrayList<>();
        roles.add(botcCharacter.EMPTY);
        for (Team team : teams) {
            roles.addAll(script.getCharactersByTeam(team));
        }
        return roles;
    }

    @Override
    protected AbstractSelectionGUI<botcCharacter> newInstance(ServerPlayerEntity player, int page) {
        return new PlayerCharacterSelectGUI(this.player, this.script, this.onSelectItem, this.onCancel, this.seeTravellers, page);
    }

    @Override
    protected ItemStack getItemStack(botcCharacter item) {
        return TokenItemStack.of(item);
    }
}
