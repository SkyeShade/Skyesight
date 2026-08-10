package com.skyeshade.skyesight.client.render.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.world.SkyesightVisualClientLevel;
import com.skyeshade.skyesight.client.world.SkyesightVisualTerrainBackend;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class SkyesightVisualVanillaTerrain implements SkyesightVisualTerrainBackend {
    private static final int SECONDARY_RENDER_BUFFER_PACKS = 1;
    private static boolean failureLogged;
    private static long lastBootstrapSummaryMillis;

    private final Minecraft minecraft;
    private final SkyesightVisualClientLevel level;
    private final RenderBuffers renderBuffers;
    private final LevelRenderer levelRenderer;
    private ChunkPos lastBootstrapCenter;
    private int lastBootstrapRadius = -1;
    private boolean closed;

    private SkyesightVisualVanillaTerrain(SkyesightVisualClientLevel level) {
        this.minecraft = Minecraft.getInstance();
        this.level = level;
        this.renderBuffers = new RenderBuffers(SECONDARY_RENDER_BUFFER_PACKS);
        this.levelRenderer = new LevelRenderer(
                this.minecraft,
                this.minecraft.getEntityRenderDispatcher(),
                this.minecraft.getBlockEntityRenderDispatcher(),
                this.renderBuffers
        );
        this.levelRenderer.setLevel(level);
    }

    public static SkyesightVisualVanillaTerrain create(SkyesightVisualClientLevel level) {
        return new SkyesightVisualVanillaTerrain(level);
    }

    @Override
    public void onChunkStatusAdded(int chunkX, int chunkZ) {
        if (this.closed) {
            return;
        }

        this.levelRenderer.onChunkLoaded(new ChunkPos(chunkX, chunkZ));
        dirtyChunk(chunkX, chunkZ);
        this.levelRenderer.needsUpdate();
        clearBootstrapState();
    }

    @Override
    public void onChunkStatusRemoved(int chunkX, int chunkZ) {
        if (this.closed) {
            return;
        }

        dirtyChunk(chunkX, chunkZ);
        this.levelRenderer.needsUpdate();
        clearBootstrapState();
    }

    @Override
    public void scheduleTerrainUpdate() {
        if (this.closed) {
            return;
        }

        clearBootstrapState();
        this.levelRenderer.needsUpdate();
    }

    @Override
    public void scheduleBlockUpdate(BlockPos pos) {
        if (this.closed || pos == null) {
            return;
        }

        this.levelRenderer.setSectionDirtyWithNeighbors(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );
        this.levelRenderer.needsUpdate();
    }

    @Override
    public void scheduleChunkRebuild(int chunkX, int chunkZ, boolean important) {
        if (this.closed) {
            return;
        }

        dirtyChunk(chunkX, chunkZ);
        this.levelRenderer.needsUpdate();
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
        if (this.closed || camera == null || frustum == null || this.minecraft.player == null) {
            return;
        }

        try {
            LevelRendererSecondaryTerrainBridge bridge = (LevelRendererSecondaryTerrainBridge) this.levelRenderer;
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            bridge.skyesight$setupSecondaryTerrain(camera, frustum, this.minecraft.player.isSpectator());
            BootstrapResult bootstrap = bootstrapLoadedChunks(camera.getPosition(), chunkRadius);
            logBootstrapSummaryIfNeeded(bootstrap);
            bridge.skyesight$compileSecondarySections(camera);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();

            Vec3 cameraPosition = camera.getPosition();
            bridge.skyesight$renderSecondarySectionLayer(
                    RenderType.solid(),
                    cameraPosition.x(),
                    cameraPosition.y(),
                    cameraPosition.z(),
                    modelMatrix,
                    projectionMatrix
            );
            this.minecraft.getModelManager()
                    .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .setBlurMipmap(false, this.minecraft.options.mipmapLevels().get() > 0);
            try {
                bridge.skyesight$renderSecondarySectionLayer(
                        RenderType.cutoutMipped(),
                        cameraPosition.x(),
                        cameraPosition.y(),
                        cameraPosition.z(),
                        modelMatrix,
                        projectionMatrix
                );
            } finally {
                this.minecraft.getModelManager()
                        .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                        .restoreLastBlurMipmap();
            }
            bridge.skyesight$renderSecondarySectionLayer(
                    RenderType.cutout(),
                    cameraPosition.x(),
                    cameraPosition.y(),
                    cameraPosition.z(),
                    modelMatrix,
                    projectionMatrix
            );
        } catch (RuntimeException exception) {
            logFailureOnce(exception);
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public int visibleChunkCount() {
        return this.level.getChunkSource().getLoadedChunksCount();
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }

        this.closed = true;
        this.levelRenderer.setLevel(null);
        this.levelRenderer.close();
        clearBootstrapState();
    }

    private BootstrapResult bootstrapLoadedChunks(Vec3 cameraPosition, int radius) {
        int clampedRadius = Math.max(0, radius);
        ChunkPos center = new ChunkPos(
                ((int) Math.floor(cameraPosition.x())) >> 4,
                ((int) Math.floor(cameraPosition.z())) >> 4
        );
        if (center.equals(this.lastBootstrapCenter) && clampedRadius == this.lastBootstrapRadius) {
            return BootstrapResult.skipped();
        }

        int loadedChunksFound = 0;
        int chunksNotified = 0;
        int sectionsDirtied = 0;

        for (int dz = -clampedRadius; dz <= clampedRadius; dz++) {
            for (int dx = -clampedRadius; dx <= clampedRadius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }

                loadedChunksFound++;
                this.levelRenderer.onChunkLoaded(new ChunkPos(chunkX, chunkZ));
                chunksNotified++;
                sectionsDirtied += dirtyChunk(chunkX, chunkZ);
            }
        }

        this.levelRenderer.needsUpdate();
        this.lastBootstrapCenter = center;
        this.lastBootstrapRadius = clampedRadius;
        return new BootstrapResult(true, center, clampedRadius, loadedChunksFound, chunksNotified, sectionsDirtied);
    }

    private int dirtyChunk(int chunkX, int chunkZ) {
        int sectionsDirtied = 0;
        for (int sectionY = this.level.getMinSection(); sectionY < this.level.getMaxSection(); sectionY++) {
            this.levelRenderer.setSectionDirty(chunkX, sectionY, chunkZ);
            sectionsDirtied++;
        }
        return sectionsDirtied;
    }

    private void clearBootstrapState() {
        this.lastBootstrapCenter = null;
        this.lastBootstrapRadius = -1;
    }

    private static void logFailureOnce(RuntimeException exception) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        Skyesight.LOGGER.warn("[Skyesight] Vanilla visual-world terrain render failed", exception);
    }

    private static void logBootstrapSummaryIfNeeded(BootstrapResult bootstrap) {
        if (bootstrap == null || !bootstrap.ran() || !SkyesightDebugConfig.VERBOSE_RENDER) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBootstrapSummaryMillis < 1000L) {
            return;
        }
        lastBootstrapSummaryMillis = now;
        ChunkPos center = bootstrap.center();
        Skyesight.LOGGER.info(
                "[Skyesight] Vanilla visual terrain bootstrap center={},{} radius={} loadedChunks={} notifiedChunks={} dirtiedSections={}",
                center.x,
                center.z,
                bootstrap.radius(),
                bootstrap.loadedChunksFound(),
                bootstrap.chunksNotified(),
                bootstrap.sectionsDirtied()
        );
    }

    private record BootstrapResult(
            boolean ran,
            ChunkPos center,
            int radius,
            int loadedChunksFound,
            int chunksNotified,
            int sectionsDirtied
    ) {
        private static BootstrapResult skipped() {
            return new BootstrapResult(false, new ChunkPos(0, 0), 0, 0, 0, 0);
        }
    }
}
