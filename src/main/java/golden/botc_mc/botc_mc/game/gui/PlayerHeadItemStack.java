package golden.botc_mc.botc_mc.game.gui;

import com.mojang.authlib.properties.PropertyMap;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.seat.PlayerSeat;
import golden.botc_mc.botc_mc.game.seat.Seat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility class for creating player head ItemStacks.
 * Can create heads for specific players or seats.
 */
public class PlayerHeadItemStack {

    // Set to true to assign default profiles to empty seats.
    private static final boolean POPULATE_HEADS = true;

    /**
     * Create a player head ItemStack for the given player.
     * @param player The player whose head to create.
     * @return An ItemStack representing the player's head.
     */
    public static ItemStack of(ServerPlayerEntity player) {
        ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
        ProfileComponent profile = new ProfileComponent(player.getGameProfile());
        headItem.set(DataComponentTypes.PROFILE, profile);
        return headItem;
    }

    /**
     * Create a player head ItemStack for the given seat.
     * If the seat has a player entity, use that player's head; otherwise, use a default player head.
     * The head will be named according to the seat's occupant text.
     * Prefer using {@code of(Seat, int)} as it sets the seat number as the item count.
     * @param seat The seat whose occupant's head to create.
     * @return An ItemStack representing the seat's occupant's head.
     */
    public static ItemStack of(Seat seat) {
        ItemStack headItem = seat.hasPlayerEntity() ? of(seat.getPlayerEntity()) : new ItemStack(Items.PLAYER_HEAD);

        headItem.set(DataComponentTypes.ITEM_MODEL, Identifier.of(botc.ID, "player_head"));

        CustomModelDataComponent customModelDataComponent = new CustomModelDataComponent(
            List.of(),
            List.of(seat.isAlive(), seat instanceof PlayerSeat && ((PlayerSeat) seat).canGhostVote()),
            List.of(), List.of()
        );
        headItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, customModelDataComponent);

        headItem.set(DataComponentTypes.CUSTOM_NAME, seat.getOccupantText());
        return headItem;
    }

    /**
     * Create a player head ItemStack for the given seat with a specific seat number.
     * The head will be named according to the seat's occupant text and the count set to the seat number.
     * This is preferred over {@code of(Seat)} as it sets the seat number as the item count.
     * @param seat The seat whose occupant's head to create.
     * @param seatNumber The seat number to set as the item count.
     * @return An ItemStack representing the seat's occupant's head with the specified count.
     */
    public static ItemStack of(Seat seat, int seatNumber) {
        ItemStack headItem = of(seat);
        headItem.setCount(seatNumber);
        if (POPULATE_HEADS && !seat.hasPlayerEntity()) {
            ProfileComponent profile = new ProfileComponent(Optional.empty(),
                    Optional.of(new UUID(0, seatNumber)),
                    new PropertyMap());
            headItem.set(DataComponentTypes.PROFILE, profile);
        }
        return headItem;
    }
}
