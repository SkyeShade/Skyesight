package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.skyeshade.skyesight.PortalFirstUseTimeline;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.view.SkyesightMutableCamera;
import com.skyeshade.skyesight.client.world.SameLevelSkyesightChunkSource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

public final class SecondaryViewContext {
    private final SkyesightMutableCamera camera = new SkyesightMutableCamera();
    private final ChunkTracker sodiumChunkTracker = new ChunkTracker();
    private final SameLevelSkyesightChunkSource sodiumChunkSource = new SameLevelSkyesightChunkSource();
    private final SecondaryRemoteEntityTracker remoteEntityTracker = new SecondaryRemoteEntityTracker();

    private TextureTarget renderTarget;
    private SodiumWorldRenderer sodiumRenderer;
    private ClientLevel sodiumRendererLevel;
    private ChunkPos remoteChunkCenter;
    private ChunkPos sodiumRebuildCenter;
    private ClientLevel frozenRemoteLevel;
    private Vec3 frozenRemoteCameraPosition;
    private int firstVisibleTerrainFramesSkipped;
    private ResourceLocation viewId;
    private boolean sodiumRendererQueued;
    private long sodiumRendererQueuedTick = -1L;
    private long sodiumRendererCreatedTick = -1L;
    private int sodiumRendererReadyAgeFrames;
    private boolean sodiumRendererAssignedFromPool;
    private boolean setupTerrainCalled;
    private int currentSameDimReuseRadiusChunks = -1;
    private int targetSameDimReuseRadiusChunks = -1;
    private int terrainWarmupAgeFrames;
    private boolean sameDimTerrainWarmupComplete;
    private int lastCandidateSectionCount;
    private int lastReadyChunkPopulationCount;
    private final Queue<ChunkPos> pendingSodiumRebuildChunks = new ArrayDeque<>();
    private final LongSet pendingSodiumBlockUpdateChunks = new LongOpenHashSet();
    private ChunkPos pendingSodiumRebuildCenter;
    private float lastPortalCameraYaw = Float.NaN;
    private double lastPortalCameraYawDeltaDegrees;
    private double recentSetupTerrainMs;
    private boolean mainCompiledSectionsPrimed;
    private int primerCursor;
    private int primedSections;
    private int primerFrames;
    private TerrainSetupReuseKey lastTerrainSetupReuseKey;
    private int terrainSetupPendingRebuildStableFrames;

    public SkyesightMutableCamera camera() {
        return this.camera;
    }

    public ChunkTracker sodiumChunkTracker() {
        return this.sodiumChunkTracker;
    }

    public SameLevelSkyesightChunkSource sodiumChunkSource() {
        return this.sodiumChunkSource;
    }

    public SecondaryRemoteEntityTracker remoteEntityTracker() {
        return this.remoteEntityTracker;
    }

    public TextureTarget getOrCreateRenderTarget(int width, int height) {
        boolean allocated = this.renderTarget == null;
        boolean resized = this.renderTarget != null
                && (this.renderTarget.width != width || this.renderTarget.height != height);
        if (this.renderTarget == null
                || this.renderTarget.width != width
                || this.renderTarget.height != height) {
            Minecraft minecraft = Minecraft.getInstance();
            int boundBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int mainTarget = minecraft == null ? -1 : minecraft.getMainRenderTarget().frameBufferId;
            long start = System.nanoTime();
            int boundAfter;
            long durationMs;
            try {
                if (this.viewId != null) {
                    PortalFirstUseTimeline.logOnce(
                            "first_render_target_allocation_start:" + this.viewId + ":" + width + "x" + height,
                            "first_render_target_allocation_start",
                            this.viewId,
                            "allocated=" + (allocated ? "yes" : "no")
                                    + " resized=" + (resized ? "yes" : "no")
                                    + " size=" + width + "x" + height
                    );
                }
                if (this.renderTarget != null) {
                    this.renderTarget.destroyBuffers();
                }

                this.renderTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            } finally {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, boundBefore);
            }
            durationMs = (System.nanoTime() - start) / 1_000_000L;
            boundAfter = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            if (this.viewId != null) {
                PortalFirstUseTimeline.logOnce(
                        "first_render_target_allocation_end:" + this.viewId + ":" + width + "x" + height,
                        "first_render_target_allocation_end",
                        this.viewId,
                        "durationMs=" + durationMs
                                + " boundMainBefore=" + (boundBefore == mainTarget ? "yes" : "no")
                                + " boundMainAfter=" + (boundAfter == mainTarget ? "yes" : "no")
                );
            }
            if (SkyesightDebugConfig.shouldLogRenderTargetAudit()) {
                Skyesight.LOGGER.info(
                        "[Skyesight] PORTAL_FIRST_TARGET_ALLOC_AUDIT: viewId={} targetType={} allocated={} resized={} clearCalled={} boundMainBefore={} boundMainAfter={} durationMs={} reason={}",
                        this.viewId == null ? "-" : this.viewId,
                        "SecondaryViewContext",
                        allocated ? "yes" : "no",
                        resized ? "yes" : "no",
                        "no",
                        boundBefore == mainTarget ? "yes" : "no",
                        boundAfter == mainTarget ? "yes" : "no",
                        durationMs,
                        "getOrCreateRenderTarget"
                );
            }
        }

        return this.renderTarget;
    }

    public TextureTarget renderTarget() {
        return this.renderTarget;
    }

    public SodiumWorldRenderer sodiumRenderer() {
        return this.sodiumRenderer;
    }

    public void setSodiumRenderer(SodiumWorldRenderer sodiumRenderer) {
        this.sodiumRenderer = sodiumRenderer;
        this.sodiumRendererReadyAgeFrames = 0;
        this.setupTerrainCalled = false;
    }

    public ClientLevel sodiumRendererLevel() {
        return this.sodiumRendererLevel;
    }

    public void setSodiumRendererLevel(ClientLevel sodiumRendererLevel) {
        this.sodiumRendererLevel = sodiumRendererLevel;
    }

    public ResourceLocation viewId() {
        return this.viewId;
    }

    public void setViewId(ResourceLocation viewId) {
        this.viewId = viewId;
    }

    public boolean sodiumRendererQueued() {
        return this.sodiumRendererQueued;
    }

    public void markSodiumRendererQueued(long gameTime) {
        this.sodiumRendererQueued = true;
        if (this.sodiumRendererQueuedTick < 0L) {
            this.sodiumRendererQueuedTick = gameTime;
        }
    }

    public void clearSodiumRendererQueued() {
        this.sodiumRendererQueued = false;
    }

    public long sodiumRendererQueuedTick() {
        return this.sodiumRendererQueuedTick;
    }

    public long sodiumRendererCreatedTick() {
        return this.sodiumRendererCreatedTick;
    }

    public void markSodiumRendererCreated(long gameTime) {
        this.sodiumRendererCreatedTick = gameTime;
        this.sodiumRendererQueued = false;
        this.sodiumRendererReadyAgeFrames = 0;
        this.setupTerrainCalled = false;
    }

    public boolean sodiumRendererAssignedFromPool() {
        return this.sodiumRendererAssignedFromPool;
    }

    public void setSodiumRendererAssignedFromPool(boolean sodiumRendererAssignedFromPool) {
        this.sodiumRendererAssignedFromPool = sodiumRendererAssignedFromPool;
    }

    public boolean setupTerrainCalled() {
        return this.setupTerrainCalled;
    }

    public void markSetupTerrainCalled() {
        this.setupTerrainCalled = true;
    }

    public int currentSameDimReuseRadiusChunks() {
        return this.currentSameDimReuseRadiusChunks;
    }

    public int targetSameDimReuseRadiusChunks() {
        return this.targetSameDimReuseRadiusChunks;
    }

    public int terrainWarmupAgeFrames() {
        return this.terrainWarmupAgeFrames;
    }

    public boolean sameDimTerrainWarmupComplete() {
        return this.sameDimTerrainWarmupComplete;
    }

    public int lastCandidateSectionCount() {
        return this.lastCandidateSectionCount;
    }

    public int lastReadyChunkPopulationCount() {
        return this.lastReadyChunkPopulationCount;
    }

    public void updateSameDimTerrainWarmup(
            int targetReuseRadiusChunks,
            int initialRadiusChunks,
            int firstActiveMaxRadiusChunks,
            int growthIntervalFrames,
            int growthStepChunks
    ) {
        int target = Math.max(0, targetReuseRadiusChunks);
        int initial = Math.max(0, Math.min(target, Math.min(initialRadiusChunks, firstActiveMaxRadiusChunks)));
        if (this.targetSameDimReuseRadiusChunks != target || this.currentSameDimReuseRadiusChunks < 0) {
            this.targetSameDimReuseRadiusChunks = target;
            this.currentSameDimReuseRadiusChunks = initial;
            this.terrainWarmupAgeFrames = 0;
            this.sameDimTerrainWarmupComplete = this.currentSameDimReuseRadiusChunks >= target;
            return;
        }

        this.terrainWarmupAgeFrames++;
        if (!this.sameDimTerrainWarmupComplete
                && growthIntervalFrames > 0
                && this.terrainWarmupAgeFrames % growthIntervalFrames == 0) {
            this.currentSameDimReuseRadiusChunks = Math.min(
                    target,
                    this.currentSameDimReuseRadiusChunks + Math.max(1, growthStepChunks)
            );
        }
        this.sameDimTerrainWarmupComplete = this.currentSameDimReuseRadiusChunks >= target;
    }

    public void recordSameDimTerrainPopulation(int candidateSectionCount, int readyChunkPopulationCount) {
        this.lastCandidateSectionCount = candidateSectionCount;
        this.lastReadyChunkPopulationCount = readyChunkPopulationCount;
    }

    public Queue<ChunkPos> pendingSodiumRebuildChunks() {
        return this.pendingSodiumRebuildChunks;
    }

    public LongSet pendingSodiumBlockUpdateChunks() {
        return this.pendingSodiumBlockUpdateChunks;
    }

    public boolean enqueuePendingSodiumBlockUpdateChunk(ChunkPos chunk, int maxPendingChunks) {
        if (chunk == null || maxPendingChunks <= 0) {
            return false;
        }
        if (this.pendingSodiumBlockUpdateChunks.size() >= maxPendingChunks
                && !this.pendingSodiumBlockUpdateChunks.contains(chunk.toLong())) {
            return false;
        }
        return this.pendingSodiumBlockUpdateChunks.add(chunk.toLong());
    }

    public void clearPendingSodiumBlockUpdateChunks() {
        this.pendingSodiumBlockUpdateChunks.clear();
    }

    public ChunkPos pendingSodiumRebuildCenter() {
        return this.pendingSodiumRebuildCenter;
    }

    public void setPendingSodiumRebuildCenter(ChunkPos pendingSodiumRebuildCenter) {
        this.pendingSodiumRebuildCenter = pendingSodiumRebuildCenter;
    }

    public double updatePortalCameraYawDelta(float yaw) {
        if (Float.isNaN(this.lastPortalCameraYaw)) {
            this.lastPortalCameraYaw = yaw;
            this.lastPortalCameraYawDeltaDegrees = 0.0D;
            return 0.0D;
        }
        float delta = Math.abs(Mth.wrapDegrees(yaw - this.lastPortalCameraYaw));
        this.lastPortalCameraYaw = yaw;
        this.lastPortalCameraYawDeltaDegrees = delta;
        return delta;
    }

    public double lastPortalCameraYawDeltaDegrees() {
        return this.lastPortalCameraYawDeltaDegrees;
    }

    public double recentSetupTerrainMs() {
        return this.recentSetupTerrainMs;
    }

    public void setRecentSetupTerrainMs(double recentSetupTerrainMs) {
        this.recentSetupTerrainMs = recentSetupTerrainMs;
    }

    public int sodiumRendererReadyAgeFrames() {
        return this.sodiumRendererReadyAgeFrames;
    }

    public boolean mainCompiledSectionsPrimed() {
        return this.mainCompiledSectionsPrimed;
    }

    public void setMainCompiledSectionsPrimed(boolean mainCompiledSectionsPrimed) {
        this.mainCompiledSectionsPrimed = mainCompiledSectionsPrimed;
    }

    public int primerCursor() {
        return this.primerCursor;
    }

    public void setPrimerCursor(int primerCursor) {
        this.primerCursor = primerCursor;
    }

    public int primedSections() {
        return this.primedSections;
    }

    public void addPrimedSections(int primedSections) {
        this.primedSections += Math.max(0, primedSections);
    }

    public int primerFrames() {
        return this.primerFrames;
    }

    public void incrementPrimerFrames() {
        this.primerFrames++;
    }

    public void incrementSodiumRendererReadyAgeFrames() {
        this.sodiumRendererReadyAgeFrames++;
    }

    public ChunkPos remoteChunkCenter() {
        return this.remoteChunkCenter;
    }

    public void setRemoteChunkCenter(ChunkPos remoteChunkCenter) {
        this.remoteChunkCenter = remoteChunkCenter;
    }

    public ChunkPos sodiumRebuildCenter() {
        return this.sodiumRebuildCenter;
    }

    public void setSodiumRebuildCenter(ChunkPos sodiumRebuildCenter) {
        this.sodiumRebuildCenter = sodiumRebuildCenter;
    }

    public Vec3 frozenRemoteCameraPosition() {
        return this.frozenRemoteCameraPosition;
    }

    public ChunkPos frozenRemoteChunkCenter() {
        return this.frozenRemoteCameraPosition == null
                ? null
                : new ChunkPos(BlockPos.containing(this.frozenRemoteCameraPosition));
    }

    public Vec3 getOrSetFrozenRemoteCameraPosition(ClientLevel level, Vec3 cameraPosition) {
        if (this.frozenRemoteCameraPosition == null || this.frozenRemoteLevel != level) {
            this.frozenRemoteLevel = level;
            this.frozenRemoteCameraPosition = cameraPosition;
            this.remoteChunkCenter = new ChunkPos(BlockPos.containing(cameraPosition));
            this.sodiumRebuildCenter = null;
        }

        return this.frozenRemoteCameraPosition;
    }

    public void resetFrozenRemoteCenter() {
        this.frozenRemoteLevel = null;
        this.frozenRemoteCameraPosition = null;
        this.remoteChunkCenter = null;
        this.sodiumRebuildCenter = null;
    }

    public int firstVisibleTerrainFramesSkipped() {
        return this.firstVisibleTerrainFramesSkipped;
    }

    public void incrementFirstVisibleTerrainFramesSkipped() {
        this.firstVisibleTerrainFramesSkipped++;
    }

    public TerrainSetupReuseDecision terrainSetupReuseDecision(TerrainSetupReuseKey key) {
        if (key == null) {
            return TerrainSetupReuseDecision.empty("missing-key");
        }
        if (this.lastTerrainSetupReuseKey == null) {
            this.terrainSetupPendingRebuildStableFrames = 0;
            return TerrainSetupReuseDecision.fromKey(false, key, "first-setup");
        }
        TerrainSetupReuseDecision decision = this.lastTerrainSetupReuseKey.canReuse(key);
        this.terrainSetupPendingRebuildStableFrames = decision.pendingRebuildChanged()
                ? 0
                : this.terrainSetupPendingRebuildStableFrames + 1;
        return decision.withPendingRebuildStableFrames(this.terrainSetupPendingRebuildStableFrames);
    }

    public void recordTerrainSetupReuseKey(TerrainSetupReuseKey key) {
        this.lastTerrainSetupReuseKey = key;
    }

    public void clearTerrainSetupReuseKey(String reason) {
        this.lastTerrainSetupReuseKey = null;
        this.terrainSetupPendingRebuildStableFrames = 0;
    }

    public void close() {
        if (this.renderTarget != null) {
            this.renderTarget.destroyBuffers();
            this.renderTarget = null;
        }
        if (this.sodiumRenderer != null) {
            try {
                this.sodiumRenderer.setLevel(null);
            } catch (RuntimeException exception) {
                Skyesight.LOGGER.warn("[Skyesight] Failed to release secondary Sodium renderer during context close", exception);
            }
            this.sodiumRenderer = null;
            this.sodiumRendererLevel = null;
        }

        this.resetFrozenRemoteCenter();
        this.pendingSodiumRebuildChunks.clear();
        this.pendingSodiumBlockUpdateChunks.clear();
        this.clearTerrainSetupReuseKey("context-close");
    }

    public record TerrainSetupReuseDecision(
            boolean reuse,
            double positionDeltaBlocks,
            double rotationDeltaDegrees,
            String reason,
            int pendingRebuildCountGlobal,
            int pendingRebuildCountForView,
            int pendingBlockUpdateCountForView,
            boolean reuseBlockedByPendingViewChunks,
            boolean pendingRebuildChanged,
            int pendingRebuildOldCount,
            int pendingRebuildNewCount,
            int pendingRebuildAdded,
            int pendingRebuildRemoved,
            int pendingRebuildStableFrames,
            boolean reuseBlockedByNewPendingChunks,
            boolean readyChunksActualChanged,
            int readyChunksOldCount,
            int readyChunksNewCount
    ) {
        private static TerrainSetupReuseDecision empty(String reason) {
            return new TerrainSetupReuseDecision(
                    false,
                    0.0D,
                    0.0D,
                    reason,
                    0,
                    0,
                    0,
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    0,
                    0
            );
        }

        private static TerrainSetupReuseDecision fromKey(boolean reuse, TerrainSetupReuseKey key, String reason) {
            return new TerrainSetupReuseDecision(
                    reuse,
                    0.0D,
                    0.0D,
                    reason,
                    key.pendingRebuildCountGlobal(),
                    key.pendingRebuildCountForView(),
                    key.pendingBlockUpdateCountForView(),
                    false,
                    false,
                    key.pendingRebuildCountForView(),
                    key.pendingRebuildCountForView(),
                    0,
                    0,
                    0,
                    false,
                    false,
                    key.readyChunkCount(),
                    key.readyChunkCount()
            );
        }

        private TerrainSetupReuseDecision withPendingRebuildStableFrames(int stableFrames) {
            return new TerrainSetupReuseDecision(
                    this.reuse,
                    this.positionDeltaBlocks,
                    this.rotationDeltaDegrees,
                    this.reason,
                    this.pendingRebuildCountGlobal,
                    this.pendingRebuildCountForView,
                    this.pendingBlockUpdateCountForView,
                    this.reuseBlockedByPendingViewChunks,
                    this.pendingRebuildChanged,
                    this.pendingRebuildOldCount,
                    this.pendingRebuildNewCount,
                    this.pendingRebuildAdded,
                    this.pendingRebuildRemoved,
                    stableFrames,
                    this.reuseBlockedByNewPendingChunks,
                    this.readyChunksActualChanged,
                    this.readyChunksOldCount,
                    this.readyChunksNewCount
            );
        }
    }

    public record TerrainSetupReuseKey(
            ResourceLocation viewId,
            ClientLevel level,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            int viewportWidth,
            int viewportHeight,
            int terrainChunkRadius,
            int portalOwnedRenderRadiusChunks,
            int reuseRadiusChunks,
            boolean reusePlayerLoadedChunksForSameDim,
            boolean renderTranslucent,
            int readyChunkCount,
            long readyChunkSignature,
            int pendingRebuildCountGlobal,
            int pendingRebuildCountForView,
            long pendingRebuildSignatureForView,
            int pendingBlockUpdateCount,
            int pendingBlockUpdateCountForView,
            long pendingBlockUpdateSignatureForView,
            Matrix4f projectionMatrix,
            Matrix4f modelViewMatrix,
            Matrix4f cullProjectionMatrix
    ) {
        private static final double POSITION_REUSE_EPSILON_BLOCKS = 0.05D;
        private static final double ROTATION_REUSE_EPSILON_DEGREES = 0.25D;

        private TerrainSetupReuseDecision canReuse(TerrainSetupReuseKey next) {
            if (!Objects.equals(this.viewId, next.viewId)) {
                return decision(false, next, "view-changed");
            }
            if (this.level != next.level) {
                return decision(false, next, "level-changed");
            }
            if (this.viewportWidth != next.viewportWidth || this.viewportHeight != next.viewportHeight) {
                return decision(false, next, "viewport-changed");
            }
            if (!matrixEquals(this.projectionMatrix, next.projectionMatrix)) {
                return decision(false, next, "projection-changed:projection");
            }
            if (!matrixEquals(this.modelViewMatrix, next.modelViewMatrix)) {
                return decision(false, next, "projection-changed:modelView");
            }
            if (!matrixEquals(this.cullProjectionMatrix, next.cullProjectionMatrix)) {
                return decision(false, next, "projection-changed:cullProjection");
            }
            if (this.terrainChunkRadius != next.terrainChunkRadius
                    || this.portalOwnedRenderRadiusChunks != next.portalOwnedRenderRadiusChunks
                    || this.reuseRadiusChunks != next.reuseRadiusChunks
                    || this.reusePlayerLoadedChunksForSameDim != next.reusePlayerLoadedChunksForSameDim
                    || this.renderTranslucent != next.renderTranslucent) {
                return decision(false, next, "settings-changed");
            }
            if (this.readyChunkCount != next.readyChunkCount) {
                return decision(false, next, "ready-chunks-changed");
            }
            if (this.readyChunkSignature != next.readyChunkSignature) {
                return decision(false, next, "ready-chunks-changed");
            }
            boolean pendingRebuildChanged = this.pendingRebuildCountForView != next.pendingRebuildCountForView
                    || this.pendingRebuildSignatureForView != next.pendingRebuildSignatureForView;
            boolean pendingBlockUpdateChanged = this.pendingBlockUpdateCountForView != next.pendingBlockUpdateCountForView
                    || this.pendingBlockUpdateSignatureForView != next.pendingBlockUpdateSignatureForView;
            if (pendingRebuildChanged || pendingBlockUpdateChanged) {
                return decision(
                        false,
                        next,
                        "pending-updates:rebuild=" + next.pendingRebuildCountForView
                                + "/" + next.pendingRebuildCountGlobal
                                + ",block=" + next.pendingBlockUpdateCountForView
                                + "/" + next.pendingBlockUpdateCount
                );
            }

            double positionDelta = this.cameraPosition.distanceTo(next.cameraPosition);
            double rotationDelta = rotationDeltaDegrees(this.cameraRotation, next.cameraRotation);
            if (positionDelta >= POSITION_REUSE_EPSILON_BLOCKS) {
                return decision(false, next, "camera-position-changed");
            }
            if (rotationDelta >= ROTATION_REUSE_EPSILON_DEGREES) {
                return decision(false, next, "camera-rotation-changed");
            }
            return decision(true, next, "stable-camera");
        }

        private TerrainSetupReuseDecision decision(boolean reuse, TerrainSetupReuseKey next, String reason) {
            double positionDelta = this.cameraPosition.distanceTo(next.cameraPosition);
            double rotationDelta = rotationDeltaDegrees(this.cameraRotation, next.cameraRotation);
            boolean pendingBlocked = next.pendingRebuildCountForView > 0 || next.pendingBlockUpdateCountForView > 0;
            boolean pendingRebuildChanged = this.pendingRebuildCountForView != next.pendingRebuildCountForView
                    || this.pendingRebuildSignatureForView != next.pendingRebuildSignatureForView;
            boolean pendingBlockUpdateChanged = this.pendingBlockUpdateCountForView != next.pendingBlockUpdateCountForView
                    || this.pendingBlockUpdateSignatureForView != next.pendingBlockUpdateSignatureForView;
            boolean pendingChanged = pendingRebuildChanged || pendingBlockUpdateChanged;
            boolean readyChanged = this.readyChunkCount != next.readyChunkCount
                    || this.readyChunkSignature != next.readyChunkSignature;
            int pendingAdded = Math.max(0, next.pendingRebuildCountForView - this.pendingRebuildCountForView);
            int pendingRemoved = Math.max(0, this.pendingRebuildCountForView - next.pendingRebuildCountForView);
            return new TerrainSetupReuseDecision(
                    reuse,
                    positionDelta,
                    rotationDelta,
                    reason,
                    next.pendingRebuildCountGlobal,
                    next.pendingRebuildCountForView,
                    next.pendingBlockUpdateCountForView,
                    pendingBlocked && pendingChanged,
                    pendingRebuildChanged,
                    this.pendingRebuildCountForView,
                    next.pendingRebuildCountForView,
                    pendingAdded,
                    pendingRemoved,
                    pendingRebuildChanged ? 0 : 1,
                    pendingChanged,
                    readyChanged,
                    this.readyChunkCount,
                    next.readyChunkCount
            );
        }

        private static double rotationDeltaDegrees(Quaternionf a, Quaternionf b) {
            Quaternionf normalizedA = new Quaternionf(a).normalize();
            Quaternionf normalizedB = new Quaternionf(b).normalize();
            double dot = Math.abs(
                    normalizedA.x * normalizedB.x
                            + normalizedA.y * normalizedB.y
                            + normalizedA.z * normalizedB.z
                            + normalizedA.w * normalizedB.w
            );
            dot = Math.max(-1.0D, Math.min(1.0D, dot));
            return Math.toDegrees(2.0D * Math.acos(dot));
        }

        private static boolean matrixEquals(Matrix4f a, Matrix4f b) {
            return a.equals(b, 1.0E-4F);
        }
    }
}
