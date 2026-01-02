package golden.botc_mc.botc_mc.game.gui;

import eu.pb4.sgui.api.gui.SimpleGui;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.botcSeatManager;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

public class GrimoireGUI extends SimpleGui {

    public GrimoireGUI(ServerPlayerEntity player, botcSeatManager seatManager) {
        super(ScreenHandlerType.GENERIC_9X6, player, true);
        this.setTitle(Text.of("Grimoire"));

        int layoutStyle = getLayoutType(seatManager.getSeatCount());

        // Add items to the GUI based on seat and layoutStyle
        // This is a placeholder for actual implementation
        for (int n = 0; n < seatManager.getSeatCount(); n++) {
            PlayerSeat seat = seatManager.getSeatFromNumber(n+1);
            ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
            MutableText headText = (MutableText) Text.of((n + 1) + ": ");
            headText.styled(style -> style.withItalic(false).withColor(Formatting.WHITE));
            if (seat.hasPlayerEntity()) {
                ProfileComponent profile = new ProfileComponent(seat.getPlayerEntity().getGameProfile());
                headItem.set(DataComponentTypes.PROFILE, profile);
            }
            headText.append(seat.getOccupantText());
            headItem.set(DataComponentTypes.CUSTOM_NAME, headText);

            ItemStack tokenItem = new ItemStack(
                    switch (seat.getAlignment()) {
                        case Team.Alignment.GOOD -> Items.ARCHER_POTTERY_SHERD;
                        case Team.Alignment.NEUTRAL -> Items.PLENTY_POTTERY_SHERD;
                        case Team.Alignment.EVIL -> Items.SKULL_POTTERY_SHERD;
                        default -> Items.FLOW_POTTERY_SHERD;
                    }
            );
            tokenItem.set(DataComponentTypes.CUSTOM_NAME, seat.getCharacterText());

            if (layoutStyle == 1) {
                this.setSlot(9 * n, headItem);
                this.setSlot(9 * n + 1, tokenItem);
            }
            if (layoutStyle == 2) {
                this.setSlot(n, headItem);
                this.setSlot(n + 9, tokenItem);
            }
            if (layoutStyle == 3) {
                this.setSlot(n < 6 ? 9 * n : 9 * (n % 6) + 8, headItem);
                this.setSlot(n < 6 ? 9 * n + 1 : 9 * (n % 6) + 7, tokenItem);
            }
            if (layoutStyle == 4) {
                this.setSlot(n < 9 ? n : n + 4 * 9, headItem);
                this.setSlot(n < 9 ? n + 9 : n + 3 * 9, tokenItem);
            }
//            List<String> reminders = seat.getReminders();
//            for (int i = 0; i < reminders.size(); i++) {
//                ItemStack reminderItem = new ItemStack(Items.PAPER);
//                MutableText reminderText = (MutableText) Text.of(reminders.get(i));
//                reminderText.styled(style -> style.withItalic(false));
//                reminderItem.set(DataComponentTypes.CUSTOM_NAME, reminderText);
//                this.setSlot(18 + n + 9 * i, reminderItem);
//            }
        }
    }

    private static int getLayoutType(int seatCount) {
        if (seatCount <= 6) return 1;
        if (seatCount <= 9) return 2;
        if (seatCount <= 12) return 3;
        if (seatCount <= 18) return 4;
        return 0;
    }

    private static int getMaxReminders(int layoutType) {
        return switch (layoutType) {
            case 1 -> 7;
            case 2 -> 4;
            case 3 -> 2;
            case 4 -> 1;
            default -> 1;
        };
    }
}
