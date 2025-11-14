package golden.botc_mc.botc_mc.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import golden.botc_mc.botc_mc.game.state.BotcGameState;

/**
 * Describes how long each playable phase in the Blood on the Clocktower loop should last.
 * Durations are expressed in seconds inside configuration files, but exposed as ticks for runtime use.
 */
public record botcPhaseDurations(int dayDiscussionSecs,
                                 int nominationSecs,
                                 int executionSecs,
                                 int nightSecs) {
    private static final int DEFAULT_DAY = 120;
    private static final int DEFAULT_NOMINATION = 45;
    private static final int DEFAULT_EXECUTION = 20;
    private static final int DEFAULT_NIGHT = 60;

    public static final botcPhaseDurations DEFAULT = new botcPhaseDurations(
            DEFAULT_DAY,
            DEFAULT_NOMINATION,
            DEFAULT_EXECUTION,
            DEFAULT_NIGHT
    );

    public static final MapCodec<botcPhaseDurations> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("day_discussion_secs").orElse(DEFAULT.dayDiscussionSecs()).forGetter(botcPhaseDurations::dayDiscussionSecs),
            Codec.INT.fieldOf("nomination_secs").orElse(DEFAULT.nominationSecs()).forGetter(botcPhaseDurations::nominationSecs),
            Codec.INT.fieldOf("execution_secs").orElse(DEFAULT.executionSecs()).forGetter(botcPhaseDurations::executionSecs),
            Codec.INT.fieldOf("night_secs").orElse(DEFAULT.nightSecs()).forGetter(botcPhaseDurations::nightSecs)
    ).apply(instance, botcPhaseDurations::new));

    public static final Codec<botcPhaseDurations> CODEC = MAP_CODEC.codec();

    public static botcPhaseDurations defaults() {
        return DEFAULT;
    }

    /**
     * Converts the configured number of seconds into ticks (20 ticks = 1 second) for the given state.
     */
    public long durationTicks(BotcGameState state) {
        long seconds = switch (state) {
            case DAY_DISCUSSION -> this.dayDiscussionSecs;
            case NOMINATION -> this.nominationSecs;
            case EXECUTION -> this.executionSecs;
            case NIGHT -> this.nightSecs;
            default -> 5; // short placeholder (e.g., countdown/end) when no explicit duration is required
        };

        return Math.max(1L, seconds * 20L);
    }
}

