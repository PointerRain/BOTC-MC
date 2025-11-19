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
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        RespawnRegion respawn = this.map.getRegions().spawn();
        BlockPos center = respawn.centerBlock();

        float radius = 2.0f;
        float x = center.getX() + MathHelper.nextFloat(player.getRandom(), -radius, radius);
        float z = center.getZ() + MathHelper.nextFloat(player.getRandom(), -radius, radius);

        player.teleport(this.world, x, center.getY(), z, Set.of(), respawn.yaw(), respawn.pitch(), true);
    }
}
