package golden.botc_mc.botc_mc.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public record botcMapConfig(BlockState spawnBlockState, int spawnX, int spawnY, int spawnZ) {
    public static final Codec<botcMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.optionalFieldOf("spawnBlockState", Blocks.STONE.getDefaultState()).forGetter(botcMapConfig::spawnBlockState),
            Codec.INT.fieldOf("spawnX").orElse(0).forGetter(botcMapConfig::spawnX),
            Codec.INT.fieldOf("spawnY").orElse(65).forGetter(botcMapConfig::spawnY),
            Codec.INT.fieldOf("spawnZ").orElse(0).forGetter(botcMapConfig::spawnZ)
    ).apply(instance, botcMapConfig::new));

    public BlockState spawnBlockState() { return this.spawnBlockState; }
    public int spawnY() { return this.spawnY; }
    public int spawnZ() { return this.spawnZ; }
}
