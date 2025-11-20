package golden.botc_mc.botc_mc.game.voice;

import net.minecraft.util.math.BlockPos;

/**
 * @param groupId Persisted voice chat group id (UUID string) assigned by SVC when created
 */
public record VoiceRegion(String id, String groupName, String groupId, BlockPos cornerA, BlockPos cornerB) {

    // New block-position based containment (stable against fractional movement)
    public boolean containsBlock(int x, int y, int z) {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        // If region has zero vertical height (minY == maxY) expand it by 1 block up/down so players standing on top are included.
        if (minY == maxY) {
            minY = minY - 1;
            maxY = maxY + 1;
        }
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    // Helpful for debug logging
    public String boundsDebug() {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        return "[" + minX + "," + minY + "," + minZ + "]..[" + maxX + "," + maxY + "," + maxZ + "]";
    }
}
