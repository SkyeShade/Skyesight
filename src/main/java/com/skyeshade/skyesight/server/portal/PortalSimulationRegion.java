package com.skyeshade.skyesight.server.portal;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record PortalSimulationRegion(
        ResourceLocation viewId,
        UUID owner,
        ResourceKey<Level> ownerDimension,
        ResourceKey<Level> targetDimension,
        ChunkPos centerChunk,
        Vec3 virtualObserverPosition,
        int loadRadiusChunks,
        int entityTickRadiusChunks,
        int mobSpawnRadiusChunks,
        boolean sameDim,
        boolean crossDim,
        boolean farSameDim
) {
}
