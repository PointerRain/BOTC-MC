package golden.botc_mc.botc_mc.game;

import net.minecraft.block.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import golden.botc_mc.botc_mc.botc;
import golden.botc_mc.botc_mc.game.map.botcMap;
import golden.botc_mc.botc_mc.game.map.botcMap.RespawnRegion;

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

        // Give the player 3 seconds (60 ticks) of invulnerability on spawn
        player.setInvulnerable(true);
        this.world.getServer().execute(() -> {
            // schedule disabling after 3 seconds of game time
            this.world.getServer().getOverworld().getServer().execute(() -> {
                player.setInvulnerable(false);
            });
        });
    }

    // Return a safe spawn location computed around X=0,Z=0 (highest surface near 0,0),
    // falling back to (0, ~, 0) if nothing is found.
    public Vec3d getSafeSpawnPosition() {
        RespawnRegion respawn = this.map.getRegions().spawn();
        BlockPos center = respawn.centerBlock();
        BlockPos best = findHighestSpawnable(center, 6);
        if (best != null) {
            return new Vec3d(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5);
        }
        // Fallback to just above the region center
        return new Vec3d(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        RespawnRegion respawn = this.map.getRegions().spawn();
        BlockPos center = respawn.centerBlock();

        BlockPos best = findHighestSpawnable(center, 6);
        if (best == null) {
            float radius = 2.0f;
            float x = center.getX() + MathHelper.nextFloat(player.getRandom(), -radius, radius);
            float z = center.getZ() + MathHelper.nextFloat(player.getRandom(), -radius, radius);
            player.teleport(this.world, x, center.getY(), z, Set.of(), respawn.yaw(), respawn.pitch(), true);
            return;
        }

        double x = best.getX() + 0.5;
        double y = best.getY() + 1.0;
        double z = best.getZ() + 0.5;
        player.teleport(this.world, x, y, z, Set.of(), respawn.yaw(), respawn.pitch(), true);
    }

    /**
     * Finds the highest spawnable block near a given center within the given radius.
     * "Spawnable" means: any block with a non-empty collision shape (covers glass & stained glass) and at least 2 air blocks above it.
     */
    private BlockPos findHighestSpawnable(BlockPos center, int horizontalRadius) {
        BlockPos best = null;

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                BlockPos columnPos = center.add(dx, 0, dz);

                // Use heightmap as a quick starting point for the top of this column
                int topY = this.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ());
                if (topY <= this.world.getBottomY()) continue;

                BlockPos.Mutable pos = new BlockPos.Mutable(columnPos.getX(), topY, columnPos.getZ());

                // Walk downward until we find a spawnable surface
                while (pos.getY() >= this.world.getBottomY()) {
                    BlockState state = this.world.getBlockState(pos);
                    if (isSpawnSurface(state)) {
                        BlockPos surface = pos.toImmutable();
                        if (canStandAbove(surface)) {
                            if (best == null || surface.getY() > best.getY()) {
                                best = surface;
                            }
                            break;
                        }
                    }
                    pos.move(0, -1, 0);
                }
            }
        }

        return best;
    }

    private boolean isSpawnSurface(BlockState state) {
        // Any block with a collision shape counts as a surface the player can stand on,
        // which includes glass and stained glass.
        return !state.getCollisionShape(this.world, BlockPos.ORIGIN).isEmpty();
    }

    private boolean canStandAbove(BlockPos surface) {
        BlockPos above = surface.up();
        BlockPos above2 = surface.up(2);
        return this.world.isAir(above) && this.world.isAir(above2);
    }
}
