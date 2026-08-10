package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.portal.PortalRenderCostAudit;
import com.skyeshade.skyesight.client.render.config.PortalRemoteChunkConfig;
import com.skyeshade.skyesight.client.render.config.PortalSecondaryRenderConfig;
import com.skyeshade.skyesight.client.render.config.PortalSodiumRenderConfig;
import com.skyeshade.skyesight.client.render.sodium.PortalSodiumRendererPool;
import com.skyeshade.skyesight.client.render.sodium.SameDimMainSodiumSectionReuse;
import com.skyeshade.skyesight.client.render.sodium.SameDimPortalTerrainPrimer;
import com.skyeshade.skyesight.client.render.sodium.SkyesightSodiumRenderContext;
import com.skyeshade.skyesight.client.render.sodium.SodiumSecondaryViewState;
import com.skyeshade.skyesight.client.render.state.PortalRemoteChunkRuntimeState;
import com.skyeshade.skyesight.client.render.state.PortalSecondaryRenderState;
import com.skyeshade.skyesight.client.render.state.PortalSodiumReflectionState;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class PortalSecondarySodiumTerrainRenderer {
    private PortalSecondarySodiumTerrainRenderer() {
    }

    static void prewarmPortalRenderersIfNeeded(Minecraft minecraft) {
        if (!PortalSodiumRenderConfig.PORTAL_SODIUM_PREWARM_ENABLED) {
            return;
        }

        PortalSodiumRendererPool.prewarmIfNeeded(
                minecraft,
                PortalSodiumRenderConfig.DEFAULT_PORTAL_SODIUM_PREWARM_RENDERERS,
                PortalSodiumRenderConfig.DEFAULT_PORTAL_SODIUM_PREWARM_DELAY_FRAMES,
                PortalSodiumRenderConfig.DEFAULT_PORTAL_SODIUM_PREWARM_PER_FRAME
        );
    }

    static void clearRendererPool() {
        PortalSodiumRendererPool.clear();
    }

    static boolean render(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick
    ) {
        frame.diagnostics().setBackend("SODIUM_TERRAIN_ONLY");
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }

        SodiumSecondaryViewState sodiumState = SodiumSecondaryViewState.getOrCreate(context);
        Camera camera = frame.camera();
        PortalSecondaryRenderState.activeRemoteTerrainChunkRadius =
                PortalSecondaryWorldRenderer.configuredSameDimRenderChunkRadius(frame);
        Matrix4f projection = frame.projectionMatrix();
        Matrix4f modelView = frame.modelViewMatrix();
        var frustum = frame.frustum();

        RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        if (!frame.diagnostics().renderToCurrentTarget()
                || frame.diagnostics().renderSkyInCurrentTarget()) {
            PortalSecondaryWorldRenderer.renderSecondarySkyIfEnabled(frame, minecraft, partialTick);
        } else {
            PortalSecondaryWorldRenderer.resetSecondaryFeatureDiagnosticsForDirectRender();
        }

        RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        ResourceLocation viewId = frame.diagnostics().entityWatchRegionId();
        String stickFlashMode = stickFlashTestModeFor(viewId);
        if ("no_sodium".equals(stickFlashMode)) {
            return false;
        }
        if (!frame.diagnostics().renderTerrain()) {
            return false;
        }
        if (SkyesightDebugConfig.DEBUG_DISABLE_SAME_DIM_PORTAL_TERRAIN_FOR_FLASH_TEST
                && frame.diagnostics().renderToCurrentTarget()) {
            return false;
        }

        int sectionCount = Math.max(1, minecraft.level.getSectionsCount());
        int portalOwnedRadius = PortalSecondaryWorldRenderer.configuredPortalOwnedRenderRadiusChunks(frame);
        int targetReuseRadius = PortalSecondaryWorldRenderer.configuredSameDimPlayerLoadedReuseRadiusChunks(frame);
        context.updateSameDimTerrainWarmup(
                targetReuseRadius,
                PortalSodiumRenderConfig.DEFAULT_SAME_DIM_REUSE_INITIAL_RADIUS_CHUNKS,
                PortalSodiumRenderConfig.DEFAULT_SAME_DIM_REUSE_FIRST_ACTIVE_MAX_RADIUS_CHUNKS,
                PortalSodiumRenderConfig.DEFAULT_SAME_DIM_REUSE_RADIUS_GROWTH_INTERVAL_FRAMES,
                PortalSodiumRenderConfig.DEFAULT_SAME_DIM_REUSE_RADIUS_GROWTH_STEP_CHUNKS
        );
        double yawDelta = context.updatePortalCameraYawDelta(camera.getYRot());
        boolean turnThrottled = yawDelta > PortalSodiumRenderConfig.DEFAULT_PORTAL_TURN_THROTTLE_DEGREES;
        boolean warmupCompleteBeforeWork = context.sameDimTerrainWarmupComplete();
        if (!warmupCompleteBeforeWork
                && PortalSecondaryRenderState.sameDimPortalTerrainWarmupsThisFrame >= PortalSodiumRenderConfig.DEFAULT_MAX_NEW_SAME_DIM_PORTAL_TERRAIN_WARMUPS_PER_FRAME) {
            return false;
        }
        if (!warmupCompleteBeforeWork) {
            PortalSecondaryRenderState.sameDimPortalTerrainWarmupsThisFrame++;
        }
        int effectiveReuseRadius = context.currentSameDimReuseRadiusChunks();
        if (SkyesightDebugConfig.DEBUG_FORCE_SAME_DIM_REUSE_RADIUS_ON_FIRST_ACTIVATION >= 0
                && context.terrainWarmupAgeFrames() <= 1) {
            effectiveReuseRadius = Math.min(
                    effectiveReuseRadius,
                    SkyesightDebugConfig.DEBUG_FORCE_SAME_DIM_REUSE_RADIUS_ON_FIRST_ACTIVATION
            );
        }
        if (turnThrottled) {
            effectiveReuseRadius = Math.min(effectiveReuseRadius, PortalSodiumRenderConfig.DEFAULT_PORTAL_TURN_THROTTLED_REUSE_RADIUS_CAP);
        }
        if (PortalSodiumRenderConfig.DEFAULT_PORTAL_REUSE_SHRINK_ON_SPIKE
                && context.recentSetupTerrainMs() > PortalSodiumRenderConfig.DEFAULT_PORTAL_REUSE_GROWTH_MAX_SETUP_MS) {
            effectiveReuseRadius = Math.max(portalOwnedRadius, effectiveReuseRadius - 1);
        }
        effectiveReuseRadius = Math.max(0, Math.min(targetReuseRadius, effectiveReuseRadius));
        int maxScannedChunks = Math.max(
                1,
                PortalSodiumRenderConfig.DEFAULT_MAX_SAME_DIM_READY_SECTIONS_SCANNED_PER_FRAME / sectionCount
        );
        long readyChunkStart = PortalRenderCostAudit.start();
        boolean chunkTrackerSkippedForStickTest = "no_chunk_tracker".equals(stickFlashMode);
        if (!chunkTrackerSkippedForStickTest) {
            sodiumState.chunkSource().updateReadyChunks(
                    minecraft.level,
                    sodiumState.chunkTracker(),
                    camera.getPosition(),
                    portalOwnedRadius,
                    effectiveReuseRadius,
                    PortalSecondaryWorldRenderer.configuredReusePlayerLoadedChunksForSameDim(frame),
                    PortalSodiumRenderConfig.DEFAULT_MAX_SAME_DIM_READY_CHUNKS_ADDED_PER_FRAME,
                    maxScannedChunks
            );
        }
        PortalRenderCostAudit.record(viewId, "readyChunks", readyChunkStart);
        PortalRenderCostAudit.record(viewId, "terrainChunkReadyUpdate", readyChunkStart);
        int candidateSectionsAfterBudget = chunkTrackerSkippedForStickTest
                ? 0
                : sodiumState.chunkSource().lastScannedChunkCount() * sectionCount;
        int readyChunksAdded = chunkTrackerSkippedForStickTest
                ? 0
                : sodiumState.chunkSource().lastAddedChunkCount();
        boolean budgetLimited = !chunkTrackerSkippedForStickTest
                && (!sodiumState.chunkSource().lastScanCompletedCycle()
                || sodiumState.chunkSource().lastBudgetSkippedChunkCount() > 0);
        context.recordSameDimTerrainPopulation(candidateSectionsAfterBudget, readyChunksAdded);
        if (budgetLimited && !warmupCompleteBeforeWork) {
            return false;
        }
        long sodiumAcquireStart = PortalRenderCostAudit.start();
        SodiumWorldRenderer renderer = frame.diagnostics().renderToCurrentTarget()
                ? getOrCreateSodiumRenderer(minecraft, minecraft.level, context, viewId)
                : getSodiumRendererForSecondaryDebug(minecraft, minecraft.level, context, viewId);
        PortalRenderCostAudit.record(viewId, "sodiumAcquire", sodiumAcquireStart);
        if (renderer == null) {
            return false;
        }
        boolean usingMainSodiumRenderer = SodiumWorldRenderer.instanceNullable() == renderer;
        if (PortalSecondaryWorldRenderer.shouldSkipNewPortalTerrainWarmup(frame, context)) {
            context.incrementFirstVisibleTerrainFramesSkipped();
            context.incrementSodiumRendererReadyAgeFrames();
            return false;
        }
        context.incrementSodiumRendererReadyAgeFrames();
        long cameraUpdateStart = PortalRenderCostAudit.start();
        Viewport viewport = ((ViewportProvider) frustum).sodium$createViewport();
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(projection, modelView);
        var cameraPosition = camera.getPosition();
        PortalRenderCostAudit.record(viewId, "terrainCameraUpdate", cameraUpdateStart);
        long sectionPrimerStart = PortalRenderCostAudit.start();

        int clampedReuseRadius = Math.clamp(
                effectiveReuseRadius,
                PortalSodiumRenderConfig.DEFAULT_SAME_DIM_MAIN_SECTION_PRIMER_RADIUS_CHUNKS,
                targetReuseRadius
        );
        SameDimPortalTerrainPrimer.primeFromMainCompiledSections(
                viewId,
                minecraft.level,
                context,
                renderer,
                cameraPosition,
                clampedReuseRadius,
                PortalSodiumRenderConfig.DEFAULT_MAX_MAIN_SECTION_PRIMER_SECTIONS_PER_FRAME,
                PortalSodiumRenderConfig.DEFAULT_MAX_MAIN_SECTION_PRIMER_FRAMES
        );
        PortalRenderCostAudit.record(viewId, "sectionPrimer", sectionPrimerStart);
        PortalRenderCostAudit.record(viewId, "terrainSectionCollect", sectionPrimerStart);

        RenderDevice.enterManagedCode();

        try (SkyesightSodiumRenderContext.Scope ignored =
                     SkyesightSodiumRenderContext.push(
                             sodiumState.chunkTracker(),
                             PortalSodiumRenderConfig.SODIUM_DISABLE_OCCLUSION_CULLING_FOR_SECONDARY
                     )) {
            int pendingRebuildChunkCountGlobal = context.pendingSodiumRebuildChunks().size();
            int pendingRebuildChunkCountForView = countPendingRebuildChunksForTrackedTerrain(context, sodiumState);
            long pendingRebuildChunkSignatureForView = pendingRebuildChunkSignatureForTrackedTerrain(context, sodiumState);
            int pendingBlockUpdateChunkCount = context.pendingSodiumBlockUpdateChunks().size();
            int pendingBlockUpdateChunkCountForView = countPendingBlockUpdatesForTrackedTerrain(context, sodiumState);
            long pendingBlockUpdateSignatureForView = pendingBlockUpdateSignatureForTrackedTerrain(context, sodiumState);
            SecondaryViewContext.TerrainSetupReuseKey setupReuseKey =
                    new SecondaryViewContext.TerrainSetupReuseKey(
                            viewId,
                            minecraft.level,
                            camera.getPosition(),
                            new Quaternionf(camera.rotation()),
                            frame.viewportWidth(),
                            frame.viewportHeight(),
                            frame.diagnostics().terrainChunkRadius(),
                            frame.diagnostics().portalOwnedRenderRadiusChunks(),
                            effectiveReuseRadius,
                            PortalSecondaryWorldRenderer.configuredReusePlayerLoadedChunksForSameDim(frame),
                            frame.diagnostics().renderTranslucent(),
                            sodiumState.chunkSource().trackedChunkCount(),
                            sodiumState.chunkSource().trackedChunkSignature(),
                            pendingRebuildChunkCountGlobal,
                            pendingRebuildChunkCountForView,
                            pendingRebuildChunkSignatureForView,
                            pendingBlockUpdateChunkCount,
                            pendingBlockUpdateChunkCountForView,
                            pendingBlockUpdateSignatureForView,
                            frame.projectionMatrix(),
                            frame.modelViewMatrix(),
                            frame.cullProjectionMatrix()
                    );
            SecondaryViewContext.TerrainSetupReuseDecision setupReuseDecision =
                    context.terrainSetupReuseDecision(setupReuseKey);
            boolean setupTerrainReused = context.setupTerrainCalled() && setupReuseDecision.reuse();
            long setupTerrainStart = 0L;
            long setupTerrainDurationMs = 0L;
            if (!setupTerrainReused) {
                setupTerrainStart = PortalRenderCostAudit.start();
                renderer.setupTerrain(
                        camera,
                        viewport,
                        minecraft.player.isSpectator(),
                        false
                );
                context.markSetupTerrainCalled();
                context.recordTerrainSetupReuseKey(setupReuseKey);
                setupTerrainDurationMs = (System.nanoTime() - setupTerrainStart) / 1_000_000L;
                PortalRenderCostAudit.record(viewId, "terrainSetup", setupTerrainStart);
                PortalRenderCostAudit.record(viewId, "terrainSetupTerrainCall", setupTerrainStart);
                PortalRenderCostAudit.record(viewId, "sodiumSetupTerrain", setupTerrainStart);
                context.setRecentSetupTerrainMs(setupTerrainDurationMs);
            } else {
                context.setRecentSetupTerrainMs(0.0D);
            }
            PortalRenderCostAudit.recordTerrainSetupDetails(
                    viewId,
                    !setupTerrainReused,
                    setupTerrainReused,
                    setupReuseDecision.positionDeltaBlocks(),
                    setupReuseDecision.rotationDeltaDegrees(),
                    setupReuseDecision.pendingRebuildCountGlobal(),
                    setupReuseDecision.pendingRebuildCountForView(),
                    pendingBlockUpdateChunkCount,
                    setupReuseDecision.pendingBlockUpdateCountForView(),
                    setupReuseDecision.reuseBlockedByPendingViewChunks(),
                    setupReuseDecision.pendingRebuildChanged(),
                    setupReuseDecision.pendingRebuildOldCount(),
                    setupReuseDecision.pendingRebuildNewCount(),
                    setupReuseDecision.pendingRebuildAdded(),
                    setupReuseDecision.pendingRebuildRemoved(),
                    setupReuseDecision.pendingRebuildStableFrames(),
                    setupReuseDecision.reuseBlockedByNewPendingChunks(),
                    setupReuseDecision.readyChunksActualChanged(),
                    setupReuseDecision.readyChunksOldCount(),
                    setupReuseDecision.readyChunksNewCount(),
                    sodiumState.chunkSource().lastAddedChunkCount(),
                    sodiumState.chunkSource().lastRemovedChunkCount(),
                    setupReuseDecision.reason()
            );
            BlockPos sharedRemoteCenter = PortalSecondaryWorldRenderer.secondaryRemoteCenterBlockPos(context, frame);
            scheduleRemoteSodiumRebuildsIfNeeded(renderer, minecraft.level, sharedRemoteCenter, context, sodiumState);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderType.solid().setupRenderState();

            try {
                if (frame.diagnostics().renderToCurrentTarget()) {
                    PortalSecondaryWorldRenderer.applyDirectDepthModeAtSodiumDrawPoint();
                }

                long drawSolidStart = PortalRenderCostAudit.start();
                renderer.drawChunkLayer(
                        RenderType.solid(),
                        matrices,
                        cameraPosition.x(),
                        cameraPosition.y(),
                        cameraPosition.z()
                );
                PortalRenderCostAudit.record(viewId, "terrainSolid", drawSolidStart);
                long drawCutoutStart = PortalRenderCostAudit.start();
                drawSodiumTerrainLayer(renderer, RenderType.cutoutMipped(), matrices, cameraPosition);
                drawSodiumTerrainLayer(renderer, RenderType.cutout(), matrices, cameraPosition);
                PortalRenderCostAudit.record(viewId, "terrainCutout", drawCutoutStart);
                boolean alwaysBorrowDrawEnabled =
                        SkyesightDebugConfig.ENABLE_SAME_DIM_MAIN_SECTION_BORROWED_DRAWING_SOLID_CUTOUT;
                if (alwaysBorrowDrawEnabled) {
                    SameDimMainSodiumSectionReuse.drawBorrowedSolidCutoutSections(
                            viewId,
                            minecraft.level,
                            renderer,
                            cameraPosition,
                            portalOwnedRadius,
                            effectiveReuseRadius,
                            PortalSecondaryWorldRenderer.configuredReusePlayerLoadedChunksForSameDim(frame),
                            viewport,
                            matrices,
                            minecraft.screen != null,
                            GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                                    == minecraft.getMainRenderTarget().frameBufferId,
                            GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                            true
                    );
                }
            } catch (RuntimeException exception) {
                Skyesight.LOGGER.warn(
                        "[Skyesight] Sodium terrain draw failed",
                        exception
                );
            } finally {
                RenderType.solid().clearRenderState();
            }
            if (frame.diagnostics().renderToCurrentTarget() && PortalSecondaryRenderConfig.DIRECT_RENDER_TERRAIN_DRAW_SOLID_ONLY) {
                renderDirectTranslucentTerrainIfEnabled(frame, renderer, matrices, usingMainSodiumRenderer);
                return true;
            }

            try {
                if (!frame.diagnostics().renderToCurrentTarget()
                        && PortalSecondaryRenderConfig.SECONDARY_FEATURE_TRANSLUCENT
                        && !usingMainSodiumRenderer) {
                    long translucentStart = PortalRenderCostAudit.start();
                    SecondarySodiumTranslucentTerrainPass.render(frame, renderer, matrices);
                    PortalRenderCostAudit.record(viewId, "terrainTranslucent", translucentStart);
                }
            } catch (RuntimeException exception) {
                Skyesight.LOGGER.warn(
                        "[Skyesight] Sodium translucent terrain draw failed",
                        exception
                );
            }
            return true;
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    private static String stickFlashTestModeFor(ResourceLocation viewId) {
        if (viewId == null) {
            return "normal";
        }
        RegisteredPortalView view = SkyesightPortalApi.getPortal(viewId.toString());
        if (view == null || !"debug-stick".equals(view.sourceTag())) {
            return "normal";
        }
        if (SkyesightDebugConfig.DEBUG_STICK_RENDER_NO_SODIUM_RENDERER_FOR_FLASH_TEST) {
            return "no_sodium";
        }
        if (SkyesightDebugConfig.DEBUG_STICK_RENDER_NO_CHUNK_TRACKER_UPDATE_FOR_FLASH_TEST) {
            return "no_chunk_tracker";
        }
        return "normal";
    }

    private static void renderDirectTranslucentTerrainIfEnabled(
            SecondaryViewFrame frame,
            SodiumWorldRenderer renderer,
            ChunkRenderMatrices matrices,
            boolean usingMainSodiumRenderer
    ) {
        if (!PortalSecondaryRenderConfig.DIRECT_RENDER_TRANSLUCENT_TERRAIN
                || !frame.diagnostics().renderToCurrentTarget()
                || !frame.diagnostics().renderTranslucent()
                || usingMainSodiumRenderer) {
            return;
        }

        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(SecondaryBlockEntityPass.depthWriteEnabled());
            SecondarySodiumTranslucentTerrainPass.render(frame, renderer, matrices);
        } catch (RuntimeException exception) {
            Skyesight.LOGGER.warn("[Skyesight] Direct portal translucent terrain failed", exception);
        } finally {
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawSodiumTerrainLayer(
            SodiumWorldRenderer renderer,
            RenderType renderType,
            ChunkRenderMatrices matrices,
            Vec3 cameraPosition
    ) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        renderType.setupRenderState();

        try {
            renderer.drawChunkLayer(
                    renderType,
                    matrices,
                    cameraPosition.x(),
                    cameraPosition.y(),
                    cameraPosition.z()
            );
        } finally {
            renderType.clearRenderState();
        }
    }

    private static SodiumWorldRenderer createPortalSodiumRendererInRenderStage(
            Minecraft minecraft,
            ClientLevel level,
            SecondaryViewContext context,
            ResourceLocation viewId
    ) {
        SodiumSecondaryViewState state = SodiumSecondaryViewState.getOrCreate(context);
        SodiumWorldRenderer previousRenderer = state.renderer();
        if (previousRenderer != null) {
            return previousRenderer;
        }
        SodiumWorldRenderer pooled = PortalSodiumRendererPool.acquire(viewId, level);
        if (pooled != null) {
            state.setRenderer(pooled);
            state.setRendererLevel(level);
            context.markSodiumRendererCreated(level.getGameTime());
            context.setSodiumRendererAssignedFromPool(true);
            return pooled;
        }
        if (PortalSecondaryRenderState.newPortalSodiumRenderersCreatedThisFrame >= PortalSodiumRenderConfig.DEFAULT_MAX_NEW_PORTAL_SODIUM_RENDERERS_PER_FRAME) {
            return null;
        }

        SodiumWorldRenderer renderer = null;
        long createStart = System.nanoTime();
        boolean created = false;
        boolean calledSetLevel = false;
        PortalSodiumRendererPool.logFallbackCreate(viewId, 0L, "fallback-create-start");
        RenderDevice.enterManagedCode();
        try {
            renderer = new SodiumWorldRenderer(minecraft);
            created = true;
            state.setRenderer(renderer);
            state.setRendererLevel(level);
            context.markSodiumRendererCreated(level.getGameTime());
            context.setSodiumRendererAssignedFromPool(false);
            renderer.setLevel(level);
            calledSetLevel = true;
            renderer.scheduleTerrainUpdate();
            PortalSecondaryRenderState.newPortalSodiumRenderersCreatedThisFrame++;
            return renderer;
        } catch (IllegalStateException exception) {
            return null;
        } finally {
            RenderDevice.exitManagedCode();
            long durationMs = (System.nanoTime() - createStart) / 1_000_000L;
            PortalSodiumRendererPool.logFirstUseInitAudit(
                    "fallback_render_stage_create",
                    created,
                    calledSetLevel,
                    durationMs,
                    renderer == null ? "fallback-create-failed" : "fallback-renderer-created"
            );
            PortalSodiumRendererPool.logFallbackCreate(
                    viewId,
                    durationMs,
                    renderer == null ? "fallback-create-failed" : "fallback-renderer-created"
            );
        }
    }

    private static SodiumWorldRenderer getOrCreateSodiumRenderer(
            Minecraft minecraft,
            ClientLevel level,
            SecondaryViewContext context,
            ResourceLocation viewId
    ) {
        SodiumSecondaryViewState state = SodiumSecondaryViewState.getOrCreate(context);
        SodiumWorldRenderer sodiumRenderer = state.renderer();
        ClientLevel sodiumRendererLevel = state.rendererLevel();

        if (sodiumRenderer == null || sodiumRendererLevel != level) {
            if (sodiumRenderer != null) {
                RenderDevice.enterManagedCode();

                try {
                    sodiumRenderer.setLevel(null);
                } finally {
                    RenderDevice.exitManagedCode();
                }
            }

            return createPortalSodiumRendererInRenderStage(minecraft, level, context, viewId);
        }

        return sodiumRenderer;
    }

    private static SodiumWorldRenderer getSodiumRendererForSecondaryDebug(
            Minecraft minecraft,
            ClientLevel level,
            SecondaryViewContext context,
            ResourceLocation viewId
    ) {
        if (PortalSodiumRenderConfig.USE_MAIN_SODIUM_RENDERER_FOR_SECONDARY_VIEW) {
            SodiumWorldRenderer mainRenderer = SodiumWorldRenderer.instanceNullable();

            if (mainRenderer != null) {
                return mainRenderer;
            }
        }

        return getOrCreateSodiumRenderer(minecraft, level, context, viewId);
    }

    private static boolean scheduleRemoteSodiumRebuildsIfNeeded(
            SodiumWorldRenderer renderer,
            ClientLevel level,
            BlockPos cameraBlockPos,
            SecondaryViewContext context,
            SodiumSecondaryViewState state
    ) {
        if (!PortalSodiumRenderConfig.SODIUM_SCHEDULE_REMOTE_REBUILDS || PortalRemoteChunkRuntimeState.loadedChunksInRadius <= 0) {
            return false;
        }

        ChunkPos center = new ChunkPos(cameraBlockPos);
        int expectedClientChunks = expectedRemoteChunkCount();
        int expectedRequiredClientChunks = expectedRequiredRemoteChunkCount();

        if (PortalRemoteChunkRuntimeState.loadedChunksInRadius < expectedClientChunks) {
            PortalSecondaryRenderState.sodiumDelayedRebuildStableFrames = 0;
            return false;
        }

        if (PortalRemoteChunkRuntimeState.requiredLoadedChunksInRadius < expectedRequiredClientChunks) {
            PortalSecondaryRenderState.sodiumDelayedRebuildStableFrames = 0;
            return false;
        }

        if (PortalRemoteChunkRuntimeState.clientChunkNonAirSamples <= 0 && PortalRemoteChunkRuntimeState.clientCenterSectionNonAirCount <= 0) {
            PortalSecondaryRenderState.sodiumDelayedRebuildStableFrames = 0;
            return false;
        }

        PortalSecondaryRenderState.sodiumDelayedRebuildStableFrames++;

        if (PortalSecondaryRenderState.sodiumDelayedRebuildStableFrames < PortalSodiumRenderConfig.SODIUM_DELAYED_REBUILD_FRAMES) {
            return false;
        }

        if (countSodiumSectionsInRadius(renderer, level, cameraBlockPos) <= 0) {
            return false;
        }

        enqueuePortalRebuildChunksIfNeeded(center, context);
        int budget = context.lastPortalCameraYawDeltaDegrees() > PortalSodiumRenderConfig.DEFAULT_PORTAL_TURN_THROTTLE_DEGREES
                ? PortalSodiumRenderConfig.DEFAULT_PORTAL_TURN_THROTTLED_REBUILD_BUDGET
                : PortalSodiumRenderConfig.DEFAULT_MAX_PORTAL_SECTION_REBUILDS_SCHEDULED_PER_FRAME;
        int scheduledNow = drainPortalRebuildQueue(renderer, level, context, budget);
        boolean scheduled = scheduledNow > 0;
        if (scheduled) {
            renderer.scheduleTerrainUpdate();
        }
        context.setSodiumRebuildCenter(center);
        return scheduled;
    }

    private static int enqueuePortalRebuildChunksIfNeeded(ChunkPos center, SecondaryViewContext context) {
        if (center == null || context == null) {
            return 0;
        }
        if (center.equals(context.pendingSodiumRebuildCenter()) && !context.pendingSodiumRebuildChunks().isEmpty()) {
            return 0;
        }
        if (center.equals(context.sodiumRebuildCenter()) && context.pendingSodiumRebuildChunks().isEmpty()) {
            return 0;
        }
        context.pendingSodiumRebuildChunks().clear();
        context.setPendingSodiumRebuildCenter(center);
        if (PortalSodiumRenderConfig.SODIUM_REBUILD_CENTER_SECTION_ONLY) {
            context.pendingSodiumRebuildChunks().add(center);
            return 1;
        }

        int radius = Math.max(0, PortalSecondaryRenderState.activeRemoteTerrainChunkRadius);
        int discovered = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                context.pendingSodiumRebuildChunks().add(new ChunkPos(center.x + dx, center.z + dz));
                discovered++;
            }
        }
        return discovered;
    }

    private static int countPendingRebuildChunksForTrackedTerrain(SecondaryViewContext context, SodiumSecondaryViewState state) {
        if (context == null) {
            return 0;
        }
        int count = 0;
        for (ChunkPos chunk : context.pendingSodiumRebuildChunks()) {
            if (state.chunkSource().isTracked(chunk)) {
                count++;
            }
        }
        return count;
    }

    private static long pendingRebuildChunkSignatureForTrackedTerrain(SecondaryViewContext context, SodiumSecondaryViewState state) {
        if (context == null) {
            return 0L;
        }
        long signature = 0L;
        for (ChunkPos chunk : context.pendingSodiumRebuildChunks()) {
            if (state.chunkSource().isTracked(chunk)) {
                signature += mixPackedChunk(ChunkPos.asLong(chunk.x, chunk.z));
            }
        }
        return signature;
    }

    private static int countPendingBlockUpdatesForTrackedTerrain(SecondaryViewContext context, SodiumSecondaryViewState state) {
        if (context == null) {
            return 0;
        }
        int count = 0;
        for (long packed : context.pendingSodiumBlockUpdateChunks()) {
            if (state.chunkSource().isTracked(packed)) {
                count++;
            }
        }
        return count;
    }

    private static long pendingBlockUpdateSignatureForTrackedTerrain(SecondaryViewContext context, SodiumSecondaryViewState state) {
        if (context == null) {
            return 0L;
        }
        long signature = 0L;
        for (long packed : context.pendingSodiumBlockUpdateChunks()) {
            if (state.chunkSource().isTracked(packed)) {
                signature += mixPackedChunk(packed);
            }
        }
        return signature;
    }

    private static long mixPackedChunk(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private static int drainPortalRebuildQueue(
            SodiumWorldRenderer renderer,
            ClientLevel level,
            SecondaryViewContext context,
            int budget
    ) {
        if (renderer == null || level == null || context == null || budget <= 0) {
            return 0;
        }
        int scheduled = 0;
        while (scheduled < budget && !context.pendingSodiumRebuildChunks().isEmpty()) {
            ChunkPos chunk = context.pendingSodiumRebuildChunks().poll();
            renderer.scheduleRebuildForChunks(
                    chunk.x,
                    level.getMinSection(),
                    chunk.z,
                    chunk.x,
                    level.getMaxSection() - 1,
                    chunk.z,
                    false
            );
            scheduled++;
        }
        return scheduled;
    }

    private static int expectedRemoteChunkCount() {
        int width = PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS * 2 + 1;
        return width * width;
    }

    private static int expectedRequiredRemoteChunkCount() {
        int width = PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS * 2 + 1;
        return width * width;
    }

    private static int countSodiumSectionsInRadius(
            SodiumWorldRenderer renderer,
            ClientLevel level,
            BlockPos cameraBlockPos
    ) {
        try {
            RenderSectionManager manager = getSodiumRenderSectionManager(renderer);

            if (manager == null) {
                return 0;
            }

            Long2ReferenceMap<RenderSection> sectionByPosition =
                    (Long2ReferenceMap<RenderSection>) PortalSodiumReflectionState.sodiumSectionManagerSectionByPositionField.get(manager);
            int centerChunkX = SectionPos.blockToSectionCoord(cameraBlockPos.getX());
            int centerChunkZ = SectionPos.blockToSectionCoord(cameraBlockPos.getZ());
            int minSection = level.getMinSection();
            int maxSection = level.getMaxSection();
            int stored = 0;

            int radius = Math.max(0, PortalSecondaryRenderState.activeRemoteTerrainChunkRadius);
            for (int chunkZ = centerChunkZ - radius;
                 chunkZ <= centerChunkZ + radius;
                 chunkZ++) {
                for (int chunkX = centerChunkX - radius;
                     chunkX <= centerChunkX + radius;
                     chunkX++) {
                    for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                        RenderSection section = sectionByPosition.get(SectionPos.asLong(chunkX, sectionY, chunkZ));

                        if (section == null) {
                            continue;
                        }

                        stored++;
                    }
                }
            }

            return stored;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            PortalSodiumReflectionState.sodiumReflectionFailed = true;
            return 0;
        }
    }

    private static RenderSectionManager getSodiumRenderSectionManager(SodiumWorldRenderer renderer)
            throws ReflectiveOperationException {
        if (renderer == null) {
            return null;
        }

        initializeSodiumReflection();

        if (PortalSodiumReflectionState.sodiumReflectionFailed) {
            return null;
        }

        return (RenderSectionManager) PortalSodiumReflectionState.sodiumWorldRendererSectionManagerField.get(renderer);
    }

    private static void initializeSodiumReflection() throws NoSuchFieldException {
        if (PortalSodiumReflectionState.sodiumWorldRendererSectionManagerField != null || PortalSodiumReflectionState.sodiumReflectionFailed) {
            return;
        }

        PortalSodiumReflectionState.sodiumWorldRendererSectionManagerField =
                SodiumWorldRenderer.class.getDeclaredField("renderSectionManager");
        PortalSodiumReflectionState.sodiumWorldRendererSectionManagerField.setAccessible(true);
        PortalSodiumReflectionState.sodiumSectionManagerSectionByPositionField =
                RenderSectionManager.class.getDeclaredField("sectionByPosition");
        PortalSodiumReflectionState.sodiumSectionManagerSectionByPositionField.setAccessible(true);
        PortalSodiumReflectionState.sodiumSectionManagerRenderListsField =
                RenderSectionManager.class.getDeclaredField("renderLists");
        PortalSodiumReflectionState.sodiumSectionManagerRenderListsField.setAccessible(true);
    }
}
