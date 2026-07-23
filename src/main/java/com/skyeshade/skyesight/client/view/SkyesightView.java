package com.skyeshade.skyesight.client.view;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.api.*;

import com.skyeshade.skyesight.client.SkyesightClientThreading;
import com.skyeshade.skyesight.client.render.SkyesightCameraMatrices;
import com.skyeshade.skyesight.client.render.SkyesightFrustumFactory;
import com.skyeshade.skyesight.client.render.SkyesightProjectionMatrices;
import com.skyeshade.skyesight.client.render.SkyesightProjectionScope;
import com.skyeshade.skyesight.client.render.env.SkyesightEnvironmentRendererSelector;
import com.skyeshade.skyesight.client.render.fog.SkyesightFogRenderer;
import com.skyeshade.skyesight.client.render.light.SkyesightLightTextureUpdater;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.Objects;

public final class SkyesightView implements SkyesightViewHandle {
    private final ResourceLocation id;
    private final SkyesightInternalCamera camera;
    private final SkyesightRenderMode renderMode;

    private ResourceKey<Level> dimension;
    private int renderDistanceChunks;
    private int width;
    private int height;
    private float fov;
    private volatile TextureTarget target;
    private SkyesightClipPlane clipPlane;
    private volatile boolean closed;
    private Matrix4f projectionOverride;
    private final Object targetLock = new Object();

    public SkyesightView(SkyesightViewSpec spec) {
        Objects.requireNonNull(spec, "view spec");
        this.id = Objects.requireNonNull(spec.id(), "view id");

        this.dimension = Objects.requireNonNull(spec.dimension(), "view dimension");

        this.renderDistanceChunks = Math.max(1, spec.renderDistanceChunks());
        this.width = Math.max(1, spec.width());
        this.height = Math.max(1, spec.height());
        this.fov = Math.max(1.0F, spec.fov());
        this.renderMode = Objects.requireNonNull(spec.renderMode(), "view render mode");
        this.camera = new SkyesightInternalCamera();

        this.camera.setPosition(Objects.requireNonNull(spec.position(), "view position"));
        this.camera.setRotation(Objects.requireNonNull(spec.rotation(), "view rotation"));

        resize(this.width, this.height);
    }
    @Override
    public void setProjectionOverride(Matrix4f projectionMatrix) {
        this.projectionOverride = projectionMatrix == null ? null : new Matrix4f(projectionMatrix);
    }

    @Override
    public void clearProjectionOverride() {
        this.projectionOverride = null;
    }

    @Override
    public Matrix4f projectionOverride() {
        return this.projectionOverride == null ? null : new Matrix4f(this.projectionOverride);
    }
    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public SkyesightRenderMode renderMode() {
        return this.renderMode;
    }


    @Override
    public SkyesightViewCamera camera() {
        return this.camera;
    }
    @Override
    public void setClipPlane(SkyesightClipPlane clipPlane) {
        this.clipPlane = clipPlane;
    }

    @Override
    public void clearClipPlane() {
        this.clipPlane = null;
    }

    @Override
    public SkyesightClipPlane clipPlane() {
        return this.clipPlane;
    }
    @Override
    public void setDimension(ResourceKey<Level> dimension) {
        if (this.closed) {
            return;
        }

        if (this.dimension.equals(dimension)) {
            return;
        }

        SkyesightClientChunkRequester.reset(this.id);
        SkyesightVisualWorldManager.close(this.id);

        this.dimension = dimension;
    }

    @Override
    public void setRenderDistance(int renderDistanceChunks) {
        this.renderDistanceChunks = Math.max(1, renderDistanceChunks);
    }

    @Override
    public int renderDistanceChunks() {
        return this.renderDistanceChunks;
    }
    @Override
    public float fov() {
        return this.fov;
    }

    @Override
    public void setFov(float fov) {
        this.fov = Math.max(1.0F, fov);
    }
    @Override
    public void resize(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);

        synchronized (this.targetLock) {
            if (this.closed) {
                return;
            }
            if (this.target != null && this.width == width && this.height == height) {
                return;
            }
            this.width = width;
            this.height = height;
        }

        int targetWidth = width;
        int targetHeight = height;
        SkyesightClientThreading.runOnRenderThread(() -> resizeTargetOnRenderThread(targetWidth, targetHeight));
    }

    private void resizeTargetOnRenderThread(int width, int height) {
        synchronized (this.targetLock) {
            if (this.closed || this.width != width || this.height != height) {
                return;
            }
            if (this.target != null && this.target.width == width && this.target.height == height) {
                return;
            }
            if (this.target != null) {
                this.target.destroyBuffers();
            }
            this.target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        }
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public TextureTarget outputTarget() {
        return this.target;
    }

    @Override
    public int colorTextureId() {
        if (this.target == null) {
            return -1;
        }

        return this.target.getColorTextureId();
    }

    @Override
    public void renderNow(float partialTick) {
        if (this.closed) {
            return;
        }

        Matrix4f modelMatrix = SkyesightCameraMatrices.createModelView(this.camera.minecraftCamera());

        float aspect = (float) this.width / (float) this.height;

        Matrix4f cullingProjectionMatrix;

        if (this.projectionOverride != null) {
            cullingProjectionMatrix = new Matrix4f(this.projectionOverride);
        } else {
            cullingProjectionMatrix = SkyesightProjectionMatrices.perspective(
                    this.fov,
                    aspect,
                    0.05F,
                    Math.max(512.0F, this.renderDistanceChunks * 16.0F)
            );
        }
        Matrix4f terrainCullingProjectionMatrix = new Matrix4f(cullingProjectionMatrix);
        Matrix4f renderProjectionMatrix = new Matrix4f(cullingProjectionMatrix);

        if (this.clipPlane != null) {
            renderProjectionMatrix = SkyesightProjectionMatrices.applyObliqueClipPlane(
                    renderProjectionMatrix,
                    this.camera.minecraftCamera(),
                    this.clipPlane
            );
        }

        try (SkyesightProjectionScope projectionScope = new SkyesightProjectionScope(renderProjectionMatrix)) {
            renderInternal(
                    partialTick,
                    modelMatrix,
                    cullingProjectionMatrix,
                    terrainCullingProjectionMatrix,
                    renderProjectionMatrix
            );
        }
    }
    @Override
    public SkyesightViewOutput output() {
        return this.output;
    }
    private final SkyesightViewOutput output = new SkyesightViewOutput() {
        @Override
        public int width() {
            return SkyesightView.this.width;
        }

        @Override
        public int height() {
            return SkyesightView.this.height;
        }

        @Override
        public int colorTextureId() {
            if (SkyesightView.this.target == null) {
                return -1;
            }

            return SkyesightView.this.target.getColorTextureId();
        }

        @Override
        public int depthTextureId() {
            if (SkyesightView.this.target == null) {
                return -1;
            }

            return SkyesightView.this.target.getDepthTextureId();
        }

        @Override
        public RenderTarget renderTarget() {
            return SkyesightView.this.target;
        }
    };
    private void renderInternal(
            float partialTick,
            Matrix4f modelMatrix,
            Matrix4f cullingProjectionMatrix,
            Matrix4f terrainCullingProjectionMatrix,
            Matrix4f renderProjectionMatrix
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || this.target == null) {
            return;
        }

        SkyesightVisualWorld visualWorld =
                SkyesightVisualWorldManager.getOrCreate(this.id, this.dimension);

        if (visualWorld == null || visualWorld.isClosed()) {
            return;
        }

        SkyesightClientChunkRequester.requestChunksFor(
                this.id,
                this.dimension,
                this.camera.minecraftCamera(),
                this.renderDistanceChunks
        );

        visualWorld.level().setDayTime(minecraft.level.getDayTime());
        visualWorld.level().setGameTime(minecraft.level.getGameTime());

        Frustum frustum = SkyesightFrustumFactory.create(
                this.camera.minecraftCamera(),
                modelMatrix,
                terrainCullingProjectionMatrix
        );

        this.target.bindWrite(true);
        RenderSystem.viewport(0, 0, this.width, this.height);

        try {
            Vec3 skyColor = visualWorld.level().getSkyColor(
                    this.camera.minecraftCamera().getPosition(),
                    partialTick
            );

            RenderSystem.clearColor(
                    (float) skyColor.x(),
                    (float) skyColor.y(),
                    (float) skyColor.z(),
                    1.0F
            );

            RenderSystem.clear(
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                    Minecraft.ON_OSX
            );

            int fogDistanceChunks = this.renderDistanceChunks;

            SkyesightFogRenderer.setupForSky(
                    visualWorld.level(),
                    this.camera.minecraftCamera(),
                    partialTick,
                    fogDistanceChunks
            );

            SkyesightEnvironmentRendererSelector.get().renderSky(
                    visualWorld.level(),
                    this.camera.minecraftCamera(),
                    modelMatrix,
                    cullingProjectionMatrix,
                    partialTick
            );

            SkyesightLightTextureUpdater.updateFor(
                    visualWorld.level(),
                    this.camera.minecraftCamera(),
                    partialTick
            );

            SkyesightFogRenderer.setupForTerrain(
                    visualWorld.level(),
                    this.camera.minecraftCamera(),
                    partialTick,
                    fogDistanceChunks
            );

            RenderSystem.disableCull();

            visualWorld.renderTerrain(
                    this.camera.minecraftCamera(),
                    frustum,
                    modelMatrix,
                    renderProjectionMatrix,
                    this.renderDistanceChunks
            );

            RenderSystem.enableCull();

            minecraft.gameRenderer.lightTexture().turnOnLightLayer();

            try {
                visualWorld.renderBlockEntities(
                        this.camera.minecraftCamera(),
                        modelMatrix,
                        renderProjectionMatrix,
                        partialTick
                );

                visualWorld.renderEntities(
                        this.camera.minecraftCamera(),
                        modelMatrix,
                        partialTick
                );

                visualWorld.renderParticles(
                        this.camera.minecraftCamera(),
                        modelMatrix,
                        renderProjectionMatrix,
                        partialTick
                );
            } finally {
                minecraft.gameRenderer.lightTexture().turnOffLightLayer();
            }
        } finally {
            SkyesightFogRenderer.clear();
            SkyesightLightTextureUpdater.restoreMain(partialTick);

            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.viewport(
                    0,
                    0,
                    minecraft.getWindow().getWidth(),
                    minecraft.getWindow().getHeight()
            );

            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }


    }

    @Override
    public void close() {
        TextureTarget targetToDestroy;
        synchronized (this.targetLock) {
            if (this.closed) {
                return;
            }

            this.closed = true;
            targetToDestroy = this.target;
            this.target = null;
        }

        SkyesightClientChunkRequester.reset(this.id);
        SkyesightVisualWorldManager.close(this.id);

        if (targetToDestroy != null) {
            SkyesightClientThreading.runOnRenderThread(targetToDestroy::destroyBuffers);
        }
    }
    @Override
    public boolean isClosed() {
        return this.closed;
    }

}
