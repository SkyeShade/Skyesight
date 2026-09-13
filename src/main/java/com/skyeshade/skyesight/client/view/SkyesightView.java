package com.skyeshade.skyesight.client.view;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.api.SkyesightClipPlane;
import com.skyeshade.skyesight.api.SkyesightRenderMode;
import com.skyeshade.skyesight.api.SkyesightViewCamera;
import com.skyeshade.skyesight.api.SkyesightViewHandle;
import com.skyeshade.skyesight.api.SkyesightViewOutput;
import com.skyeshade.skyesight.api.SkyesightViewRenderOptions;
import com.skyeshade.skyesight.api.SkyesightViewSpec;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalChunkStorage;
import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;
import com.skyeshade.skyesight.client.render.SecondarySceneEnvironmentRenderer;
import com.skyeshade.skyesight.client.render.SecondarySceneFrame;
import com.skyeshade.skyesight.client.render.SecondarySceneOutputState;
import com.skyeshade.skyesight.client.render.SecondarySceneRenderer;
import com.skyeshade.skyesight.client.render.SecondaryViewContext;
import com.skyeshade.skyesight.client.render.SecondaryViewFrame;
import com.skyeshade.skyesight.client.render.SkyesightCameraMatrices;
import com.skyeshade.skyesight.client.render.SkyesightCameraOutput;
import com.skyeshade.skyesight.client.render.SkyesightFrustumFactory;
import com.skyeshade.skyesight.client.render.SkyesightProjectionMatrices;
import com.skyeshade.skyesight.client.render.SkyesightProjectionScope;
import com.skyeshade.skyesight.client.render.state.PortalSecondaryRenderState;
import com.skyeshade.skyesight.client.SkyesightClientThreading;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.network.SkyesightClientChunkHandler;
import com.skyeshade.skyesight.network.SkyesightRemoteViewLifecyclePayload;
import com.skyeshade.skyesight.remote.SkyesightRemoteCenterDiagnostics;
import com.skyeshade.skyesight.remote.SkyesightRemoteRadiusPolicy;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

public final class SkyesightView implements SkyesightViewHandle {
    private static final AtomicLong NEXT_CAMERA_GENERATION = new AtomicLong(1L);
    private static final long REMOTE_DIAGNOSTIC_INTERVAL_MILLIS = 2_000L;
    private final ResourceLocation id;
    private final SkyesightInternalCamera camera;
    private final SkyesightRenderMode renderMode;
    private final SecondaryViewContext secondaryViewContext = new SecondaryViewContext();


    private ResourceKey<Level> dimension;
    private int renderDistanceChunks;
    private int remoteLoadRadiusLimit = Integer.MAX_VALUE;
    private int width;
    private int height;
    private float fov;
    private SkyesightViewRenderOptions renderOptions;
    private volatile TextureTarget target;
    private SkyesightClipPlane clipPlane;
    private volatile boolean closed;
    private Matrix4f projectionOverride;
    private final Object targetLock = new Object();
    private long remoteGeneration;
    private ResourceKey<Level> remoteDimension;
    private long lastRemoteDiagnosticMillis;

    public SkyesightView(SkyesightViewSpec spec) {
        Objects.requireNonNull(spec, "view spec");
        this.id = Objects.requireNonNull(spec.id(), "view id");

        this.dimension = Objects.requireNonNull(spec.dimension(), "view dimension");

        this.renderDistanceChunks = Math.max(1, spec.renderDistanceChunks());
        this.width = Math.max(1, spec.width());
        this.height = Math.max(1, spec.height());
        this.fov = Math.max(1.0F, spec.fov());
        this.renderMode = Objects.requireNonNull(spec.renderMode(), "view render mode");
        this.renderOptions = spec.renderOptions() == null
                ? SkyesightViewRenderOptions.defaults()
                : spec.renderOptions();
        this.camera = new SkyesightInternalCamera();
        this.secondaryViewContext.setViewId(this.id);

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

        retireRemoteRegistration();
        SkyesightVisualWorldManager.close(this.id);
        SkyesightPortalChunkStorage.clearView(this.id);
        SkyesightClientChunkHandler.invalidateView(this.id);

        this.dimension = dimension;
    }

    @Override
    public void setRenderDistance(int renderDistanceChunks) {
        this.renderDistanceChunks = Math.max(1, renderDistanceChunks);
    }

    @Override
    public void setRemoteLoadRadiusLimit(int radiusChunks) {
        if (radiusChunks < 1) throw new IllegalArgumentException("Remote load radius limit must be positive");
        this.remoteLoadRadiusLimit = radiusChunks;
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
    public SkyesightViewRenderOptions renderOptions() {
        return this.renderOptions;
    }

    @Override
    public void setRenderOptions(SkyesightViewRenderOptions options) {
        this.renderOptions = options == null ? SkyesightViewRenderOptions.defaults() : options;
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
            this.target = this.secondaryViewContext.getOrCreateRenderTarget(width, height);
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

        if (!ensureRemoteRegistration(minecraft)) return;
        SkyesightVisualWorld visualWorld = null;
        if (!this.dimension.equals(minecraft.level.dimension())) {
            visualWorld = SkyesightVisualWorldManager.getOrCreate(this.id, this.dimension);
            if (visualWorld == null || visualWorld.isClosed()) return;
            SkyesightClientChunkRequester.requestChunksFor(this.id, this.dimension, this.camera.minecraftCamera(),
                    SkyesightRemoteRadiusPolicy.loadRadius(this.renderDistanceChunks, this.remoteLoadRadiusLimit));
            if (!visualWorld.environmentReady()) return;
            logRemoteDiagnostics(visualWorld);
        }
        var level = visualWorld == null ? minecraft.level : visualWorld.level();
        var explicitPosition = this.camera.position();
        var explicitRotation = new Quaternionf(this.camera.rotation());
        this.camera.minecraftCamera().setup(level, minecraft.player, false, false, partialTick);
        this.camera.setPosition(explicitPosition);
        this.camera.setRotation(explicitRotation);
        var frame = new SecondaryViewFrame(this.camera.minecraftCamera(), this.target, this.width, this.height,
                renderProjectionMatrix, modelMatrix, terrainCullingProjectionMatrix,
                SkyesightFrustumFactory.create(this.camera.minecraftCamera(), modelMatrix, terrainCullingProjectionMatrix));
        var options = frame.diagnostics();
        options.setCameraView(true);
        options.setRenderToCurrentTarget(true);
        options.setEntityWatchRegionId(this.id);
        options.setPortalInstanceId(this.id.toString());
        options.setTerrainChunkRadius(this.renderDistanceChunks);
        options.setPortalOwnedRenderRadiusChunks(this.renderDistanceChunks);
        options.setSameDimPlayerLoadedReuseRadiusChunks(this.renderDistanceChunks);
        options.setEntityChunkRadius(this.renderDistanceChunks);
        options.setBlockEntityChunkRadius(this.renderDistanceChunks);
        options.setBlockUpdateChunkRadius(this.renderDistanceChunks);
        options.setRenderTerrain(this.renderOptions.terrain());
        options.setRenderSky(false);
        options.setRenderTranslucent(true);
        options.setRenderEntities(this.renderOptions.entities());
        options.setRenderBlockEntities(this.renderOptions.blockEntities());
        options.setRenderParticles(this.renderOptions.particles());
        options.setPublishEntityWatchRegion(this.renderOptions.publishWatchRegion());
        if (visualWorld == null && this.renderOptions.publishWatchRegion()) {
            PortalSecondaryWorldRenderer.updateSecondaryChunkWatchRegionIfNeeded(minecraft, frame, this.secondaryViewContext);
            PortalSecondaryWorldRenderer.updateRemoteEntityTrackingIfEnabled(frame, this.secondaryViewContext, minecraft);
        }
        var scene = new SecondarySceneFrame(this.id, level, visualWorld, frame, this.secondaryViewContext,
                partialTick, this.target, () -> {});
        var saved = SecondarySceneOutputState.capture();
        boolean previousSecondary = PortalSecondaryRenderState.renderingSecondaryView;
        try {
            PortalSecondaryRenderState.renderingSecondaryView = true;
            this.target.bindWrite(true);
            RenderSystem.disableScissor();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            this.target.setClearColor(0, 0, 0, 1);
            this.target.clear(Minecraft.ON_OSX);
            this.target.bindWrite(true);
            if (this.renderOptions.sky()) SecondarySceneEnvironmentRenderer.renderBackground(scene, this.secondaryViewContext.clouds());
            SecondarySceneRenderer.renderContents(scene);
            if (this.renderMode == SkyesightRenderMode.WORLD) SkyesightCameraOutput.makeOpaque(this.target);
        } finally {
            PortalSecondaryRenderState.renderingSecondaryView = previousSecondary;
            saved.restore();
        }
    }
    private boolean ensureRemoteRegistration(Minecraft minecraft) {
        if (minecraft.level == null || this.dimension.equals(minecraft.level.dimension())) {
            retireRemoteRegistration();
            return true;
        }

        if (this.remoteGeneration > 0L
                && this.dimension.equals(this.remoteDimension)
                && SkyesightRemoteViewRegistry.accepts(
                        this.id,
                        this.remoteGeneration,
                        this.dimension
                )) {
            return true;
        }

        retireRemoteRegistration();
        this.remoteGeneration = NEXT_CAMERA_GENERATION.getAndIncrement();
        this.remoteDimension = this.dimension;
        SkyesightRemoteViewRegistry.register(
                this.id,
                this.remoteDimension,
                this.remoteGeneration
        );
        sendRemoteLifecycle(true);
        SkyesightRemoteCenterDiagnostics.log("register",this.id,this.remoteGeneration,"target="+this.remoteDimension.location());

        // Registration and chunk requests share the same network channel. Wait
        // one frame so the server authorizes this generation before warmup.
        return false;
    }

    private void retireRemoteRegistration() {
        if (this.remoteGeneration <= 0L || this.remoteDimension == null) {
            return;
        }
        SkyesightRemoteCenterDiagnostics.log("retire",this.id,this.remoteGeneration,"target="+this.remoteDimension.location());
        SkyesightClientChunkRequester.reset(this.id, this.remoteGeneration);
        sendRemoteLifecycle(false);
        SkyesightRemoteViewRegistry.unregister(this.id, this.remoteGeneration);
        this.remoteGeneration = 0L;
        this.remoteDimension = null;
        this.lastRemoteDiagnosticMillis = 0L;
    }

    private void sendRemoteLifecycle(boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null
                || this.remoteGeneration <= 0L
                || this.remoteDimension == null) {
            return;
        }
        PacketDistributor.sendToServer(new SkyesightRemoteViewLifecyclePayload(
                this.id,
                this.remoteGeneration,
                this.remoteDimension,
                active
        ));
    }

    private void logRemoteDiagnostics(SkyesightVisualWorld visualWorld) {
        if (!SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastRemoteDiagnosticMillis < REMOTE_DIAGNOSTIC_INTERVAL_MILLIS) {
            return;
        }
        this.lastRemoteDiagnosticMillis = now;

        SkyesightClientChunkRequester.ViewRequestDiagnostics requests =
                SkyesightClientChunkRequester.diagnostics(this.id);
        Vec3 position = this.camera.position();
        ChunkPos cameraChunk = new ChunkPos(
                ((int) Math.floor(position.x())) >> 4,
                ((int) Math.floor(position.z())) >> 4
        );
        Skyesight.LOGGER.info(
                "[Skyesight] SKYESIGHT_CAMERA_REMOTE: view={} dim={} generation={} registered={} "
                        + "visualWorld={} backend={} requestedChunks={} pendingChunks={} loadedChunks={} "
                        + "cameraChunk={},{} requestCenter={}",
                this.id,
                this.dimension.location(),
                this.remoteGeneration,
                SkyesightRemoteViewRegistry.accepts(
                        this.id,
                        this.remoteGeneration,
                        this.dimension
                ),
                visualWorld != null && !visualWorld.isClosed(),
                visualWorld == null ? "none" : visualWorld.terrainBackendName(),
                requests.lastRequestedChunks(),
                requests.pendingChunks(),
                visualWorld == null ? 0 : visualWorld.level().getChunkSource().getLoadedChunksCount(),
                cameraChunk.x,
                cameraChunk.z,
                requests.center() == null ? "none" : requests.center()
        );
    }

    @Override
    public void close() {
        retireRemoteRegistration();
        synchronized (this.targetLock) {
            if (this.closed) {
                return;
            }

            this.closed = true;
            this.target = null;
        }

        SkyesightVisualWorldManager.close(this.id);
        SkyesightPortalChunkStorage.clearView(this.id);
        SkyesightClientChunkHandler.invalidateView(this.id);
        SkyesightClientThreading.runOnRenderThread(() -> { this.secondaryViewContext.close(); });
    }
    @Override
    public boolean isClosed() {
        return this.closed;
    }

}
