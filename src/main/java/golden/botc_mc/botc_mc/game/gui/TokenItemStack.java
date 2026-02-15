package golden.botc_mc.botc_mc.game.gui;

import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.CharacterLoader;
import golden.botc_mc.botc_mc.game.NightAction;
import golden.botc_mc.botc_mc.game.Script;
import golden.botc_mc.botc_mc.game.Team;
import golden.botc_mc.botc_mc.game.botcCharacter;
import golden.botc_mc.botc_mc.game.seat.Seat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

    private static CustomModelDataComponent customModelData(boolean actsFirstNight, boolean actsOtherNights,
                                                            boolean setup, int reminders, String team,
                                                            List<Integer> colours) {
        List<Float> floats = List.of((float) reminders);
        List<Boolean> booleans = List.of(actsFirstNight, actsOtherNights, setup);
        List<String> strings = List.of(team);

        return new CustomModelDataComponent(
            floats, booleans, strings, colours
        );
    }

    private static CustomModelDataComponent customModelData(botcCharacter character, Script script,
                                                            List<Integer> colours) {
        boolean actsFirstNight = false;
        boolean actsOtherNights = false;
        for (NightAction nightAction : script.firstNightOrder(false)) {
            if (Objects.equals(nightAction.id, character.id())) {
                actsFirstNight = true;
                break;
            }
        }
        for (NightAction nightAction : script.otherNightOrder()) {
            if (Objects.equals(nightAction.id, character.id())) {
                actsOtherNights = true;
                break;
            }
        }

        int reminders = 0;
        if (character.reminders() != null) {
            reminders += character.reminders().size();
        }
        if (character.remindersGlobal() != null) {
            reminders += character.remindersGlobal().size();
        }

        return customModelData(actsFirstNight, actsOtherNights, character.setup(), reminders,
                character.team() != null ? character.team().toString() : "none",
                colours);
    }

    private static CustomModelDataComponent customModelData(botcCharacter character, List<Integer> colours) {
        boolean actsFirstNight = false;
        boolean actsOtherNights = false;
        for (String nightAction : CharacterLoader.firstNightOrder) {
            if (Objects.equals(nightAction, character.id())) {
                actsFirstNight = true;
                break;
            }
        }
        for (String nightAction : CharacterLoader.otherNightOrder) {
            if (Objects.equals(nightAction, character.id())) {
                actsOtherNights = true;
                break;
            }
        }

        int reminders = 0;
        if (character.reminders() != null) {
            reminders += character.reminders().size();
        }
        if (character.remindersGlobal() != null) {
            reminders += character.remindersGlobal().size();
        }

        return customModelData(actsFirstNight, actsOtherNights, character.setup(), reminders,
                character.team() != null ? character.team().toString() : "none",
                colours);
    }


    private static ItemStack unformattedToken(botcCharacter character) {
        ItemStack tokenItem = new ItemStack(
                character.team() == null ? Items.FLOW_POTTERY_SHERD :
                switch (character.team()) {
                    case Team.TOWNSFOLK -> Items.HEART_POTTERY_SHERD;
                    case Team.OUTSIDER -> Items.ANGLER_POTTERY_SHERD;
                    case Team.MINION -> Items.BREWER_POTTERY_SHERD;
                    case Team.DEMON -> Items.SKULL_POTTERY_SHERD;
                    case Team.TRAVELLER -> Items.PRIZE_POTTERY_SHERD;
                    case Team.FABLED -> Items.BURN_POTTERY_SHERD;
                    case Team.LORIC -> Items.PLENTY_POTTERY_SHERD;
                }
        );

        if (character.token() != null) {
            tokenItem.set(DataComponentTypes.ITEM_MODEL, Identifier.of(botc.ID, "tokens/" + character.token()));
        } else {
            tokenItem.set(DataComponentTypes.ITEM_MODEL, Identifier.of(botc.ID, "tokens/empty"));
        }

        MutableText nameText = (MutableText) character.toFormattedText(false, true, true, false);
        nameText.styled(style -> style.withItalic(false));
        tokenItem.set(DataComponentTypes.CUSTOM_NAME, nameText);
        if (character == botcCharacter.EMPTY) {
            return tokenItem;
        }
        List<Text> loreLines = new ArrayList<>();
        for (String line : character.abilityText().getString().split("\\.")) {
            MutableText loreLine = (MutableText) Text.of(line.trim() + ".");
            loreLine.styled(style -> style.withItalic(false).withColor(Formatting.GRAY));
            loreLines.add(loreLine);
        }
        tokenItem.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
        return tokenItem;
    }

    /**
     * Create a token ItemStack for the given character.
     * The token's appearance and lore are based on the character's team and ability.
     * {@code of(Seat)} is preferred when creating tokens for seats, as it sets the name and alignment appropriately.
     * @param character The character for whom to create the token.
     * @return An ItemStack representing the character's token.
     */
    public static ItemStack ofx(botcCharacter character) {
        ItemStack tokenItem = unformattedToken(character);

        List<Integer> colours = List.of();
        if (character.team() != null && character.team().getColour(false) != null) {
            Integer colourValue = character.team().getColour(false).getColorValue();
            if (colourValue != null) {
                colours = List.of(colourValue);
            }
        }

        tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, customModelData(character, colours));

        return tokenItem;
    }

    public static ItemStack of(botcCharacter character, Script script) {
        botc.LOGGER.info("This script4 is: {}", script);
        ItemStack tokenItem = unformattedToken(character);

        List<Integer> colours = List.of();
        if (character.team() != null && character.team().getColour(false) != null) {
            Integer colourValue = character.team().getColour(false).getColorValue();
            if (colourValue != null) {
                colours = List.of(colourValue);
            }
        }

        tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, customModelData(character, script, colours));

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
        ItemStack tokenItem = unformattedToken(character);

        tokenItem.set(DataComponentTypes.CUSTOM_NAME, seat.getCharacterText());

        List<Integer> colours = List.of();
        Integer colourValue = seat.getColour(false).getColorValue();
        if (colourValue != null) {
            colours = List.of(colourValue);
        }

        tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, customModelData(character, script, colours));

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
                CustomModelDataComponent customModelDataComponent = new CustomModelDataComponent(
                    List.of(), List.of(), List.of(), List.of(colourValue)
                );
                tokenItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, customModelDataComponent);
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