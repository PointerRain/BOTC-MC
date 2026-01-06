package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.game.seat.Seat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerHeadItemStack {
    public static ItemStack of(ServerPlayerEntity player) {
        ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
        ProfileComponent profile = new ProfileComponent(player.getGameProfile());
        headItem.set(DataComponentTypes.PROFILE, profile);
        return headItem;
    }

    public static ItemStack of(Seat seat) {
        ItemStack headItem = seat.hasPlayerEntity() ? of(seat.getPlayerEntity()) : new ItemStack(Items.PLAYER_HEAD);
        headItem.set(DataComponentTypes.CUSTOM_NAME, seat.getOccupantText());
        return headItem;
    }

    public static ItemStack of(Seat seat, int seatNumber) {
        ItemStack headItem = of(seat);
        headItem.setCount(seatNumber);
        return headItem;
    }
}
