package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class SecondaryViewFrame {
    private final Camera camera;
    private final TextureTarget colorTarget;
    private final int viewportWidth;
    private final int viewportHeight;
    private final Matrix4f projectionMatrix;
    private final Matrix4f modelViewMatrix;
    private final Matrix4f cullProjectionMatrix;
    private final Frustum frustum;
    private final Diagnostics diagnostics = new Diagnostics();

    public SecondaryViewFrame(
            Camera camera,
            TextureTarget colorTarget,
            int viewportWidth,
            int viewportHeight,
            Matrix4f projectionMatrix,
            Matrix4f modelViewMatrix,
            Matrix4f cullProjectionMatrix,
            Frustum frustum
    ) {
        this.camera = camera;
        this.colorTarget = colorTarget;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.projectionMatrix = new Matrix4f(projectionMatrix);
        this.modelViewMatrix = new Matrix4f(modelViewMatrix);
        this.cullProjectionMatrix = new Matrix4f(cullProjectionMatrix);
        this.frustum = frustum;
    }

    public Camera camera() {
        return this.camera;
    }

    public TextureTarget colorTarget() {
        return this.colorTarget;
    }

    public int viewportWidth() {
        return this.viewportWidth;
    }

    public int viewportHeight() {
        return this.viewportHeight;
    }

    public Matrix4f projectionMatrix() {
        return new Matrix4f(this.projectionMatrix);
    }

    public Matrix4f modelViewMatrix() {
        return new Matrix4f(this.modelViewMatrix);
    }

    public Matrix4f cullProjectionMatrix() {
        return new Matrix4f(this.cullProjectionMatrix);
    }

    public Frustum frustum() {
        return this.frustum;
    }

    public Diagnostics diagnostics() {
        return this.diagnostics;
    }

    public static final class Diagnostics {
        private String backend = "n/a";
        private boolean publishEntityWatchRegion = true;
        private boolean runEntityPass;
        private ResourceLocation entityWatchRegionId;
        private String projectionMode = "NORMAL_PERSPECTIVE";
        private float projectionNearPlane;
        private float projectionFarPlane;
        private String projectionSummary = "n/a";
        private String cullProjectionSummary = "n/a";
        private String cullProjectionMode = "normal";
        private String portalInstanceId = "n/a";
        private int portalStencilRef;
        private boolean renderToCurrentTarget;
        private boolean renderSkyInCurrentTarget;
        private int terrainChunkRadius;
        private int portalOwnedRenderRadiusChunks;
        private int sameDimPlayerLoadedReuseRadiusChunks;
        private boolean reusePlayerLoadedChunksForSameDim;
        private int entityChunkRadius;
        private int blockEntityChunkRadius;
        private int blockUpdateChunkRadius;
        private boolean renderSky = true;
        private boolean renderTerrain = true;
        private boolean renderTranslucent = true;
        private boolean renderEntities = true;
        private boolean renderBlockEntities = true;
        private boolean renderParticles = true;
        private boolean renderBackface;
        private String viewPhysicalSide = "unknown";

        public String backend() {
            return this.backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public boolean publishEntityWatchRegion() {
            return this.publishEntityWatchRegion;
        }

        public void setPublishEntityWatchRegion(boolean publishEntityWatchRegion) {
            this.publishEntityWatchRegion = publishEntityWatchRegion;
        }

        public boolean runEntityPass() {
            return this.runEntityPass;
        }

        public void setRunEntityPass(boolean runEntityPass) {
            this.runEntityPass = runEntityPass;
        }

        public ResourceLocation entityWatchRegionId() {
            return this.entityWatchRegionId;
        }

        public void setEntityWatchRegionId(ResourceLocation entityWatchRegionId) {
            this.entityWatchRegionId = entityWatchRegionId;
        }

        public String projectionMode() {
            return this.projectionMode;
        }

        public void setProjectionMode(String projectionMode) {
            this.projectionMode = projectionMode;
        }

        public float projectionNearPlane() {
            return this.projectionNearPlane;
        }

        public void setProjectionNearPlane(float projectionNearPlane) {
            this.projectionNearPlane = projectionNearPlane;
        }

        public float projectionFarPlane() {
            return this.projectionFarPlane;
        }

        public void setProjectionFarPlane(float projectionFarPlane) {
            this.projectionFarPlane = projectionFarPlane;
        }

        public String projectionSummary() {
            return this.projectionSummary;
        }

        public void setProjectionSummary(String projectionSummary) {
            this.projectionSummary = projectionSummary;
        }

        public String cullProjectionSummary() {
            return this.cullProjectionSummary;
        }

        public void setCullProjectionSummary(String cullProjectionSummary) {
            this.cullProjectionSummary = cullProjectionSummary;
        }

        public String cullProjectionMode() {
            return this.cullProjectionMode;
        }

        public void setCullProjectionMode(String cullProjectionMode) {
            this.cullProjectionMode = cullProjectionMode;
        }

        public String portalInstanceId() {
            return this.portalInstanceId;
        }

        public void setPortalInstanceId(String portalInstanceId) {
            this.portalInstanceId = portalInstanceId;
        }

        public int portalStencilRef() {
            return this.portalStencilRef;
        }

        public void setPortalStencilRef(int portalStencilRef) {
            this.portalStencilRef = portalStencilRef;
        }

        public boolean renderToCurrentTarget() {
            return this.renderToCurrentTarget;
        }

        public void setRenderToCurrentTarget(boolean renderToCurrentTarget) {
            this.renderToCurrentTarget = renderToCurrentTarget;
        }

        public boolean renderSkyInCurrentTarget() {
            return this.renderSkyInCurrentTarget;
        }

        public void setRenderSkyInCurrentTarget(boolean renderSkyInCurrentTarget) {
            this.renderSkyInCurrentTarget = renderSkyInCurrentTarget;
        }

        public int terrainChunkRadius() {
            return this.terrainChunkRadius;
        }

        public void setTerrainChunkRadius(int terrainChunkRadius) {
            this.terrainChunkRadius = terrainChunkRadius;
        }

        public int portalOwnedRenderRadiusChunks() {
            return this.portalOwnedRenderRadiusChunks;
        }

        public void setPortalOwnedRenderRadiusChunks(int portalOwnedRenderRadiusChunks) {
            this.portalOwnedRenderRadiusChunks = portalOwnedRenderRadiusChunks;
        }

        public int sameDimPlayerLoadedReuseRadiusChunks() {
            return this.sameDimPlayerLoadedReuseRadiusChunks;
        }

        public void setSameDimPlayerLoadedReuseRadiusChunks(int sameDimPlayerLoadedReuseRadiusChunks) {
            this.sameDimPlayerLoadedReuseRadiusChunks = sameDimPlayerLoadedReuseRadiusChunks;
        }

        public boolean reusePlayerLoadedChunksForSameDim() {
            return this.reusePlayerLoadedChunksForSameDim;
        }

        public void setReusePlayerLoadedChunksForSameDim(boolean reusePlayerLoadedChunksForSameDim) {
            this.reusePlayerLoadedChunksForSameDim = reusePlayerLoadedChunksForSameDim;
        }

        public int entityChunkRadius() {
            return this.entityChunkRadius;
        }

        public void setEntityChunkRadius(int entityChunkRadius) {
            this.entityChunkRadius = entityChunkRadius;
        }

        public int blockEntityChunkRadius() {
            return this.blockEntityChunkRadius;
        }

        public void setBlockEntityChunkRadius(int blockEntityChunkRadius) {
            this.blockEntityChunkRadius = blockEntityChunkRadius;
        }

        public int blockUpdateChunkRadius() {
            return this.blockUpdateChunkRadius;
        }

        public void setBlockUpdateChunkRadius(int blockUpdateChunkRadius) {
            this.blockUpdateChunkRadius = blockUpdateChunkRadius;
        }

        public boolean renderSky() {
            return this.renderSky;
        }

        public void setRenderSky(boolean renderSky) {
            this.renderSky = renderSky;
        }

        public boolean renderTerrain() {
            return this.renderTerrain;
        }

        public void setRenderTerrain(boolean renderTerrain) {
            this.renderTerrain = renderTerrain;
        }

        public boolean renderTranslucent() {
            return this.renderTranslucent;
        }

        public void setRenderTranslucent(boolean renderTranslucent) {
            this.renderTranslucent = renderTranslucent;
        }

        public boolean renderEntities() {
            return this.renderEntities;
        }

        public void setRenderEntities(boolean renderEntities) {
            this.renderEntities = renderEntities;
        }

        public boolean renderBlockEntities() {
            return this.renderBlockEntities;
        }

        public void setRenderBlockEntities(boolean renderBlockEntities) {
            this.renderBlockEntities = renderBlockEntities;
        }

        public boolean renderParticles() {
            return this.renderParticles;
        }

        public void setRenderParticles(boolean renderParticles) {
            this.renderParticles = renderParticles;
        }

        public boolean renderBackface() {
            return this.renderBackface;
        }

        public void setRenderBackface(boolean renderBackface) {
            this.renderBackface = renderBackface;
        }

        public String viewPhysicalSide() {
            return this.viewPhysicalSide;
        }

        public void setViewPhysicalSide(String viewPhysicalSide) {
            this.viewPhysicalSide = viewPhysicalSide == null || viewPhysicalSide.isBlank()
                    ? "unknown"
                    : viewPhysicalSide;
        }
    }
}
