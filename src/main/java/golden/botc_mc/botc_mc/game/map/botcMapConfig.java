package golden.botc_mc.botc_mc.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public record botcMapConfig(BlockState spawnBlockState) {
    public static final Codec<botcMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("spawnBlock").orElse(Blocks.STONE.getDefaultState()).forGetter(botcMapConfig::spawnBlockState)
    ).apply(instance, botcMapConfig::new));

    public BlockState spawnBlockState() { return this.spawnBlockState; }
}

