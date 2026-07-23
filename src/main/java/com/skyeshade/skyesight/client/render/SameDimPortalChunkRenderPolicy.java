package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.api.RegisteredPortalView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class SameDimPortalChunkRenderPolicy {
    private SameDimPortalChunkRenderPolicy() {}

    public static boolean shouldRenderChunkForSameDimPortal(
            ClientLevel level,
            RegisteredPortalView view,
            ChunkPos chunk,
            Vec3 portalCameraPos
    ) {
        if (view == null || view.renderSettings() == null) {
            return false;
        }

        return shouldRenderChunkForSameDimPortal(
                level,
                chunk,
                portalCameraPos,
                view.renderSettings().portalOwnedRenderRadiusChunks(),
                view.renderSettings().sameDimPlayerLoadedReuseRadiusChunks(),
                view.renderSettings().reusePlayerLoadedChunksForSameDim()
        );
    }

    public static boolean shouldRenderChunkForSameDimPortal(
            ClientLevel level,
            ChunkPos chunk,
            Vec3 portalCameraPos,
            int portalOwnedRenderRadiusChunks,
            int sameDimPlayerLoadedReuseRadiusChunks,
            boolean reusePlayerLoadedChunksForSameDim
    ) {
        if (level == null || chunk == null || portalCameraPos == null || !isLoaded(level, chunk)) {
            return false;
        }

        ChunkPos cameraChunk = new ChunkPos(net.minecraft.core.BlockPos.containing(portalCameraPos));
        int distance = Math.max(Math.abs(chunk.x - cameraChunk.x), Math.abs(chunk.z - cameraChunk.z));
        int ownedRadius = Math.max(0, portalOwnedRenderRadiusChunks);
        if (distance <= ownedRadius) {
            return true;
        }

        return reusePlayerLoadedChunksForSameDim
                && distance <= Math.max(0, sameDimPlayerLoadedReuseRadiusChunks);
    }

    public static boolean isLoaded(ClientLevel level, ChunkPos chunk) {
        return level != null
                && chunk != null
                && level.getChunkSource().getChunk(chunk.x, chunk.z, false) != null;
    }
}
