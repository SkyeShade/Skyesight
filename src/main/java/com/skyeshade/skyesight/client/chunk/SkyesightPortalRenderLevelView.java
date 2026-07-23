package com.skyeshade.skyesight.client.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public final class SkyesightPortalRenderLevelView implements SkyesightChunkView {
    private static final int FALLBACK_RAW_BRIGHTNESS = 15;

    private final ResourceKey<Level> dimension;
    private int missingChunkFallbackCount;

    public SkyesightPortalRenderLevelView(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public boolean hasChunk(ChunkPos pos) {
        return SkyesightPortalChunkStorage.hasChunk(this.dimension, pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return SkyesightPortalChunkStorage.getBlockState(this.dimension, pos)
                .orElseGet(() -> {
                    this.missingChunkFallbackCount++;
                    return Blocks.AIR.defaultBlockState();
                });
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    public int getMinBuildHeight() {
        return SkyesightPortalChunkStorage.minBuildHeight(this.dimension)
                .orElse(0);
    }

    public int getMaxBuildHeight() {
        return SkyesightPortalChunkStorage.maxBuildHeight(this.dimension)
                .orElse(256);
    }

    public int getSectionIndexFromSectionY(int sectionY) {
        return sectionY - (this.getMinBuildHeight() >> 4);
    }

    public boolean hasLightData(ChunkPos pos) {
        return SkyesightPortalChunkStorage.lightDataPresent(this.dimension, pos);
    }

    public int getRawBrightness(BlockPos pos, int skyDarken) {
        return Math.max(0, FALLBACK_RAW_BRIGHTNESS - Math.max(0, skyDarken));
    }

    public String firstNonAirSummary(int chunkX, int chunkZ) {
        return SkyesightPortalChunkStorage.firstNonAirSummary(this.dimension, chunkX, chunkZ);
    }

    public int missingChunkFallbackCount() {
        return this.missingChunkFallbackCount;
    }
}
