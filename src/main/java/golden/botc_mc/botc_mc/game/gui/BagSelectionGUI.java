package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Function;

public class BagSelectionGUI extends AbstractMultiSelectGUI<botcCharacter> {

    protected final Script script;
    protected final botcSeatManager seatManager;

    public BagSelectionGUI(ServerPlayerEntity player,
                           Script script, botcSeatManager seatManager, List<botcCharacter> selectedItems,
                           Function<List<botcCharacter>, ?> onFinaliseSelection, Runnable onCancel,
                           int page) {
        super(player, script.characters(), selectedItems, onFinaliseSelection, onCancel, page);

        this.script = script;
        this.seatManager = seatManager;
    }

    @Override
    public void beforeOpen() {
        super.beforeOpen();

        if (seatManager != null) {
            ItemStack stack = new ItemStack(Items.PAPER);
            Text name = Text.translatable("gui.botc-mc.selection.bag").styled(style -> style.withItalic(false));
            int[] defaultCounts = botcSeatManager.COUNTS.get(seatManager.getSeatCount());
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
        }
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
        if (this.seatManager == null) {return true;}
        return this.selectedItems.size() == seatManager.getSeatCount();
    }
}
