package golden.botc_mc.botc_mc.game;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import golden.botc_mc.botc_mc.game.map.Map;
import golden.botc_mc.botc_mc.game.map.Map.RespawnRegion;

import java.util.Set;

public class SpawnLogic {
    private static final int SEARCH_RADIUS = 8;

    private final Map map;
    private final ServerWorld world;

    public SpawnLogic(ServerWorld world, Map map) {
        this.map = map;
        this.world = world;
    }

    /**
     * Resets some player state upon respawn.
     * Resets player gamemode, velocity, and negates fall damage.
     * @param player
     * @param gameMode
     */
    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.changeGameMode(gameMode);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0f;
    }

    // Return a safe spawn location near the map's respawn region center.
    public Vec3d getSafeSpawnPosition() {
        RespawnRegion respawn = this.map.getRegions().spawn();
        BlockPos center = respawn.centerBlock();
        BlockPos best = findHighestSpawnable(center);
        if (best != null) {
            return new Vec3d(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5);
        }
        // Fallback: use center one block above as last resort
        return new Vec3d(center.getX() + 0.5, Math.max(center.getY(), this.world.getBottomY()) + 1.0, center.getZ() + 0.5);
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        RespawnRegion respawn = this.map.getRegions().spawn();
        BlockPos center = respawn.centerBlock();

        BlockPos best = findHighestSpawnable(center);
        if (best == null) {
            float radius = 2.0f;
            float x = center.getX() + MathHelper.nextFloat(player.getRandom(), -radius, radius);
            float z = center.getZ() + MathHelper.nextFloat(player.getRandom(), -radius, radius);
            player.teleport(this.world, x, Math.max(center.getY(), this.world.getBottomY()) + 1.0, z, Set.of(), respawn.yaw(), respawn.pitch(), true);
            return;
        }

        double x = best.getX() + 0.5;
        double y = best.getY() + 1.0;
        double z = best.getZ() + 0.5;
        player.teleport(this.world, x, y, z, Set.of(), respawn.yaw(), respawn.pitch(), true);
    }

    /**
     * Finds the highest spawnable block near a given center within SEARCH_RADIUS.
     */
    private BlockPos findHighestSpawnable(BlockPos center) {
        BlockPos best = null;
        final int bottom = this.world.getBottomY();
        final int top = bottom + this.world.getDimension().height() - 1;
        final int startYCap = Math.min(top, center.getY() + 96);

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                BlockPos.Mutable pos = new BlockPos.Mutable(x, startYCap, z);
                while (pos.getY() >= bottom) {
                    BlockState state = this.world.getBlockState(pos);
                    if (!state.isAir() && isSpawnSurface(pos, state)) {
                        BlockPos surface = pos.toImmutable();
                        if (canStandAbove(surface)) {
                            if (best == null || surface.getY() > best.getY()) best = surface;
                            break;
                        }
                    }
                    pos.move(0, -1, 0);
                }
            }
        }
        return best;
    }

    /**
     * Determines if the given block state at the position can serve as a spawn surface.
     * Any block with a collision shape counts as a surface the player can stand on,
     * which includes glass and stained glass.
     */
    private boolean isSpawnSurface(BlockPos pos, BlockState state) {
        return !state.getCollisionShape(this.world, pos).isEmpty();
    }

    private boolean canStandAbove(BlockPos surface) {
        BlockPos above = surface.up();
        BlockPos above2 = surface.up(2);
        return this.world.isAir(above) && this.world.isAir(above2);
    }
}