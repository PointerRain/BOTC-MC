package golden.botc_mc.botc_mc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum Team {
    TOWNSFOLK,
    OUTSIDER,
    MINION,
    DEMON,
    FABLED,
    LORIC,
    TRAVELLER;



    public static final Codec<Team> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("team").forGetter(Team::toString)
    ).apply(instance, Team::fromString));

//    /**
//     * Get the string representation of the team.
//     * @return the team as a string
//     */
//    @Override
//    public String toString() {
//        return switch (this) {
//            case TOWNSFOLK -> "Townsfolk";
//            case OUTSIDER -> "Outsider";
//            case MINION -> "Minion";
//            case DEMON -> "Demon";
//            case FABLED -> "Fabled";
//            case LORIC -> "Loric";
//            case TRAVELLER -> "Traveller";
//        };
//    }

    public MutableText toText() {
        return Text.literal(this.toString());
    }

    /**
     * Get the colour associated with this team.
     * @param dark whether to use the dark variant of the colour
     * @return the colour formatting for this team
     */
    public Formatting getColour(boolean dark) {
        return switch (this) {
            case TOWNSFOLK -> dark ? Formatting.DARK_BLUE : Formatting.BLUE;
            case OUTSIDER -> dark ? Formatting.DARK_AQUA : Formatting.AQUA;
            case MINION -> Formatting.RED;
            case DEMON -> Formatting.DARK_RED;
            case FABLED -> dark ? Formatting.GOLD : Formatting.YELLOW;
            case LORIC -> dark ? Formatting.DARK_GREEN : Formatting.GREEN;
            case TRAVELLER -> dark ? Formatting.DARK_PURPLE : Formatting.LIGHT_PURPLE;
        };
    }

    /**
     * Get the text representation of the team with colour formatting.
     * @param dark whether to use the dark variant of the colour
     * @return the team as coloured text
     */
    public Text toFormattedText(boolean dark) {
        return this.toText().formatted(this.getColour(dark));
    }

    /**
     * Get the team enum from a string.
     * @param teamStr the team string
     * @return the corresponding team enum
     */
    public static Team fromString(String teamStr) {
        return switch (teamStr.toLowerCase()) {
            case "townsfolk" -> TOWNSFOLK;
            case "outsider" -> OUTSIDER;
            case "minion" -> MINION;
            case "demon" -> DEMON;
            case "fabled" -> FABLED;
            case "loric" -> LORIC;
            case "traveller" -> TRAVELLER;
            default -> throw new IllegalArgumentException("Unknown team: " + teamStr);
        };
    }

    /**
     * Get the default alignment for this team.
     * Note that players can change alignment through gameplay.
     * @return the default alignment for this team
     */
    public Alignment getDefaultAlignment() {
        return switch (this) {
            case TOWNSFOLK, OUTSIDER -> Alignment.GOOD;
            case MINION, DEMON -> Alignment.EVIL;
            case FABLED, LORIC -> Alignment.NPC;
            case TRAVELLER -> Alignment.NEUTRAL;
        };
    }

    /** Alignment enum for the team. */
    public enum Alignment {
        GOOD,
        EVIL,
        NPC,
        NEUTRAL;

        public String toString() {
            return switch (this) {
                case GOOD -> "Good";
                case EVIL -> "Evil";
                case NPC -> "NPC";
                case NEUTRAL -> "Neutral";
            };
        }

        public MutableText toText() {
            return Text.literal(this.toString());
        }

        /**
         * Get the colour associated with this alignment.
         * Note that team colours should be used for most purposes; alignment colours are
         * primarily for special cases like swapped alignment indicators.
         * @param dark whether to use the dark variant of the colour
         * @return the colour formatting for this alignment
         */
        public Formatting getColour(boolean dark) {
            return switch (this) {
                case GOOD -> dark ? Formatting.DARK_BLUE : Formatting.BLUE;
                case EVIL -> dark ? Formatting.DARK_RED : Formatting.RED;
                case NPC -> dark ? Formatting.BLACK : Formatting.GRAY;
                case NEUTRAL -> dark ? Formatting.DARK_PURPLE : Formatting.LIGHT_PURPLE;
            };
        }

        public static Alignment fromString(String alignmentStr) {
            return switch (alignmentStr.toLowerCase()) {
                case "good" -> GOOD;
                case "evil" -> EVIL;
                case "npc" -> NPC;
                case "neutral" -> NEUTRAL;
                default -> throw new IllegalArgumentException("Unknown alignment: " + alignmentStr);
            };
        }
    }
}
