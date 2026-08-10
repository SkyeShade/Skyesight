package com.skyeshade.skyesight.client.world;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

public interface SkyesightVisualTerrainBackend extends AutoCloseable {
    void onChunkStatusAdded(int chunkX, int chunkZ);

    void onChunkStatusRemoved(int chunkX, int chunkZ);

    void scheduleTerrainUpdate();

    void scheduleBlockUpdate(BlockPos pos);

    void scheduleChunkRebuild(int chunkX, int chunkZ, boolean important);

    void renderTerrain(
            Camera camera,
            Frustum frustum,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            int chunkRadius,
            boolean renderTranslucent
    );

    int visibleChunkCount();

    @Override
    void close();
}
