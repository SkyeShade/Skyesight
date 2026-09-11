package com.skyeshade.skyesight.client.transition;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import java.util.Map;

/** Read-only collision fallback. Real received chunks always win; no chunk cache is modified. */
final class SecondaryCollisionView implements CollisionGetter {
    private final ClientLevel physical;
    private final Map<Long, LevelChunk> warmed;
    SecondaryCollisionView(ClientLevel physical, Map<Long, LevelChunk> warmed) { this.physical = physical; this.warmed = warmed; }
    static LevelChunk received(ClientLevel level, int x, int z) {
        // ClientLevel.hasChunk() always returns true, even before the first terrain packet.
        var chunk = level.getChunkSource().getChunk(x, z, false);
        return chunk instanceof net.minecraft.world.level.chunk.EmptyLevelChunk ? null : chunk;
    }
    @Override public BlockGetter getChunkForCollisions(int x, int z) {
        var chunk = received(physical, x, z);
        return chunk != null ? chunk : warmed.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        var chunk = getChunkForCollisions(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        var chunk = getChunkForCollisions(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }
    @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) { return physical.getEntityCollisions(entity, box); }
    @Override public WorldBorder getWorldBorder() { return physical.getWorldBorder(); }
    @Override public int getHeight() { return physical.getHeight(); }
    @Override public int getMinBuildHeight() { return physical.getMinBuildHeight(); }
}
