package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.seat.Seat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public record TokenItemStack(ItemStack tokenItem) {

    public static ItemStack of(botcCharacter character) {
        ItemStack tokenItem = new ItemStack(
                character.team() == null ? Items.FLOW_POTTERY_SHERD :
                switch (character.team()) {
                    case Team.TOWNSFOLK -> Items.HEART_POTTERY_SHERD;
                    case Team.OUTSIDER -> Items.ANGLER_POTTERY_SHERD;
                    case Team.MINION -> Items.BREWER_POTTERY_SHERD;
                    case Team.DEMON -> Items.SKULL_POTTERY_SHERD;
                    case Team.TRAVELLER -> Items.PRIZE_POTTERY_SHERD;
                    default -> Items.FLOW_POTTERY_SHERD;
                }
        );
        MutableText nameText = (MutableText) character.toFormattedText(false);
        nameText.styled(style -> style.withBold(true).withItalic(false));
        tokenItem.set(DataComponentTypes.CUSTOM_NAME, nameText);
        if (character == botcCharacter.EMPTY) {
            return tokenItem;
        }
        List<Text> loreLines = new ArrayList<>();
        for (String line : character.ability().split("\\.")) {
            MutableText loreLine = (MutableText) Text.of(line.trim() + ".");
            loreLine.styled(style -> style.withItalic(false).withColor(Formatting.GRAY));
            loreLines.add(loreLine);
        }
        tokenItem.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
        return tokenItem;
    }

    public static ItemStack of(Seat seat) {
        ItemStack tokenItem = of(seat.getCharacter());
        tokenItem.set(DataComponentTypes.CUSTOM_NAME, seat.getCharacterText());
        return tokenItem;
    }
}
