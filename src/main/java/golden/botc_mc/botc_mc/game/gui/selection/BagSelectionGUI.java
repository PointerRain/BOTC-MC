package golden.botc_mc.botc_mc.game.gui.selection;

import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import golden.botc_mc.botc_mc.game.items.TokenItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BagSelectionGUI extends AbstractMultiSelectGUI<botcCharacter> {

    protected final Script script;
    protected final botcSeatManager seatManager;
    private int requiredRoles;

    public BagSelectionGUI(ServerPlayerEntity player,
                           Script script, botcSeatManager seatManager, List<botcCharacter> selectedItems,
                           Consumer<List<botcCharacter>> onFinaliseSelection, Consumer<List<botcCharacter>> onCancel,
                           int page) {
        super(player, getAllCharacters(script), selectedItems, onFinaliseSelection, onCancel, page);

        this.script = script;
        this.seatManager = seatManager;

        this.requiredRoles = 0;
        for (int i = 0; i < seatManager.getSeatCount(); i++) {
            if (seatManager.getSeatFromNumber(i+1).getCharacter() != botcCharacter.EMPTY && seatManager.getSeatFromNumber(i+1).getCharacter().team() == Team.TRAVELLER) {
                continue;
            }
            requiredRoles++;
        }
        if (requiredRoles >= botcSeatManager.ROLES_MAX) {requiredRoles = botcSeatManager.ROLES_MAX;}
    }

    /**
     * Gets all characters selectable in the bag selection GUI.
     * @param script The script to use characters from.
     * @return A list of all characters in the script that should be in the GUI.
     */
    private static List<botcCharacter> getAllCharacters(Script script) {
        List<botcCharacter> characters = new ArrayList<>();
        characters.addAll(script.getCharactersByTeam(Team.TOWNSFOLK, false));
        characters.addAll(script.getCharactersByTeam(Team.OUTSIDER, false));
        characters.addAll(script.getCharactersByTeam(Team.MINION, false));
        characters.addAll(script.getCharactersByTeam(Team.DEMON, false));
        return characters;
    }

    @Override
    public void beforeOpen() {
        super.beforeOpen();

        if (seatManager != null) {
            ItemStack stack = new ItemStack(Items.PAPER);
            Text name = Text.translatable("gui.botc-mc.selection.bag").styled(style -> style.withItalic(false));
            int[] defaultCounts = botcSeatManager.getRoleCount(seatManager.getSeatCount());
            int[] counts = {0, 0, 0, 0};
            for (botcCharacter character : selectedItems) {
                counts[character.team().ordinal()]++;
            }

            LoreComponent lore = new LoreComponent(List.of(
                    Text.translatable("gui.botc-mc.selection.bag.townsfolk", counts[0], defaultCounts[0]).styled(style -> style
                            .withItalic(false).withFormatting(Team.TOWNSFOLK.getColour(false))),
                    Text.translatable("gui.botc-mc.selection.bag.outsiders", counts[1], defaultCounts[1]).styled(style -> style
                            .withItalic(false).withFormatting(Team.OUTSIDER.getColour(false))),
                    Text.translatable("gui.botc-mc.selection.bag.minions", counts[2], defaultCounts[2]).styled(style -> style
                            .withItalic(false).withFormatting(Team.MINION.getColour(false))),
                    Text.translatable("gui.botc-mc.selection.bag.demons", counts[3], defaultCounts[3]).styled(style -> style
                            .withItalic(false).withFormatting(Team.DEMON.getColour(false)))));

            stack.set(DataComponentTypes.CUSTOM_NAME, name);
            stack.set(DataComponentTypes.LORE, lore);

            this.setSlot(2 + 9 * this.getHeight() + 9 * 3, stack);

            List<botcCharacter> modifyingCharacters = this.selectedItems.stream().filter(botcCharacter::setup).toList();
            if (!modifyingCharacters.isEmpty()) {
                ItemStack warningStack = new ItemStack(Items.YELLOW_DYE);
                Text warningName;
                if (modifyingCharacters.size() == 1) {
                    warningName = Text.translatable("gui.botc-mc.selection.bag.warning.single", modifyingCharacters.getFirst().toFormattedText(false, false, false, false));
                } else {
                    warningName = Text.translatable("gui.botc-mc.selection.bag.warning.multiple", modifyingCharacters.size());
                    LoreComponent warningLore = new LoreComponent(modifyingCharacters.stream().map(botcCharacter ->
                            botcCharacter.toFormattedText(false, false, true, false)).toList());
                    warningStack.set(DataComponentTypes.LORE, warningLore);
                }
                warningStack.set(DataComponentTypes.CUSTOM_NAME, warningName);
                this.setSlot(6 + 9 * this.getHeight() + 9 * 3, warningStack);
            }


            // TODO: Want buttons for:
            // Assign without notifying players
            // Clear characters?
            // Resend characters?
        }
    }

    @Override
    protected boolean canSelectItem(botcCharacter item) {
        return !selectedItems.contains(item);
    }

    @Override
    protected AbstractSelectionGUI<botcCharacter> newInstance(ServerPlayerEntity player, int page) {
        return new BagSelectionGUI(this.player, this.script, this.seatManager, this.selectedItems,
                this.onFinaliseSelection, this.onCancel, page);
    }

    @Override
    protected ItemStack getItemStack(botcCharacter item) {
        return TokenItemStack.of(item);
    }

    @Override
    protected boolean canFinalise() {
        return this.selectedItems.size() == requiredRoles;
    }

    @Override
    protected Text getFinaliseReason() {
        return Text.translatable("gui.botc-mc.selection.bag.reason", selectedItems.size(), requiredRoles);
    }
}
