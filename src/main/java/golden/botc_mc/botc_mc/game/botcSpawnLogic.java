package golden.botc_mc.botc_mc.game;

import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.map.botcMap;
import net.minecraft.block.Block;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.Set;

public class botcSpawnLogic {
    private final GameSpace gameSpace;
    private final botcMap map;
    private final ServerWorld world;

    public botcSpawnLogic(GameSpace gameSpace, ServerWorld world, botcMap map) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.world = world;
    }

    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.changeGameMode(gameMode);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0f;

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                20 * 60 * 60,
                1,
                true,
                false
        ));
    }

    private boolean isGlassBlock(Block block) {
        // Some mappings don't expose a BlockTags.GLASS constant; use explicit checks and a
        // translation-key fallback to be tolerant across versions.
        try {
            if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) return true;
        } catch (Throwable ignored) {
            // ignore missing fields in some mappings
        }

        if (block instanceof StainedGlassBlock) return true;

        try {
            String key = block.getTranslationKey();
            if (key != null && key.toLowerCase().contains("glass")) return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        BlockPos pos = this.map.spawn;
        if (pos == null) {
            botc.LOGGER.error("Cannot spawn player! No spawn is defined in the map!");
            return;
        }

        float radius = 4.5f;
        float x = pos.getX() + MathHelper.nextFloat(player.getRandom(), -radius, radius);
        float z = pos.getZ() + MathHelper.nextFloat(player.getRandom(), -radius, radius);

        int xi = MathHelper.floor(x);
        int zi = MathHelper.floor(z);
        double finalY = -1;

        // Ensure chunk is loaded and fully generated
        this.world.getChunk(xi >> 4, zi >> 4, ChunkStatus.FULL, true);

        // Get exclusive top Y and compute max block Y
        int topExclusive = this.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, xi, zi);
        int maxBlockY = topExclusive - 1;
        int bottomY = this.world.getBottomY();
        int searchTop = Math.min(bottomY + 320, maxBlockY);
        if (searchTop < bottomY) searchTop = bottomY;

        boolean found = false;
        for (int y = searchTop; y >= bottomY; y--) {
            BlockPos bp = new BlockPos(xi, y, zi);
            var state = this.world.getBlockState(bp);
            Block block = state.getBlock();
            if ((!state.isAir() && state.isSolidBlock(this.world, bp)) || isGlassBlock(block)) {
                if (!(block instanceof LeavesBlock) &&
                    !(block instanceof SlabBlock) &&
                    state.getFluidState().isEmpty()) {
                    finalY = y + 1.0; // stand on top of this block
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            // Use sea level or 64 as a safe fallback
            finalY = Math.max(this.world.getSeaLevel(), 64);
            botc.LOGGER.warn("No safe block found at {},{}; using fallback Y {}", xi, zi, finalY);
        }

        // center the player within the block horizontally for nicer placement
        double tx = x + 0.5;
        double tz = z + 0.5;

        // clamp finalY inside world build limits. Player Y can be at most topExclusive (air top) and at least bottomY+1
        double clampedY = Math.max(finalY, this.world.getBottomY() + 1);
        clampedY = Math.min(clampedY, (double)topExclusive);

        final double finalTx = tx;
        final double finalTz = tz;
        final double finalClampedY = clampedY;

        botc.LOGGER.info("Spawning player {} at X: {}, Y: {}, Z: {} (raw: X: {}, Y: {}, Z: {})", player.getName().getString(), finalTx, finalClampedY, finalTz, x, finalY, z);
        BlockPos debugPos = new BlockPos((int)finalTx, (int)(finalClampedY - 1), (int)finalTz);
        var debugState = this.world.getBlockState(debugPos);
        botc.LOGGER.info("Block at spawn location: {} (isAir: {})", debugState.getBlock().getTranslationKey(), debugState.isAir());

        // Schedule teleport to ensure chunk and spawn are ready
        this.world.getServer().execute(() ->
            player.teleport(this.world, finalTx, finalClampedY, finalTz, Set.of(), 0.0F, 0.0F, true)
        );
    }
}
