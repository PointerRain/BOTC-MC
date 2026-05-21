package golden.botc_mc.botc_mc.game.gui.selection;

import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import golden.botc_mc.botc_mc.game.gui.ButtonBuilder;
import golden.botc_mc.botc_mc.game.gui.ButtonIcon;
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

/**
 * A multiselection GUI for selecting roles to be distributed.
 */
public class BagSelectionGUI extends AbstractMultiSelectGUI<botcCharacter> {

    protected final Script script;
    protected final botcSeatManager seatManager;
    private int requiredRoles;

    /**
     * Constructor for BagSelectionGUI.
     * @param player        The player for whom the GUI is being created.
     * @param script        The script characters are taken from
     * @param seatManager   The seat manager of the game
     * @param selectedItems Items to prefill the gui with
     * @param onFinaliseSelection The function to call when the selection is finalised.
     * @param onCancel            A runnable to call when the selection is cancelled.
     * @param page                The current page number (0-indexed).
     */
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

            this.setSlot(hotbarSlot(5), stack);

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
                this.setSlot(hotbarSlot(7), warningStack);
            }

            boolean hasCharacter = false;
            for (int i = 0; i < this.seatManager.getSeatCount(); i++) {
                if (this.seatManager.getSeatFromNumber(i+1).getCharacter() != botcCharacter.EMPTY
                    && this.seatManager.getSeatFromNumber(i+1).getCharacter().team() != Team.TRAVELLER) {
                    hasCharacter = true;
                    break;
                }
            }

            if (hasCharacter) {
                // 0: Clear Grimoire
                this.setSlot(hotbarSlot(0), ButtonBuilder.buildButton(
                        Text.translatable("gui.botc-mc.selection.bag.clear"),
                        ButtonIcon.DELETE, (i, c, a, g) -> {
                            this.seatManager.clearCharacters();
                            this.close();
                        })
                );
                // 2: Resend character info
                this.setSlot(hotbarSlot(2), ButtonBuilder.buildButton(
                        Text.translatable("gui.botc-mc.selection.bag.announce"),
                        ButtonIcon.ANNOUNCE, (i, c, a, g) -> {
                            this.seatManager.announceCharacters();
                            this.close();
                        }
                ));

            }

            // 1: Partial/Silent Assign characters
            if (!selectedItems.isEmpty() && selectedItems.size() <= requiredRoles) {
                this.setSlot(hotbarSlot(1), ButtonBuilder.buildButton(
                        Text.translatable("gui.botc-mc.selection.bag.silent"),
                        ButtonIcon.SILENT, (i, c, a, g) -> {
                            this.seatManager.partialAssignCharacters(selectedItems);
                            this.close();
                        }
                ));
            }

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
