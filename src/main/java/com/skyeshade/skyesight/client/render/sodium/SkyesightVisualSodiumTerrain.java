package com.skyeshade.skyesight.client.render.sodium;

import com.skyeshade.skyesight.client.world.SkyesightVisualClientLevel;
import com.skyeshade.skyesight.client.world.SkyesightVisualTerrainBackend;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

public final class SkyesightVisualSodiumTerrain implements SkyesightVisualTerrainBackend {
    private final ChunkTracker chunkTracker;
    private final SkyesightSodiumWorldRenderer renderer;

    private SkyesightVisualSodiumTerrain(SkyesightVisualClientLevel level) {
        this.chunkTracker = new ChunkTracker();
        this.renderer = new SkyesightSodiumWorldRenderer(Minecraft.getInstance(), this.chunkTracker);
        this.renderer.setLevel(level);
    }

    public static Object create(SkyesightVisualClientLevel level) {
        return new SkyesightVisualSodiumTerrain(level);
    }

    @Override
    public void onChunkStatusAdded(int chunkX, int chunkZ) {
        this.chunkTracker.onChunkStatusAdded(chunkX, chunkZ, 3);
    }

    @Override
    public void onChunkStatusRemoved(int chunkX, int chunkZ) {
        this.chunkTracker.onChunkStatusRemoved(chunkX, chunkZ, 3);
    }

    @Override
    public void renderTerrain(
            Camera camera,
            Frustum frustum,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            int chunkRadius,
            boolean renderTranslucent
    ) {
        this.renderer.renderTerrain(
                camera,
                frustum,
                modelMatrix,
                projectionMatrix,
                renderTranslucent
        );
    }

    @Override
    public void scheduleTerrainUpdate() {
        this.renderer.scheduleTerrainUpdate();
    }

    @Override
    public void scheduleBlockUpdate(BlockPos pos) {
        this.renderer.scheduleBlockUpdate(pos);
    }

    @Override
    public void scheduleChunkRebuild(int chunkX, int chunkZ, boolean important) {
        this.renderer.scheduleChunkRebuild(chunkX, chunkZ, important);
    }

    @Override
    public int visibleChunkCount() {
        return this.renderer.renderer().getVisibleChunkCount();
    }

    @Override
    public void close() {
        this.renderer.close();
    }

}
