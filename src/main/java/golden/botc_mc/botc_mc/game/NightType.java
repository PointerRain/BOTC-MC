package golden.botc_mc.botc_mc.game;

public enum NightType {
    FIRST, OTHER;

    @Override
    public String toString() {
        return switch (this) {
            case FIRST -> "first";
            case OTHER -> "other";
        };
    }
}
