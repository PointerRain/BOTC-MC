package golden.botc_mc.botc_mc.game.voice;

import net.minecraft.util.math.BlockPos;

/**
 * Axis-aligned cuboid region that maps player position to a voice chat group.
 *
 * @param id        unique region identifier (stable across saves)
 * @param groupName logical voice group name to join when inside
 * @param groupId   optional concrete voice chat UUID string (if already created)
 * @param cornerA   first corner of the region (inclusive)
 * @param cornerB   opposite corner of the region (inclusive)
 */
public record VoiceRegion(String id, String groupName, String groupId, BlockPos cornerA, BlockPos cornerB) {

    /** Internal helper returning normalized inclusive bounds as int[6]: minX,minY,minZ,maxX,maxY,maxZ. */
    private int[] normalizedBounds() {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        if (minY == maxY) { // expand flat regions vertically by one
            minY -= 1;
            maxY += 1;
        }
        return new int[]{minX,minY,minZ,maxX,maxY,maxZ};
    }

    /** Determine whether the block position lies inside this region.
     * Expands zero-height regions by one block up/down for inclusiveness.
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return true if inside inclusive bounds
     */
    public boolean containsBlock(int x, int y, int z) {
        int[] b = normalizedBounds();
        return x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5];
    }

    /** Debug-friendly bounds string for logging.
     * @return formatted bounds string like <code>[minX,minY,minZ]..[maxX,maxY,maxZ]</code>
     */
    public String boundsDebug() {
        int[] b = normalizedBounds();
        return "["+b[0]+","+b[1]+","+b[2]+"]..["+b[3]+","+b[4]+","+b[5]+"]";
    }
}