package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.client.world.SecondaryParticleCapture;
import com.skyeshade.skyesight.client.world.SkyesightVisualParticleManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Vanilla's two triangular sample distributions, centered on an active secondary eye. */
public final class PortalVisualDisplayTickDriver {
    private PortalVisualDisplayTickDriver() {}
    public static Result tick(ResourceLocation id, String kind, ClientLevel level,
                              SkyesightVisualParticleManager particles, Vec3 center) {
        if (level == null || particles == null) return new Result(0);
        var random = RandomSource.create(id.hashCode() * 31L + level.getGameTime());
        var pos = new BlockPos.MutableBlockPos();
        int sampled = 0;
        try (var capture = SecondaryParticleCapture.push(level, particles)) {
            for (int i = 0; i < 667; i++) {
                sampled += sample(level, center, 16, random, pos);
                sampled += sample(level, center, 32, random, pos);
            }
        }
        return new Result(sampled);
    }
    private static int sample(ClientLevel level, Vec3 center, int range, RandomSource random, BlockPos.MutableBlockPos pos) {
        pos.set(net.minecraft.util.Mth.floor(center.x) + random.nextInt(range) - random.nextInt(range),
                net.minecraft.util.Mth.floor(center.y) + random.nextInt(range) - random.nextInt(range),
                net.minecraft.util.Mth.floor(center.z) + random.nextInt(range) - random.nextInt(range));
        if (level.isOutsideBuildHeight(pos) || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return 0;
        var state = level.getBlockState(pos);
        state.getBlock().animateTick(state, level, pos, random);
        var fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            fluid.animateTick(level, pos, random);
            var drip = fluid.getDripParticle();
            if (drip != null && random.nextInt(10) == 0) {
                var below = pos.below();
                ((com.skyeshade.skyesight.mixin.client.ClientLevelDisplayTickInvoker) level).skyesight$trySpawnDripParticles(
                        below, level.getBlockState(below), drip, state.isFaceSturdy(level, pos, Direction.DOWN));
            }
        }
        if (!state.isCollisionShapeFullBlock(level, pos)) level.getBiome(pos).value().getAmbientParticle().ifPresent(ambient -> {
            if (ambient.canSpawn(random)) level.addParticle(ambient.getOptions(),
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0, 0);
        });
        return 1;
    }
    public record Result(int positionsSampled) {}
}