package golden.botc_mc.botc_mc.game.map;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility that generates simple podiums arranged in a circle.
 * <p>
 * This class provides a single static helper used by the '/botc podium' command.
 * It places single-block podiums (stone by default) on a circle centered at the
 * requested coordinates. The method is synchronous and intended to be called on
 * the server thread (command execution context).
 */
public final class PodiumGenerator {
    private PodiumGenerator() {}

    // Recorded last generation so only one set exists at a time.
    private static ServerWorld recordedWorld = null;
    private static List<BlockPos> recordedPositions = new ArrayList<>();

    /**
     * Generate `count` single-block podiums evenly spaced around a circle.
     * Returns the list of block positions that were set (useful for logging).
     *
     * @param world     server world where blocks should be placed
     * @param centerX   center X coordinate of the circle
     * @param centerY   center Y coordinate (height) where podiums are placed
     * @param centerZ   center Z coordinate of the circle
     * @param diameter  diameter of the circle in blocks (e.g. 24)
     * @param count     number of podiums to generate (if <=0 nothing is done)
     * @return list of BlockPos that were written to
     */
    public static List<BlockPos> generateCircle(ServerWorld world, int centerX, int centerY, int centerZ, double diameter, int count) {
        List<BlockPos> placed = new ArrayList<>();
        if (world == null || count <= 0) return placed;

        // clamp Y to world height range to avoid invalid positions
        int minY = world.getBottomY();
        int maxY = minY + world.getDimension().height() - 1; // inclusive top
        int y = Math.max(minY, Math.min(maxY, centerY));

        double radius = diameter / 2.0;

        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * ((double) i / (double) count);
            double fx = centerX + Math.cos(angle) * radius;
            double fz = centerZ + Math.sin(angle) * radius;

            int bx = (int) Math.round(fx);
            int bz = (int) Math.round(fz);

            BlockPos pos = new BlockPos(bx, y, bz);
            // Place a single stone block as a podium. Use flag 3 to notify neighbors and clients.
            try {
                world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3);
                placed.add(pos);
            } catch (Throwable t) {
                // ignore and continue; return list will not contain this pos
            }
        }

        return placed;
    }

    /**
     * Remove (set to air) stone blocks arranged in the same circle. Only removes
     * blocks that are exactly stone (Blocks.STONE) to reduce risk of removing
     * unrelated blocks. Returns the list of positions that were cleared.
     *
     * @param world server world
     * @param centerX center X
     * @param centerY center Y
     * @param centerZ center Z
     * @param diameter diameter in blocks
     * @param count number of positions to evaluate
     * @return list of BlockPos that were set to air
     */
    public static List<BlockPos> removeCircle(ServerWorld world, int centerX, int centerY, int centerZ, double diameter, int count) {
        List<BlockPos> removed = new ArrayList<>();
        if (world == null || count <= 0) return removed;

        int minY = world.getBottomY();
        int maxY = minY + world.getDimension().height() - 1;
        int y = Math.max(minY, Math.min(maxY, centerY));

        double radius = diameter / 2.0;

        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * ((double) i / (double) count);
            double fx = centerX + Math.cos(angle) * radius;
            double fz = centerZ + Math.sin(angle) * radius;

            int bx = (int) Math.round(fx);
            int bz = (int) Math.round(fz);

            BlockPos pos = new BlockPos(bx, y, bz);
            try {
                if (world.getBlockState(pos).getBlock() == Blocks.STONE) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    removed.add(pos);
                }
            } catch (Throwable t) {
                // ignore and continue
            }
        }

        return removed;
    }

    /**
     * Create podiums and record them as the single active set. If another set
     * already exists it will be removed first.
     * @return list of positions placed
     */
    public static synchronized List<BlockPos> createAndRecord(ServerWorld world, int centerX, int centerY, int centerZ, double diameter, int count) {
        // remove any previously recorded set
        removeRecorded();

        List<BlockPos> placed = generateCircle(world, centerX, centerY, centerZ, diameter, count);
        if (placed.isEmpty()) {
            // nothing placed -> don't record
            return Collections.emptyList();
        }

        recordedWorld = world;
        recordedPositions = new ArrayList<>(placed);
        return new ArrayList<>(recordedPositions);
    }

    /**
     * Remove the last-recorded podium set (if any). Returns positions cleared.
     */
    public static synchronized List<BlockPos> removeRecorded() {
        List<BlockPos> removed = new ArrayList<>();
        if (recordedWorld == null || recordedPositions == null || recordedPositions.isEmpty()) {
            recordedWorld = null;
            recordedPositions = new ArrayList<>();
            return removed;
        }

        ServerWorld w = recordedWorld;
        for (BlockPos pos : recordedPositions) {
            try {
                if (w.getBlockState(pos).getBlock() == Blocks.STONE) {
                    w.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    removed.add(pos);
                }
            } catch (Throwable t) {
                // ignore
            }
        }

        // clear recorded state
        recordedWorld = null;
        recordedPositions = new ArrayList<>();
        return removed;
    }

    /**
     * @return immutable snapshot of last recorded positions (may be empty)
     */
    public static synchronized List<BlockPos> getRecordedPositions() {
        return Collections.unmodifiableList(new ArrayList<>(recordedPositions));
    }
}
