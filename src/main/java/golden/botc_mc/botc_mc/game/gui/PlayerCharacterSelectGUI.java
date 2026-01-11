package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Function;

public class PlayerCharacterSelectGUI extends CharacterSelectGUI {
    private final boolean seeTravellers;

    public PlayerCharacterSelectGUI(ServerPlayerEntity player, Script script, boolean seeTravellers,
                                    Function<botcCharacter, ?> onSelectCharacter, int page) {
        super(player, script, !seeTravellers ? List.of(
                Team.TOWNSFOLK,
                Team.OUTSIDER,
                Team.MINION,
                Team.DEMON
        ) : List.of(Team.TRAVELLER), onSelectCharacter, page);

        this.seeTravellers = seeTravellers;

        if (seeTravellers) {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("All Characters"),
                    (i, c, a, g) -> new PlayerCharacterSelectGUI(player, script, false, onSelectCharacter, 0).open()));
        } else {
            this.setSlot(9 * this.getHeight() - 5, SeatMenuLayer.buildButton(Text.of("Travellers"),
                    (i, c, a, g) -> new PlayerCharacterSelectGUI(player, script, true, onSelectCharacter, 0).open()));
        }
    }

    @Override
    protected AbstractSelectionGUI<botcCharacter> newInstance(ServerPlayerEntity player, int page) {
        return new PlayerCharacterSelectGUI(this.player, this.script, this.seeTravellers, this.onSelectItem, page);
    }
}
