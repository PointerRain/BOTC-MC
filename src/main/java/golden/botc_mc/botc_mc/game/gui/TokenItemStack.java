package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.CharacterLoader;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.seat.Seat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for creating token ItemStacks for characters and seats.
 */
public record TokenItemStack(ItemStack tokenItem) {

    private static NbtComponent createCustomData(boolean actsFirstNight, boolean actsOtherNights,
                                                 boolean setup, int reminders, String team) {
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("firstNight", actsFirstNight);
        tag.putBoolean("otherNights", actsOtherNights);
        tag.putBoolean("setup", setup);
        tag.putString("reminders", String.valueOf(reminders));
        tag.putString("team", team);
        return NbtComponent.of(tag);
    }

    private static CustomModelDataComponent createCustomModelData(List<Integer> colours) {
        return new CustomModelDataComponent(List.of(), List.of(), List.of(), colours);
    }

    private static NbtComponent createCustomData(botcCharacter character, Script script) {
        boolean actsFirstNight = script.firstNightOrder(false).stream()
            .anyMatch(action -> Objects.equals(action.id, character.id()));
        boolean actsOtherNights = script.otherNightOrder().stream()
            .anyMatch(action -> Objects.equals(action.id, character.id()));

        int reminders = (character.reminders() != null ? character.reminders().size() : 0) +
                        (character.remindersGlobal() != null ? character.remindersGlobal().size() : 0);

        return createCustomData(actsFirstNight, actsOtherNights, character.setup(), reminders,
                character.team() != null ? character.team().toString() : "none");
    }

    private static NbtComponent createCustomData(botcCharacter character) {
        boolean actsFirstNight = CharacterLoader.firstNightOrder.stream()
            .anyMatch(action -> Objects.equals(action, character.id()));
        boolean actsOtherNights = CharacterLoader.otherNightOrder.stream()
            .anyMatch(action -> Objects.equals(action, character.id()));

        int reminders = (character.reminders() != null ? character.reminders().size() : 0) +
                        (character.remindersGlobal() != null ? character.remindersGlobal().size() : 0);

        return createCustomData(actsFirstNight, actsOtherNights, character.setup(), reminders,
                character.team() != null ? character.team().toString() : "none");
    }

    private static ItemStack createUnformattedToken(botcCharacter character) {
        ItemStack tokenItem = new ItemStack(
            character.team() == null ? Items.FLOW_POTTERY_SHERD : switch (character.team()) {
                case Team.TOWNSFOLK -> Items.HEART_POTTERY_SHERD;
                case Team.OUTSIDER -> Items.ANGLER_POTTERY_SHERD;
                case Team.MINION -> Items.BREWER_POTTERY_SHERD;
                case Team.DEMON -> Items.SKULL_POTTERY_SHERD;
                case Team.TRAVELLER -> Items.PRIZE_POTTERY_SHERD;
                case Team.FABLED -> Items.BURN_POTTERY_SHERD;
                case Team.LORIC -> Items.PLENTY_POTTERY_SHERD;
            }
        );

        String tokenPath = character.token() != null ? "tokens/" + character.token() : "tokens/empty";
        tokenItem.set(DataComponentTypes.ITEM_MODEL, Identifier.of(botc.ID, tokenPath));

        MutableText nameText = (MutableText) character.toFormattedText(false, true, true, false);
        nameText.styled(style -> style.withItalic(false));
        tokenItem.set(DataComponentTypes.CUSTOM_NAME, nameText);
        if (character == botcCharacter.EMPTY) {
            return tokenItem;
        }
//        List<Text> loreLines = new ArrayList<>();
//        for (String line : character.abilityText().getString().split("\\.")) {
//            MutableText loreLine = (MutableText) Text.of(line.trim() + ".");
//            loreLine.styled(style -> style.withItalic(false).withColor(Formatting.GRAY));
//            loreLines.add(loreLine);
//        }
        MutableText loreText = (MutableText) character.abilityText();
        loreText.styled(style -> style.withItalic(false).withColor(Formatting.GRAY));
        List<Text> loreLines = List.of(character.abilityText());
        tokenItem.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
        return tokenItem;
    }

    /**
     * Create a token ItemStack for the given character.
     * The token's appearance and lore are based on the character's team and ability.<br>
     * {@link #of(Seat, Script)} is preferred when creating tokens for seats, as it sets the name and alignment appropriately.<br>
     * {@link #of(botcCharacter, Script)} is preferred when creating tokens for characters in a specific script, as it includes script-specific data.
     * @param character The character for whom to create the token.
     * @return An ItemStack representing the character's token.
     */
    public static ItemStack of(botcCharacter character) {
        ItemStack tokenItem = createUnformattedToken(character);

        List<Integer> colours = List.of();
        if (character.team() != null && character.team().getColour(false) != null) {
        Integer colourValue = character.team().getColour(false).getColorValue();
        if (colourValue != null) {
            colours = List.of(colourValue);
            }
        }

        tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, createCustomModelData(colours));
        tokenItem.set(DataComponentTypes.CUSTOM_DATA, createCustomData(character));

        return tokenItem;
    }

    public static ItemStack of(botcCharacter character, Script script) {
        botc.LOGGER.info("This script4 is: {}", script);
        ItemStack tokenItem = createUnformattedToken(character);

        List<Integer> colours = List.of();
        if (character.team() != null && character.team().getColour(false) != null) {
            Integer colourValue = character.team().getColour(false).getColorValue();
            if (colourValue != null) {
                colours = List.of(colourValue);
            }
        }

        tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, createCustomModelData(colours));
        tokenItem.set(DataComponentTypes.CUSTOM_DATA, createCustomData(character, script));

        return tokenItem;
    }

    /**
     * Create a token ItemStack for the given seat.
     * The token's name is set according to the seat's character text.
     * @param seat The seat for whom to create the token.
     * @return An ItemStack representing the seat's token.
     */
    public static ItemStack of(Seat seat, Script script) {
        botcCharacter character = seat.getCharacter();
        ItemStack tokenItem = createUnformattedToken(character);
        tokenItem.set(DataComponentTypes.CUSTOM_NAME, seat.getCharacterText());

        List<Integer> colours = List.of();
        Integer colourValue = seat.getColour(false).getColorValue();
        if (colourValue != null) {
            colours = List.of(colourValue);
        }

        tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, createCustomModelData(colours));
        tokenItem.set(DataComponentTypes.CUSTOM_DATA, createCustomData(character, script));

        return tokenItem;
    }

    /**
     * Create a token ItemStack for the given reminder token.
     * The token's name and lore are set according to the reminder text and associated character.
     * @param token The reminder token for whom to create the ItemStack.
     * @return An ItemStack representing the reminder token.
     */
    public static ItemStack of(botcCharacter.ReminderToken token) {
        ItemStack tokenItem = new ItemStack(Items.PAPER);

        if (token.character() == null || token.character() == botcCharacter.EMPTY || token.character().token() == null) {
            tokenItem.set(DataComponentTypes.ITEM_MODEL, Identifier.of(botc.ID, "reminders/empty"));
        } else {
            tokenItem.set(DataComponentTypes.ITEM_MODEL, Identifier.of(botc.ID, "reminders/" + token.character().token()));
            Integer colourValue = token.character().team().getColour(false).getColorValue();
            if (colourValue != null) {
                tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                    List.of(), List.of(), List.of(), List.of(colourValue)));
            }
        }

        MutableText reminderText = (MutableText) token.toText();
        reminderText.styled(style -> style.withItalic(false));
        tokenItem.set(DataComponentTypes.CUSTOM_NAME, reminderText);

        if (token.character() != botcCharacter.EMPTY && token.character() != null) {
            MutableText characterName = (MutableText) token.character().toFormattedText(false, false, true, false);
            characterName.styled(style -> style.withBold(false).withItalic(false));
            tokenItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(characterName)));
        }

        return tokenItem;
    }
}