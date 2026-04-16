package golden.botc_mc.botc_mc.game.gui.selection;

import eu.pb4.sgui.api.gui.SignGui;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.DyeColor;

import java.util.function.Function;

public abstract class AbstractTextEntryGUI extends SignGui {

    final Function<String[], ?> onEnterText;

    /**
     * Constructor for AbstractTextEntryGUI.
     * @param player The player for whom the text entry box is being created.
     * @param onEnterText A function to call when a text reminder is entered.
     */
    public AbstractTextEntryGUI(ServerPlayerEntity player,
                          Function<String[], ?> onEnterText) {
        super(player);
        this.onEnterText = onEnterText;
        this.setSignType(Blocks.CRIMSON_WALL_SIGN);
        this.setColor(DyeColor.WHITE);
        this.signEntity.changeText(signText -> signText.withGlowing(true), true);
    }

    @Override
    public void onClose() {
        super.onClose();
        String[] text = new String[]{
                this.getLine(0).getString().trim(),
                this.getLine(1).getString().trim(),
                this.getLine(2).getString().trim(),
                this.getLine(3).getString().trim()};

        this.onEnterText.apply(text);
    }
}
