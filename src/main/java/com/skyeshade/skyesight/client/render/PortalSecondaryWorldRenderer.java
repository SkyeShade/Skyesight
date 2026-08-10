package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.api.PortalRenderSettings;
import com.skyeshade.skyesight.api.SkyesightClipPlane;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.compat.iris.SkyesightIrisCompat;
import com.skyeshade.skyesight.client.portal.DirectStencilPortalMath;
import com.skyeshade.skyesight.client.portal.DirectPortalProjectionMath;
import com.skyeshade.skyesight.client.portal.PortalRenderCostAudit;
import com.skyeshade.skyesight.client.portal.PortalRenderTargetBounds;
import com.skyeshade.skyesight.client.portal.SecondaryPortalCompositePass;
import com.skyeshade.skyesight.client.portal.PortalRenderDebugStatus;
import com.skyeshade.skyesight.client.portal.PortalFrame;
import com.skyeshade.skyesight.client.portal.PortalFrameMath;

import com.skyeshade.skyesight.client.render.config.PortalProjectionConfig;
import com.skyeshade.skyesight.client.render.config.PortalRemoteChunkConfig;
import com.skyeshade.skyesight.client.render.config.PortalSecondaryRenderConfig;
import com.skyeshade.skyesight.client.render.config.PortalSodiumRenderConfig;
import com.skyeshade.skyesight.client.render.remote.PortalRemoteChunkController;
import com.skyeshade.skyesight.client.render.state.PortalSecondaryRenderState;
import com.skyeshade.skyesight.client.render.state.PortalRemoteChunkRuntimeState;
import com.skyeshade.skyesight.mixin.client.GameRendererSetupInvoker;
import com.skyeshade.skyesight.server.SkyesightSecondaryWatchRegion;
import com.skyeshade.skyesight.server.SkyesightSecondaryChunkWatchRegion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


public final class PortalSecondaryWorldRenderer {
    private static int secondaryContextNonSecondaryTargetBindCount;
    private static String secondaryContextLastNonSecondaryBind = "n/a";
    private static boolean secondaryEntityPassAttempted;

    private PortalSecondaryWorldRenderer() {}

    public static void invalidateViewCaches(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_CENTERS.remove(viewId);
        PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_RADII.remove(viewId);
    }

    private static TextureTarget getOrCreatePortalRenderTarget(
            SecondaryViewContext context,
            Minecraft minecraft,
            Matrix4f mainViewProjection,
            PortalFrame entrancePortal,
            Vec3 mainCameraPosition,
            int fallbackWidth,
            int fallbackHeight,
            ResourceLocation viewId,
            String sourceTag
    ) {
        // Returns the reusable off-screen target used to render a portal view.
        var mainTarget = minecraft == null ? null : minecraft.getMainRenderTarget();
        long targetTotalStart = PortalRenderCostAudit.start();
        long targetResolveStart = PortalRenderCostAudit.start();
        PortalRenderTargetBounds.TargetSize targetSize = PortalRenderTargetBounds.resolveTargetSize(
                viewId,
                mainViewProjection,
                entrancePortal,
                mainCameraPosition,
                mainTarget == null ? 0 : mainTarget.width,
                mainTarget == null ? 0 : mainTarget.height,
                fallbackWidth,
                fallbackHeight
        );
        PortalRenderCostAudit.record(viewId, "targetResolveSize", targetResolveStart);
        PortalRenderTargetBounds.logIfEnabled(viewId, sourceTag, targetSize);
        long targetRestoreStart = PortalRenderCostAudit.start();
        PortalTargetRenderState allocationState = PortalTargetRenderState.capture();
        PortalRenderCostAudit.record(viewId, "targetRestore", targetRestoreStart);
        TextureTarget previous = context.renderTarget();
        int previousWidth = previous == null ? 0 : previous.width;
        int previousHeight = previous == null ? 0 : previous.height;
        TextureTarget target;
        long targetAcquireStart = PortalRenderCostAudit.start();
        try {
            target = context.getOrCreateRenderTarget(targetSize.width(), targetSize.height());
        } finally {
            targetRestoreStart = PortalRenderCostAudit.start();
            allocationState.restore();
            PortalRenderCostAudit.record(viewId, "targetRestore", targetRestoreStart);
        }
        PortalRenderCostAudit.record(viewId, "targetAcquireOrResize", targetAcquireStart);
        PortalRenderCostAudit.record(viewId, "target", targetTotalStart);
        PortalRenderCostAudit.recordTarget(viewId, target.width, target.height, targetSize.boundsScaled());
        boolean created = previous == null;
        boolean resized = previous != null && (previousWidth != target.width || previousHeight != target.height);
        if (created || resized) {
            PortalRenderCostAudit.record(viewId, "targetResize", targetAcquireStart);
        } else {
            PortalRenderCostAudit.record(viewId, "targetAcquire", targetAcquireStart);
        }
        boolean requestedSizeChanged = previous != null
                && (previousWidth != targetSize.width() || previousHeight != targetSize.height());
        if (SkyesightDebugConfig.RENDER_PERF_AUDIT
                && resized
                && previousWidth == targetSize.width()
                && previousHeight == targetSize.height()) {
            Skyesight.LOGGER.error(
                    "[Skyesight] PORTAL_TARGET_REDUNDANT_RESIZE_INVARIANT view={} previous={}x{} requested={}x{} actualAfter={}x{} stage=getOrCreatePortalRenderTarget",
                    viewId == null ? "-" : viewId,
                    previousWidth,
                    previousHeight,
                    targetSize.width(),
                    targetSize.height(),
                    target.width,
                    target.height
            );
        }
        PortalRenderCostAudit.recordTargetLifecycle(
                viewId,
                previousWidth,
                previousHeight,
                targetSize.width(),
                targetSize.height(),
                target.width,
                target.height,
                created,
                resized,
                !created && !resized,
                requestedSizeChanged,
                created ? "created" : resized ? "resized" : requestedSizeChanged ? "size-changed" : "reused"
        );
        PortalRenderTargetBounds.recordResolutionUse(
                viewId,
                targetSize,
                target.width,
                target.height,
                previousWidth,
                previousHeight,
                previous == null || resized
        );
        return target;
    }


    public static void beginPortalRenderFrame() {
        PortalSecondaryRenderState.newPortalSodiumRenderersCreatedThisFrame = 0;
        PortalSecondaryRenderState.sameDimPortalTerrainWarmupsThisFrame = 0;
    }

    public static void prewarmPortalSodiumRenderersIfNeeded(Minecraft minecraft) {
        SecondarySodiumTerrainPass.prewarmPortalRenderersIfNeeded(minecraft);
    }



    public static TextureTarget renderRegisteredSecondaryViewFromPose(
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            boolean publishEntityWatchRegion,
            ResourceLocation entityWatchRegionId,
            boolean runEntityPass,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            String portalInstanceId,
            ResourceLocation viewId,
            String sourceTag
    ) {
        return renderSecondaryViewFromPose(
                context,
                minecraft,
                event,
                cameraPosition,
                cameraRotation,
                publishEntityWatchRegion,
                entityWatchRegionId,
                runEntityPass,
                exitPortal,
                null,
                portalInstanceId,
                entrancePortal,
                viewId,
                sourceTag
        );
    }

    private static TextureTarget renderSecondaryViewFromPose(
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            boolean publishEntityWatchRegion,
            ResourceLocation entityWatchRegionId,
            boolean runEntityPass,
            PortalFrame exitPortal,
            SkyesightClipPlane clipPlane,
            String portalInstanceId,
            PortalFrame targetSizingEntrancePortal,
            ResourceLocation viewId,
            String sourceTag
    ) {
        Matrix4f currentViewProjection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        PortalTargetRenderState renderState = PortalTargetRenderState.capture();
        TextureTarget output = getOrCreatePortalRenderTarget(
                context,
                minecraft,
                currentViewProjection,
                targetSizingEntrancePortal,
                event.getCamera() == null ? null : event.getCamera().getPosition(),
                PortalProjectionConfig.VIEW_WIDTH,
                PortalProjectionConfig.VIEW_HEIGHT,
                viewId,
                sourceTag
        );

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        PortalSecondaryRenderState.renderingSecondaryView = true;

        try {
            output.bindWrite(true);
            RenderSystem.viewport(0, 0, output.width, output.height);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            output.clear(Minecraft.ON_OSX);
            output.bindWrite(true);

            SecondaryViewFrame frame = createSecondaryViewFrameFromPose(
                    context,
                    minecraft,
                    event,
                    output,
                    cameraPosition,
                    cameraRotation,
                    publishEntityWatchRegion,
                    entityWatchRegionId,
                    runEntityPass,
                    exitPortal,
                    clipPlane,
                    portalInstanceId
            );
            if (SecondarySodiumTerrainPass.render(frame, context, minecraft, event)) {
                renderPostTerrainFeatures(frame, context, minecraft, event.getPartialTick().getGameTimeDeltaPartialTick(true));
            }

            return output;
        } finally {
            PortalSecondaryRenderState.renderingSecondaryView = false;

            modelViewStack.popMatrix();
            renderState.restore();
        }
    }

    public static TextureTarget renderCameraViewToTexture(
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            ResourceLocation viewId,
            int width,
            int height,
            float fov,
            int renderDistanceChunks,
            boolean renderSky,
            boolean renderTerrain,
            boolean renderBlockEntities,
            boolean renderEntities,
            boolean renderParticles,
            boolean publishWatchRegion
    ) {
        int targetWidth = Math.max(1, width);
        int targetHeight = Math.max(1, height);
        PortalTargetRenderState renderState = PortalTargetRenderState.capture();
        TextureTarget output;
        try {
            output = context.getOrCreateRenderTarget(targetWidth, targetHeight);
        } finally {
            renderState.restore();
        }

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        PortalSecondaryRenderState.renderingSecondaryView = true;

        try {
            output.bindWrite(true);
            RenderSystem.viewport(0, 0, output.width, output.height);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            output.clear(Minecraft.ON_OSX);
            output.bindWrite(true);

            SecondaryViewFrame frame = createSecondaryViewFrameFromPose(
                    context,
                    minecraft,
                    partialTick,
                    output,
                    targetWidth,
                    targetHeight,
                    cameraPosition,
                    cameraRotation,
                    publishWatchRegion,
                    publishWatchRegion ? viewId : null,
                    renderEntities,
                    null,
                    null,
                    null,
                    null,
                    null,
                    viewId == null ? "camera-view" : "camera-view:" + viewId,
                    0,
                    fov,
                    renderDistanceChunks
            );
            frame.diagnostics().setRenderSkyInCurrentTarget(renderSky);
            frame.diagnostics().setTerrainChunkRadius(renderDistanceChunks);
            frame.diagnostics().setPortalOwnedRenderRadiusChunks(renderDistanceChunks);
            frame.diagnostics().setSameDimPlayerLoadedReuseRadiusChunks(renderDistanceChunks);
            frame.diagnostics().setReusePlayerLoadedChunksForSameDim(false);
            frame.diagnostics().setEntityChunkRadius(renderDistanceChunks);
            frame.diagnostics().setBlockEntityChunkRadius(renderDistanceChunks);
            frame.diagnostics().setBlockUpdateChunkRadius(renderDistanceChunks);
            frame.diagnostics().setRenderSky(renderSky);
            frame.diagnostics().setRenderTerrain(renderTerrain);
            frame.diagnostics().setRenderTranslucent(true);
            frame.diagnostics().setRenderEntities(renderEntities);
            frame.diagnostics().setRenderBlockEntities(renderBlockEntities);
            frame.diagnostics().setRenderParticles(renderParticles);
            if (publishWatchRegion) {
                updateSecondaryChunkWatchRegionIfNeeded(minecraft, frame, context);
            }
            if (SecondarySodiumTerrainPass.render(frame, context, minecraft, partialTick)) {
                renderPostTerrainFeatures(frame, context, minecraft, partialTick);
            }

            return output;
        } finally {
            PortalSecondaryRenderState.renderingSecondaryView = false;

            modelViewStack.popMatrix();
            renderState.restore();
        }
    }

    public static void renderSecondaryViewDirectToCurrentTargetFromPose(
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            Matrix4f mainViewProjection,
            Matrix4f mainProjection,
            SkyesightClipPlane directClipPlane,
            boolean publishEntityWatchRegion,
            ResourceLocation entityWatchRegionId,
            boolean runEntityPass,
            String portalInstanceId,
            boolean renderSky,
            int portalStencilRef,
            int terrainChunkRadius,
            int portalOwnedRenderRadiusChunks,
            int sameDimPlayerLoadedReuseRadiusChunks,
            boolean reusePlayerLoadedChunksForSameDim,
            int entityChunkRadius,
            int blockEntityChunkRadius,
            int blockUpdateChunkRadius,
            boolean renderTerrain,
            boolean renderTranslucent,
            boolean renderEntities,
            boolean renderBlockEntities
    ) {
        TextureTarget placeholder = getOrCreatePortalRenderTarget(
                context,
                minecraft,
                mainViewProjection,
                entrancePortal,
                event.getCamera() == null ? null : event.getCamera().getPosition(),
                PortalProjectionConfig.VIEW_WIDTH,
                PortalProjectionConfig.VIEW_HEIGHT,
                entityWatchRegionId,
                null
        );
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousVertexSorting = RenderSystem.getVertexSorting();

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        PortalSecondaryRenderState.renderingSecondaryView = true;
        resetTargetBindDiagnostics();
        try {
            SecondaryViewFrame frame = createSecondaryViewFrameFromPose(
                    context,
                    minecraft,
                    event,
                    placeholder,
                    directProjectionViewportWidth(minecraft),
                    directProjectionViewportHeight(minecraft),
                    cameraPosition,
                    cameraRotation,
                    false,
                    null,
                    false,
                    directProjectionEntrancePortal(entrancePortal),
                    directProjectionExitPortal(exitPortal),
                    directClipPlane,
                    mainViewProjection,
                    mainProjection,
                    portalInstanceId,
                    portalStencilRef
            );
            frame.diagnostics().setPublishEntityWatchRegion(publishEntityWatchRegion);
            frame.diagnostics().setEntityWatchRegionId(entityWatchRegionId);
            frame.diagnostics().setRunEntityPass(runEntityPass);
            frame.diagnostics().setRenderToCurrentTarget(true);
            frame.diagnostics().setRenderSkyInCurrentTarget(renderSky);
            frame.diagnostics().setTerrainChunkRadius(terrainChunkRadius);
            frame.diagnostics().setPortalOwnedRenderRadiusChunks(portalOwnedRenderRadiusChunks);
            frame.diagnostics().setSameDimPlayerLoadedReuseRadiusChunks(sameDimPlayerLoadedReuseRadiusChunks);
            frame.diagnostics().setReusePlayerLoadedChunksForSameDim(reusePlayerLoadedChunksForSameDim);
            frame.diagnostics().setEntityChunkRadius(entityChunkRadius);
            frame.diagnostics().setBlockEntityChunkRadius(blockEntityChunkRadius);
            frame.diagnostics().setBlockUpdateChunkRadius(blockUpdateChunkRadius);
            frame.diagnostics().setRenderTerrain(renderTerrain);
            frame.diagnostics().setRenderTranslucent(renderTranslucent);
            frame.diagnostics().setRenderEntities(renderEntities);
            frame.diagnostics().setRenderBlockEntities(renderBlockEntities);
            RegisteredPortalView registeredView = entityWatchRegionId == null
                    ? null
                    : SkyesightPortalApi.getPortal(entityWatchRegionId.toString());
            frame.diagnostics().setRenderBackface(registeredView != null && registeredView.renderBackface());
            frame.diagnostics().setViewPhysicalSide("unknown");
            updateSecondaryChunkWatchRegionIfNeeded(minecraft, frame, context);
            if (renderTerrain) {
                DirectTerrainRenderState terrainState = DirectTerrainRenderState.capture();
                try {
                    try (SkyesightSecondaryRenderContext.Scope ignored =
                                 SkyesightSecondaryRenderContext.push(minecraft.getMainRenderTarget(), frame.camera(), minecraft.getMainRenderTarget())) {
                        if (SecondarySodiumTerrainPass.render(frame, context, minecraft, event)) {
                            renderPostTerrainFeatures(frame, context, minecraft, event.getPartialTick().getGameTimeDeltaPartialTick(true));
                        }
                    }
                } finally {
                    if (PortalSecondaryRenderState.directTerrainRestoreAfterEachPortal) {
                        terrainState.restore();
                    }
                }
            }
            PortalSecondaryRenderState.lastException = "";
        } finally {
            PortalSecondaryRenderState.renderingSecondaryView = false;

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousVertexSorting);

            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static SecondaryViewFrame createDirectPortalFrameForCapture(
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            SkyesightClipPlane directClipPlane,
            Matrix4f mainViewProjection,
            Matrix4f mainProjection,
            String portalInstanceId
    ) {
        TextureTarget placeholder = getOrCreatePortalRenderTarget(
                context,
                minecraft,
                mainViewProjection,
                entrancePortal,
                event.getCamera() == null ? null : event.getCamera().getPosition(),
                PortalProjectionConfig.VIEW_WIDTH,
                PortalProjectionConfig.VIEW_HEIGHT,
                null,
                null
        );
        return createSecondaryViewFrameFromPose(
                context,
                minecraft,
                event,
                placeholder,
                directProjectionViewportWidth(minecraft),
                directProjectionViewportHeight(minecraft),
                cameraPosition,
                cameraRotation,
                false,
                null,
                false,
                directProjectionEntrancePortal(entrancePortal),
                directProjectionExitPortal(exitPortal),
                directClipPlane,
                mainViewProjection,
                mainProjection,
                portalInstanceId
        );
    }

    private static PortalFrame directProjectionExitPortal(PortalFrame exitPortal) {
        return switch (PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE) {
            case NORMAL_MAIN_ASPECT -> null;
            case DIRECT_MAIN_PROJECTION, DIRECT_MAIN_PROJECTION_OBLIQUE_CLIP, DIRECT_OFF_AXIS_EXPERIMENTAL -> exitPortal;
            case PORTAL_OFF_AXIS_MAIN_ASPECT, PORTAL_OFF_AXIS_TARGET_ASPECT, MATCH_ENTRANCE_APERTURE,
                 OLD_PORTAL_PROJECTION_IN_PORTAL_VIEWPORT, LEGACY_CAMERA_AND_PROJECTION,
                 LEGACY_PROJECTIVE_EQUIVALENT -> exitPortal;
        };
    }

    private static PortalFrame directProjectionEntrancePortal(PortalFrame entrancePortal) {
        return PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.MATCH_ENTRANCE_APERTURE
                || PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.LEGACY_PROJECTIVE_EQUIVALENT
                ? entrancePortal
                : null;
    }

    private static int directProjectionViewportWidth(Minecraft minecraft) {
        return PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.PORTAL_OFF_AXIS_TARGET_ASPECT
                ? PortalProjectionConfig.VIEW_WIDTH
                : minecraft.getWindow().getWidth();
    }

    private static int directProjectionViewportHeight(Minecraft minecraft) {
        return PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.PORTAL_OFF_AXIS_TARGET_ASPECT
                ? PortalProjectionConfig.VIEW_HEIGHT
                : minecraft.getWindow().getHeight();
    }

    private static boolean isDirectStencilProjectionMode() {
        return PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.DIRECT_MAIN_PROJECTION
                || PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.DIRECT_MAIN_PROJECTION_OBLIQUE_CLIP
                || PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.DIRECT_OFF_AXIS_EXPERIMENTAL;
    }

    private static boolean directPortalProjectionUsesObliqueClip() {
        return PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.DIRECT_MAIN_PROJECTION_OBLIQUE_CLIP
                || PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.DIRECT_OFF_AXIS_EXPERIMENTAL
                || PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.LEGACY_CAMERA_AND_PROJECTION
                || PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.LEGACY_PROJECTIVE_EQUIVALENT;
    }

    private static SecondaryViewFrame createSecondaryViewFrameFromPose(
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event,
            TextureTarget output,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            boolean publishEntityWatchRegion,
            ResourceLocation entityWatchRegionId,
            boolean runEntityPass,
            PortalFrame exitPortal,
            SkyesightClipPlane clipPlane,
            String portalInstanceId
    ) {
        return createSecondaryViewFrameFromPose(
                context,
                minecraft,
                event,
                output,
                output.width,
                output.height,
                cameraPosition,
                cameraRotation,
                publishEntityWatchRegion,
                entityWatchRegionId,
                runEntityPass,
                null,
                exitPortal,
                clipPlane,
                null,
                null,
                portalInstanceId,
                0
        );
    }

    private static SecondaryViewFrame createSecondaryViewFrameFromPose(
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event,
            TextureTarget output,
            int viewportWidth,
            int viewportHeight,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            boolean publishEntityWatchRegion,
            ResourceLocation entityWatchRegionId,
            boolean runEntityPass,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            SkyesightClipPlane clipPlane,
            Matrix4f mainViewProjection,
            Matrix4f mainProjection,
            String portalInstanceId
    ) {
        return createSecondaryViewFrameFromPose(
                context,
                minecraft,
                event,
                output,
                viewportWidth,
                viewportHeight,
                cameraPosition,
                cameraRotation,
                publishEntityWatchRegion,
                entityWatchRegionId,
                runEntityPass,
                entrancePortal,
                exitPortal,
                clipPlane,
                mainViewProjection,
                mainProjection,
                portalInstanceId,
                0
        );
    }

    private static SecondaryViewFrame createSecondaryViewFrameFromPose(
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event,
            TextureTarget output,
            int viewportWidth,
            int viewportHeight,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            boolean publishEntityWatchRegion,
            ResourceLocation entityWatchRegionId,
            boolean runEntityPass,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            SkyesightClipPlane clipPlane,
            Matrix4f mainViewProjection,
            Matrix4f mainProjection,
            String portalInstanceId,
            int portalStencilRef
    ) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        return createSecondaryViewFrameFromPose(
                context,
                minecraft,
                partialTick,
                output,
                viewportWidth,
                viewportHeight,
                cameraPosition,
                cameraRotation,
                publishEntityWatchRegion,
                entityWatchRegionId,
                runEntityPass,
                entrancePortal,
                exitPortal,
                clipPlane,
                mainViewProjection,
                mainProjection,
                portalInstanceId,
                portalStencilRef,
                PortalProjectionConfig.VIEW_FOV,
                minecraft.options.getEffectiveRenderDistance()
        );
    }

    private static SecondaryViewFrame createSecondaryViewFrameFromPose(
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick,
            TextureTarget output,
            int viewportWidth,
            int viewportHeight,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            boolean publishEntityWatchRegion,
            ResourceLocation entityWatchRegionId,
            boolean runEntityPass,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            SkyesightClipPlane clipPlane,
            Matrix4f mainViewProjection,
            Matrix4f mainProjection,
            String portalInstanceId,
            int portalStencilRef,
            float fov,
            int renderDistanceChunks
    ) {
        var camera = context.camera();
        camera.setup(
                minecraft.level,
                minecraft.player,
                false,
                false,
                partialTick
        );
        camera.setPositionPublic(cameraPosition);
        camera.setRotationPublic(cameraRotation);

        ChunkPos chunkPos = new ChunkPos(BlockPos.containing(cameraPosition));
        context.setRemoteChunkCenter(chunkPos);

        float aspect = (float) viewportWidth / (float) viewportHeight;
        float nearPlane = 0.05F;
        float farPlane = Math.max(1, renderDistanceChunks) * 16.0F;
        Matrix4f normalProjection = SkyesightProjectionMatrices.perspective(
                Math.max(1.0F, fov),
                aspect,
                nearPlane,
                farPlane
        );
        boolean directStencilProjection = mainProjection != null && isDirectStencilProjectionMode();
        SecondaryProjectionMode projectionMode = exitPortal == null || directStencilProjection
                ? SecondaryProjectionMode.NORMAL_PERSPECTIVE
                : PortalProjectionConfig.SECONDARY_PROJECTION_MODE;
        boolean matchEntranceAperture = false;
        boolean directPortalProjectionUsedFrameMath = false;
        Matrix4f projection;

        if (directStencilProjection) {
            projection = new Matrix4f(mainProjection);
        } else if (projectionMode == SecondaryProjectionMode.PORTAL_OFF_AXIS) {
            directPortalProjectionUsedFrameMath = true;
            projection = PortalFrameMath.portalProjection(cameraPosition, exitPortal, nearPlane, farPlane);
        } else {
            projection = new Matrix4f(normalProjection);
        }
        Matrix4f cullProjectionBase = new Matrix4f(projection);
        if (clipPlane != null && directPortalProjectionUsesObliqueClip()) {
            projection = SkyesightProjectionMatrices.applyObliqueClipPlane(
                    projection,
                    camera,
                    clipPlane
            );
        }
        if (PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE == DirectPortalProjectionMode.LEGACY_PROJECTIVE_EQUIVALENT
                && entrancePortal != null
                && exitPortal != null
                && mainViewProjection != null
                && directPortalProjectionUsedFrameMath) {
            try {
                DirectPortalProjectionMath.Result projectiveResult =
                        DirectPortalProjectionMath.legacyProjectiveEquivalent(
                                projection,
                                mainViewProjection,
                                entrancePortal,
                                cameraPosition,
                                exitPortal
                        );
                if (projectiveResult.valid()) {
                    projection = projectiveResult.correctedProjection();
                }
            } catch (RuntimeException exception) {
            }
        }
        Matrix4f cullProjection = directStencilProjection
                ? new Matrix4f(normalProjection)
                : projectionMode == SecondaryProjectionMode.PORTAL_OFF_AXIS
                        && (PortalProjectionConfig.PORTAL_OFF_AXIS_CULL_USES_NORMAL_PERSPECTIVE || matchEntranceAperture)
                ? new Matrix4f(normalProjection)
                : cullProjectionBase;
        Matrix4f modelView = SkyesightCameraMatrices.createModelView(camera);
        Frustum frustum = new Frustum(modelView, cullProjection);
        frustum.prepare(
                camera.getPosition().x(),
                camera.getPosition().y(),
                camera.getPosition().z()
        );

        SecondaryViewFrame frame = new SecondaryViewFrame(
                camera,
                output,
                viewportWidth,
                viewportHeight,
                projection,
                modelView,
                cullProjection,
                frustum
        );

        String projectionModeName = directStencilProjection
                ? PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE.name()
                : matchEntranceAperture
                ? PortalProjectionConfig.DIRECT_PORTAL_PROJECTION_MODE.name()
                : projectionMode.name();
        String cullProjectionMode = directStencilProjection
                ? "normal/wide"
                : projectionMode == SecondaryProjectionMode.PORTAL_OFF_AXIS
                        && (PortalProjectionConfig.PORTAL_OFF_AXIS_CULL_USES_NORMAL_PERSPECTIVE || matchEntranceAperture)
                ? "normal"
                : projectionMode.name();
        frame.diagnostics().setProjectionMode(projectionModeName);
        frame.diagnostics().setProjectionNearPlane(nearPlane);
        frame.diagnostics().setProjectionFarPlane(farPlane);
        frame.diagnostics().setProjectionSummary("-");
        frame.diagnostics().setCullProjectionSummary("-");
        frame.diagnostics().setCullProjectionMode(cullProjectionMode);
        frame.diagnostics().setPortalInstanceId(portalInstanceId);
        frame.diagnostics().setPortalStencilRef(portalStencilRef);
        frame.diagnostics().setPublishEntityWatchRegion(publishEntityWatchRegion);
        frame.diagnostics().setEntityWatchRegionId(entityWatchRegionId);
        frame.diagnostics().setRunEntityPass(runEntityPass);
        return frame;
    }

    private static void resetTargetBindDiagnostics() {
        secondaryContextNonSecondaryTargetBindCount = 0;
        secondaryContextLastNonSecondaryBind = "n/a";
    }

    public static void recordSecondaryContextTargetBind(RenderTarget target) {
        if (!SkyesightSecondaryRenderContext.isActive() || target == null) {
            return;
        }

        RenderTarget secondaryTarget = SkyesightSecondaryRenderContext.currentTarget();
        RenderTarget mainTarget = SkyesightSecondaryRenderContext.mainTarget();

        if (target == secondaryTarget) {
            return;
        }

        secondaryContextNonSecondaryTargetBindCount++;
        secondaryContextLastNonSecondaryBind = String.format(
                Locale.ROOT,
                "%s fb=%d %dx%d view=%dx%d id=%08x",
                target == mainTarget ? "main" : "other",
                target.frameBufferId,
                target.width,
                target.height,
                target.viewWidth,
                target.viewHeight,
                System.identityHashCode(target)
        );
    }

    private static Matrix4f createGameRendererProjection(
            GameRenderer gameRenderer,
            double fov,
            float aspect
    ) {
        return new Matrix4f().perspective(
                (float) (fov * (float) (Math.PI / 180.0)),
                aspect,
                0.05F,
                gameRenderer.getDepthFar()
        );
    }

    private static void applyVanillaConfusionProjectionIfNeeded(
            Minecraft minecraft,
            Matrix4f projection,
            float partialTick
    ) {
        if (minecraft.player == null) {
            return;
        }

        float screenEffectScale = minecraft.options.screenEffectScale().get().floatValue();
        float spinningIntensity = Mth.lerp(
                partialTick,
                minecraft.player.oSpinningEffectIntensity,
                minecraft.player.spinningEffectIntensity
        ) * screenEffectScale * screenEffectScale;

        if (spinningIntensity <= 0.0F) {
            return;
        }

        int effectSpeed = minecraft.player.hasEffect(MobEffects.CONFUSION) ? 7 : 20;
        float squash = 5.0F / (spinningIntensity * spinningIntensity + 5.0F)
                - spinningIntensity * 0.04F;
        squash *= squash;

        Vector3f axis = new Vector3f(
                0.0F,
                Mth.SQRT_OF_TWO / 2.0F,
                Mth.SQRT_OF_TWO / 2.0F
        );
        int confusionAnimationTick =
                ((GameRendererSetupInvoker) minecraft.gameRenderer).skyesight$getConfusionAnimationTick();
        float angle = ((float) confusionAnimationTick + partialTick)
                * (float) effectSpeed
                * (float) (Math.PI / 180.0);

        projection.rotate(angle, axis);
        projection.scale(1.0F / squash, 1.0F, 1.0F);
        projection.rotate(-angle, axis);
    }

    static void renderDirectBlockEntitiesIfEnabled(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick
    ) {
        if (!PortalSecondaryRenderConfig.DIRECT_RENDER_BLOCK_ENTITIES) {
            SecondaryBlockEntityPass.markSkipped("feature disabled");
            return;
        }

        if (frame == null) {
            SecondaryBlockEntityPass.markSkipped("frame null at call site");
            return;
        }

        if (context == null) {
            SecondaryBlockEntityPass.markSkipped("context null at call site");
            return;
        }

        if (!frame.diagnostics().renderToCurrentTarget()) {
            SecondaryBlockEntityPass.markSkipped("not direct current-target frame");
            return;
        }

        if (frame.diagnostics().portalInstanceId().contains("direct:C")
                && !PortalRenderDebugStatus.farPortalRenderBlockEntities()) {
            SecondaryBlockEntityPass.markSkipped("far portal block entities disabled");
            return;
        }

        try {
            int stencilRef = fallbackStencilRefForLegacyDebugFrame(frame);
            int stencilBits = PortalRenderDebugStatus.stencilBits();
            SecondaryPortalCompositePass.StencilResult stencil =
                    SecondaryPortalCompositePass.beginExistingStencilApertureRead(stencilBits, stencilRef);
            if (!stencil.succeeded()) {
                SecondaryBlockEntityPass.markSkipped("stencil inactive " + stencil.exception());
                return;
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);

            ChunkPos center = new ChunkPos(secondaryRemoteCenterBlockPos(context, frame));
            SecondaryBlockEntityPass.render(
                    frame,
                    minecraft,
                    center,
                    configuredBlockEntityChunkRadius(frame),
                    partialTick
            );
        } catch (RuntimeException exception) {
            SecondaryBlockEntityPass.markSkipped("exception " + exception.getClass().getSimpleName());
        } finally {
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    static void renderPostTerrainFeatures(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick
    ) {
        if (frame == null || context == null || minecraft == null) {
            return;
        }

        if (frame.diagnostics().renderToCurrentTarget()) {
            if (!PortalSecondaryRenderConfig.DIRECT_RENDER_TERRAIN_DRAW_SOLID_ONLY) {
                return;
            }

            renderDirectBlockEntitiesIfEnabled(frame, context, minecraft, partialTick);
            if (!PortalSecondaryRenderConfig.PORTAL_PARTICLES_ALL_AFTER_ENTITIES
                    && !PortalSecondaryRenderConfig.PORTAL_PARTICLES_AFTER_ENTITIES) {
                renderSecondaryParticlesIfEnabled(
                        frame,
                        minecraft,
                        partialTick,
                        SecondaryParticlePass.RenderGroup.OPAQUE
                );
            }
            renderSecondaryEntitiesIfEnabled(frame, context, minecraft, partialTick);
            if (PortalSecondaryRenderConfig.PORTAL_PARTICLES_ALL_AFTER_ENTITIES) {
                renderSecondaryParticlesIfEnabled(
                        frame,
                        minecraft,
                        partialTick,
                        SecondaryParticlePass.RenderGroup.ALL
                );
            } else {
                renderSecondaryParticlesIfEnabled(
                        frame,
                        minecraft,
                        partialTick,
                        SecondaryParticlePass.RenderGroup.TRANSLUCENT
                );
            }
            return;
        }

        if (!PortalSecondaryRenderConfig.SECONDARY_RENDER_ENTITIES_AFTER_TRANSLUCENT) {
            renderSecondaryBlockEntitiesIfEnabled(frame, context, minecraft, partialTick);
            renderSecondaryEntitiesIfEnabled(frame, context, minecraft, partialTick);
        }

        if (PortalSecondaryRenderConfig.SECONDARY_RENDER_ENTITIES_AFTER_TRANSLUCENT) {
            renderSecondaryBlockEntitiesIfEnabled(frame, context, minecraft, partialTick);
            renderSecondaryEntitiesIfEnabled(frame, context, minecraft, partialTick);
        }

        renderSecondaryParticlesIfEnabled(frame, minecraft, partialTick);
    }

    private static int fallbackStencilRefForLegacyDebugFrame(SecondaryViewFrame frame) {
        String portalId = frame == null ? "" : frame.diagnostics().portalInstanceId();
        int explicitRef = frame == null ? 0 : frame.diagnostics().portalStencilRef();
        if (explicitRef > 0) {
            return explicitRef;
        }

        // Legacy manual/debug frames may only carry direct-render labels, so keep their historical stencil mapping.
        int fallbackRef;
        if (portalId.contains("direct:D")) {
            fallbackRef = 4;
        } else if (portalId.contains("direct:C")) {
            fallbackRef = 3;
        } else if (portalId.contains("direct:B")) {
            fallbackRef = 2;
        } else {
            fallbackRef = 1;
        }

        return fallbackRef;
    }

    static void applyDirectDepthModeAtSodiumDrawPoint() {
        String mode = PortalRenderDebugStatus.directPortalDepthMode();

        switch (mode) {
            case "DISABLE_DEPTH_TEST" -> {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            }
            case "ALWAYS_NO_WRITE" -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_ALWAYS);
                RenderSystem.depthMask(false);
            }
            case "CLEAR_PORTAL_DEPTH_THEN_LEQUAL", "RESPECT_MAIN_DEPTH" -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
            }
            default -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
            }
        }
    }

    static void renderSecondarySkyIfEnabled(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick
    ) {
        secondaryEntityPassAttempted = false;
        if (!PortalSecondaryRenderConfig.SECONDARY_FEATURE_SKY || !frame.diagnostics().renderSky() || minecraft.level == null) {
            return;
        }

        try {
            SecondarySkyPass.render(frame, minecraft, partialTick);
        } catch (RuntimeException exception) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Secondary sky render failed",
                    exception
            );
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    static void resetSecondaryFeatureDiagnosticsForDirectRender() {
        secondaryEntityPassAttempted = false;
    }

    static void renderSecondaryBlockEntitiesIfEnabled(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick
    ) {
        if (!PortalSecondaryRenderConfig.SECONDARY_FEATURE_BLOCK_ENTITIES) {
            return;
        }

        if (!frame.diagnostics().renderBlockEntities()) {
            return;
        }

        ResourceLocation viewId = frame.diagnostics().entityWatchRegionId();
        long auditStart = PortalRenderCostAudit.start();
        ChunkPos center = new ChunkPos(secondaryRemoteCenterBlockPos(context, frame));
        SecondaryBlockEntityPass.render(
                frame,
                minecraft,
                center,
                configuredBlockEntityChunkRadius(frame),
                partialTick
        );

        PortalRenderCostAudit.record(viewId, "blockEntities", auditStart);
    }

    static BlockPos secondaryRemoteCenterBlockPos(SecondaryViewContext context, SecondaryViewFrame frame) {
        Vec3 frozenCenter = context.frozenRemoteCameraPosition();
        return BlockPos.containing(frozenCenter == null ? frame.camera().getPosition() : frozenCenter);
    }

    private static Vec3 secondaryEntityCenter(SecondaryViewContext context, SecondaryViewFrame frame) {
        BlockPos center = secondaryRemoteCenterBlockPos(context, frame);
        return new Vec3(
                center.getX() + 0.5D,
                frame.camera().getPosition().y(),
                center.getZ() + 0.5D
        );
    }

    static void renderSecondaryEntitiesIfEnabled(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
        float partialTick
    ) {
        if (!PortalSecondaryRenderConfig.SECONDARY_FEATURE_ENTITIES || !frame.diagnostics().runEntityPass() || !frame.diagnostics().renderEntities()) {
            if (frame.diagnostics().publishEntityWatchRegion()) {
                removeSecondaryWatchRegion(minecraft, frame.diagnostics().entityWatchRegionId());
            }
            return;
        }

        ResourceLocation viewId = frame.diagnostics().entityWatchRegionId();
        long auditStart = PortalRenderCostAudit.start();
        updateRemoteEntityTrackingIfEnabled(frame, context, minecraft);
        Vec3 entityCenter = secondaryEntityCenter(context, frame);

        SecondaryEntityPass.Result result = SecondaryEntityPass.render(
                frame,
                context.remoteEntityTracker(),
                minecraft,
                entityCenter,
                configuredEntityChunkRadius(frame) * 16.0D,
                partialTick
        );

        secondaryEntityPassAttempted = result.attempted();
        PortalRenderCostAudit.record(viewId, "entities", auditStart);
        PortalRenderCostAudit.recordEntityCounts(
                viewId,
                result.considered(),
                result.rendered(),
                result.skippedOutsideBounds()
                        + result.skippedDistance()
                        + result.skippedFrustum()
                        + result.duplicateSuppressed()
        );
    }

    private static void updateRemoteEntityTrackingIfEnabled(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft
    ) {
        if (!PortalSecondaryRenderConfig.SECONDARY_STREAM_REMOTE_ENTITIES) {
            if (frame.diagnostics().publishEntityWatchRegion()) {
                removeSecondaryWatchRegion(minecraft, frame.diagnostics().entityWatchRegionId());
            }
            return;
        }
        Vec3 entityCenter = secondaryEntityCenter(context, frame);

        if (frame.diagnostics().publishEntityWatchRegion()) {
            updateSecondaryWatchRegion(
                    minecraft,
                    frame.diagnostics().entityWatchRegionId(),
                    entityCenter,
                    configuredEntityChunkRadius(frame) * 16.0D
            );
        }
    }

    private static void updateSecondaryWatchRegion(
            Minecraft minecraft,
            ResourceLocation regionId,
            Vec3 center,
            double radius
    ) {
        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null || minecraft.player == null || minecraft.level == null || regionId == null) {
            return;
        }

        UUID playerId = minecraft.player.getUUID();
        var dimension = minecraft.level.dimension();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player != null && !player.isRemoved()) {
                SkyesightSecondaryWatchRegion.setRegion(player, regionId, dimension, center, radius);
            }
        });
    }

    private static void updateSecondaryChunkWatchRegionIfNeeded(
            Minecraft minecraft,
            SecondaryViewFrame frame,
            SecondaryViewContext context
    ) {
        ResourceLocation regionId = frame.diagnostics().entityWatchRegionId();

        if (regionId == null) {
            return;
        }

        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        BlockPos centerBlock = secondaryRemoteCenterBlockPos(context, frame);
        ChunkPos centerChunk = new ChunkPos(centerBlock);
        int terrainChunkRadius = configuredTerrainChunkRadius(frame);
        if (!PortalRemoteChunkConfig.DIRECT_DISABLE_REMOTE_CLIENT_CACHE_EXPANSION) {
            updateClientChunkCacheExpansion(minecraft, centerChunk, terrainChunkRadius);
        }
        UUID playerId = minecraft.player.getUUID();
        var dimension = minecraft.level.dimension();
        ChunkPos lastSentCenter = PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_CENTERS.get(regionId);
        Integer lastSentRadius = PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_RADII.get(regionId);
        boolean centerChanged = !centerChunk.equals(lastSentCenter);
        boolean radiusChanged = lastSentRadius == null || lastSentRadius != terrainChunkRadius;

        if (centerChanged || radiusChanged) {
            PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_CENTERS.put(regionId, centerChunk);
            PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_RADII.put(regionId, terrainChunkRadius);
        }

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player != null && !player.isRemoved()) {
                SkyesightSecondaryChunkWatchRegion.setRegion(
                        player,
                        regionId,
                        dimension,
                        centerChunk,
                        terrainChunkRadius
                );
                if (PortalRemoteChunkConfig.FORCE_SEND_PORTAL_WATCH_CHUNKS_ON_CHANGE && (centerChanged || radiusChanged)) {
                    ServerLevel serverLevel = server.getLevel(dimension);

                    if (serverLevel != null) {
                        PortalRemoteChunkController.sendSecondaryWatchChunksToLocalClient(
                                server,
                                serverLevel,
                                player,
                                regionId,
                                centerChunk,
                                terrainChunkRadius
                        );
                    }
                }
            }
        });
    }

    static int configuredTerrainChunkRadius(SecondaryViewFrame frame) {
        return frame == null || frame.diagnostics().terrainChunkRadius() <= 0
                ? PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS
                : frame.diagnostics().terrainChunkRadius();
    }

    static int configuredPortalOwnedRenderRadiusChunks(SecondaryViewFrame frame) {
        return frame == null || frame.diagnostics().portalOwnedRenderRadiusChunks() <= 0
                ? configuredTerrainChunkRadius(frame)
                : frame.diagnostics().portalOwnedRenderRadiusChunks();
    }

    static int configuredSameDimPlayerLoadedReuseRadiusChunks(SecondaryViewFrame frame) {
        return frame == null || frame.diagnostics().sameDimPlayerLoadedReuseRadiusChunks() <= 0
                ? PortalRenderSettings.DEFAULT_SAME_DIM_PLAYER_LOADED_REUSE_RADIUS_CHUNKS
                : frame.diagnostics().sameDimPlayerLoadedReuseRadiusChunks();
    }

    static boolean configuredReusePlayerLoadedChunksForSameDim(SecondaryViewFrame frame) {
        return frame != null && frame.diagnostics().reusePlayerLoadedChunksForSameDim();
    }

    static boolean shouldSkipNewPortalTerrainWarmup(SecondaryViewFrame frame, SecondaryViewContext context) {
        return frame != null
                && context != null
                && frame.diagnostics().renderToCurrentTarget()
                && PortalSodiumRenderConfig.DEFAULT_NEW_PORTAL_TERRAIN_SKIP_FRAMES > 0
                && context.firstVisibleTerrainFramesSkipped() < PortalSodiumRenderConfig.DEFAULT_NEW_PORTAL_TERRAIN_SKIP_FRAMES;
    }

    static int configuredSameDimRenderChunkRadius(SecondaryViewFrame frame) {
        int ownedRadius = configuredPortalOwnedRenderRadiusChunks(frame);
        if (!configuredReusePlayerLoadedChunksForSameDim(frame)) {
            return ownedRadius;
        }
        return Math.max(ownedRadius, configuredSameDimPlayerLoadedReuseRadiusChunks(frame));
    }




    private static int configuredEntityChunkRadius(SecondaryViewFrame frame) {
        return frame == null || frame.diagnostics().entityChunkRadius() <= 0
                ? PortalSodiumRenderConfig.SODIUM_CHUNK_RADIUS
                : frame.diagnostics().entityChunkRadius();
    }

    private static int configuredBlockEntityChunkRadius(SecondaryViewFrame frame) {
        int configured = frame == null || frame.diagnostics().blockEntityChunkRadius() <= 0
                ? PortalSodiumRenderConfig.SODIUM_CHUNK_RADIUS
                : frame.diagnostics().blockEntityChunkRadius();
        if (!configuredReusePlayerLoadedChunksForSameDim(frame)) {
            return configured;
        }
        return Math.max(configured, configuredSameDimPlayerLoadedReuseRadiusChunks(frame));
    }

    private record PortalTargetRenderState(
            int framebuffer,
            int[] viewport,
            boolean scissorEnabled,
            int[] scissorBox,
            Matrix4f projection,
            VertexSorting vertexSorting,
            Matrix4f modelView,
            boolean depthEnabled,
            int depthFunc,
            boolean depthMask,
            boolean stencilEnabled,
            int stencilFunc,
            int stencilRef,
            int stencilValueMask,
            int stencilWriteMask,
            boolean blendEnabled,
            boolean cullEnabled,
            boolean[] colorMask,
            float[] shaderColor,
            ShaderInstance shader
    ) {
        private static PortalTargetRenderState capture() {
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int[] scissorBox = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            return new PortalTargetRenderState(
                    GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                    viewport,
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    scissorBox,
                    new Matrix4f(RenderSystem.getProjectionMatrix()),
                    RenderSystem.getVertexSorting(),
                    new Matrix4f(RenderSystem.getModelViewStack()),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                    GL11.glGetInteger(GL11.GL_STENCIL_FUNC),
                    GL11.glGetInteger(GL11.GL_STENCIL_REF),
                    GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK),
                    GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    readColorMask(),
                    RenderSystem.getShaderColor().clone(),
                    RenderSystem.getShader()
            );
        }

        private void restore() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
            RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
            if (this.scissorEnabled) {
                RenderSystem.enableScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
            } else {
                RenderSystem.disableScissor();
            }
            RenderSystem.setProjectionMatrix(this.projection, this.vertexSorting);
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.identity();
            modelViewStack.mul(this.modelView);
            RenderSystem.applyModelViewMatrix();
            if (this.depthEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthFunc(this.depthFunc);
            RenderSystem.depthMask(this.depthMask);
            RenderSystem.colorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
            if (this.stencilEnabled) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            RenderSystem.stencilMask(this.stencilWriteMask);
            RenderSystem.stencilFunc(this.stencilFunc, this.stencilRef, this.stencilValueMask);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            if (this.blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (this.cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.setShaderColor(this.shaderColor[0], this.shaderColor[1], this.shaderColor[2], this.shaderColor[3]);
            if (this.shader != null) {
                RenderSystem.setShader(() -> this.shader);
            }
        }

        private String viewportSummary() {
            return this.viewport[0] + "," + this.viewport[1] + "," + this.viewport[2] + "," + this.viewport[3];
        }

        private static boolean[] readColorMask() {
            ByteBuffer buffer = ByteBuffer.allocateDirect(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
            return new boolean[] {
                    buffer.get(0) != 0,
                    buffer.get(1) != 0,
                    buffer.get(2) != 0,
                    buffer.get(3) != 0
            };
        }
    }

    private record DirectTerrainRenderState(
            int framebuffer,
            int[] viewport,
            Matrix4f projection,
            VertexSorting vertexSorting,
            Matrix4f modelView,
            boolean depthEnabled,
            int depthFunc,
            boolean depthMask,
            boolean stencilEnabled,
            int stencilFunc,
            int stencilRef,
            int stencilValueMask,
            int stencilWriteMask,
            boolean blendEnabled,
            boolean cullEnabled,
            float[] shaderColor,
            ShaderInstance shader
    ) {
        private static DirectTerrainRenderState capture() {
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

            return new DirectTerrainRenderState(
                    GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                    viewport,
                    new Matrix4f(RenderSystem.getProjectionMatrix()),
                    RenderSystem.getVertexSorting(),
                    new Matrix4f(RenderSystem.getModelViewStack()),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                    GL11.glGetInteger(GL11.GL_STENCIL_FUNC),
                    GL11.glGetInteger(GL11.GL_STENCIL_REF),
                    GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK),
                    GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    RenderSystem.getShaderColor().clone(),
                    RenderSystem.getShader()
            );
        }

        private void restore() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
            RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
            RenderSystem.setProjectionMatrix(this.projection, this.vertexSorting);

            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.identity();
            modelViewStack.mul(this.modelView);
            RenderSystem.applyModelViewMatrix();

            if (this.depthEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthFunc(this.depthFunc);
            RenderSystem.depthMask(this.depthMask);
            RenderSystem.colorMask(true, true, true, true);

            if (this.stencilEnabled) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            RenderSystem.stencilMask(this.stencilWriteMask);
            RenderSystem.stencilFunc(this.stencilFunc, this.stencilRef, this.stencilValueMask);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            if (this.blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }

            if (this.cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (this.shader != null) {
                RenderSystem.setShader(() -> this.shader);
            }
        }


    }

    public static void requestCrossDimensionPortalChunks(
            Minecraft minecraft,
            String portalName,
            ResourceLocation regionId,
            ResourceKey<Level> sourceDimension,
            ResourceKey<Level> targetDimension,
            BlockPos cameraBlock,
            ChunkPos center,
            int radius
    ) {
        PortalRemoteChunkController.requestCrossDimensionPortalChunks(
                minecraft,
                portalName,
                regionId,
                sourceDimension,
                targetDimension,
                cameraBlock,
                center,
                radius
        );
    }

    private static void removeSecondaryWatchRegion(Minecraft minecraft, ResourceLocation regionId) {
        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null || minecraft.player == null || regionId == null) {
            return;
        }

        UUID playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            SkyesightSecondaryWatchRegion.removeRegion(player, regionId);
            SkyesightSecondaryChunkWatchRegion.removeRegion(player, regionId);
        });
    }

    static void renderSecondaryParticlesIfEnabled(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick
    ) {
        renderSecondaryParticlesIfEnabled(frame, minecraft, partialTick, SecondaryParticlePass.RenderGroup.ALL);
    }

    static void renderSecondaryParticlesIfEnabled(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick,
            SecondaryParticlePass.RenderGroup renderGroup
    ) {
        if (!PortalSecondaryRenderConfig.SECONDARY_RENDER_PARTICLES) {
            return;
        }
        if (!frame.diagnostics().renderParticles()) {
            return;
        }
        if (PortalSecondaryRenderConfig.PORTAL_PARTICLES_SAME_DIM_ONLY && minecraft.level == null) {
            return;
        }

        ResourceLocation viewId = frame.diagnostics().entityWatchRegionId();
        long auditStart = PortalRenderCostAudit.start();
        PortalVisualDisplayTickDriver.tick(
                frame.diagnostics().entityWatchRegionId(),
                sameDimPortalDisplayTickKind(frame, minecraft),
                minecraft.level,
                null,
                frame.camera().getPosition()
        );
        SecondaryParticlePass.Result result = SecondaryParticlePass.render(frame, minecraft, partialTick, renderGroup);
        PortalRenderCostAudit.record(viewId, "particles", auditStart);
    }

    private static String sameDimPortalDisplayTickKind(SecondaryViewFrame frame, Minecraft minecraft) {
        if (frame == null || frame.camera() == null || minecraft == null || minecraft.gameRenderer == null) {
            return "same-dim";
        }

        Vec3 realCameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        double distance = realCameraPos.distanceTo(frame.camera().getPosition());
        return distance <= 32.0D ? "near-same-dim" : "far-same-dim";
    }

    public static boolean sodiumForceRemoteRenderListEnabled() {
        return PortalSodiumRenderConfig.SODIUM_FORCE_REMOTE_RENDER_LIST
                && PortalSodiumRenderConfig.SODIUM_FORCE_RENDER_LIST_FROM_REMOTE_GEOMETRY;
    }

    public static int sodiumForceRemoteRenderListRadius() {
        return PortalSecondaryRenderState.activeRemoteTerrainChunkRadius;
    }

    public static int activeRemoteTerrainChunkRadius() {
        return PortalSecondaryRenderState.activeRemoteTerrainChunkRadius;
    }

    private static void updateClientChunkCacheExpansion(
            Minecraft minecraft,
            ChunkPos remoteChunkPos
    ) {
        updateClientChunkCacheExpansion(minecraft, remoteChunkPos, PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS);
    }

    private static void updateClientChunkCacheExpansion(
            Minecraft minecraft,
            ChunkPos remoteChunkPos,
            int remoteChunkRadius
    ) {
        if (minecraft.level == null || minecraft.player == null) {
            restoreClientChunkCacheRadius();
            return;
        }

        if (!PortalRemoteChunkConfig.EXPAND_CLIENT_CACHE_FOR_REMOTE_CHUNKS) {
            restoreClientChunkCacheRadius();
            return;
        }

        if (PortalSecondaryRenderState.remoteClientCacheExpandedLevel != null && PortalSecondaryRenderState.remoteClientCacheExpandedLevel != minecraft.level) {
            restoreClientChunkCacheRadius();
        }

        int playerRenderDistance = minecraft.options.getEffectiveRenderDistance();
        int playerChunkX = minecraft.player.chunkPosition().x;
        int playerChunkZ = minecraft.player.chunkPosition().z;
        int chunkDistance = Math.max(
                Math.abs(remoteChunkPos.x - playerChunkX),
                Math.abs(remoteChunkPos.z - playerChunkZ)
        );
        int requiredRadius = Math.max(
                playerRenderDistance,
                chunkDistance + Math.max(0, remoteChunkRadius) + 1
        );

        if (!PortalSecondaryRenderState.remoteClientCacheExpanded || PortalSecondaryRenderState.remoteClientCacheExpandedLevel != minecraft.level) {
            PortalSecondaryRenderState.remoteClientCacheOriginalRadius = playerRenderDistance;
            PortalSecondaryRenderState.remoteClientCacheExpandedLevel = minecraft.level;
        }

        if (PortalSecondaryRenderState.remoteClientCacheExpanded
                && PortalSecondaryRenderState.remoteClientCacheExpandedLevel == minecraft.level
                && requiredRadius <= PortalSecondaryRenderState.remoteClientCacheExpandedRadius) {
            return;
        }

        minecraft.level.getChunkSource().updateViewRadius(requiredRadius);
        PortalSecondaryRenderState.remoteClientCacheExpanded = true;
        PortalSecondaryRenderState.remoteClientCacheExpandedRadius = requiredRadius;
    }

    private static void restoreClientChunkCacheRadius() {
        if (!PortalSecondaryRenderState.remoteClientCacheExpanded || PortalSecondaryRenderState.remoteClientCacheExpandedLevel == null) {
            return;
        }

        if (PortalSecondaryRenderState.remoteClientCacheOriginalRadius > 0) {
            PortalSecondaryRenderState.remoteClientCacheExpandedLevel.getChunkSource().updateViewRadius(PortalSecondaryRenderState.remoteClientCacheOriginalRadius);
        }

        PortalSecondaryRenderState.remoteClientCacheExpanded = false;
        PortalSecondaryRenderState.remoteClientCacheExpandedLevel = null;
        PortalSecondaryRenderState.remoteClientCacheExpandedRadius = -1;
        PortalSecondaryRenderState.remoteClientCacheOriginalRadius = -1;
    }

    public static boolean shaderPackActive() {
        return SkyesightIrisCompat.isShaderPackInUse();
    }

    public static int secondaryContextNonSecondaryTargetBindCount() {
        return secondaryContextNonSecondaryTargetBindCount;
    }

    public static String secondaryContextLastNonSecondaryBind() {
        return secondaryContextLastNonSecondaryBind;
    }

    public static boolean secondaryEntitiesEnabledForPostUpdate() {
        return false;
    }

    public static boolean secondaryEntityPassAttempted() {
        return secondaryEntityPassAttempted;
    }

    public static float directPortalProjectionFov() {
        return PortalProjectionConfig.VIEW_FOV;
    }

    public static String directPortalProjectionHandedness() {
        return "COMMITTED_FLIPPED_RIGHT";
    }

    public enum SecondaryProjectionMode {
        NORMAL_PERSPECTIVE,
        PORTAL_OFF_AXIS
    }

    public enum DirectPortalProjectionMode {
        DIRECT_MAIN_PROJECTION,
        DIRECT_MAIN_PROJECTION_OBLIQUE_CLIP,
        DIRECT_OFF_AXIS_EXPERIMENTAL,
        NORMAL_MAIN_ASPECT,
        PORTAL_OFF_AXIS_MAIN_ASPECT,
        PORTAL_OFF_AXIS_TARGET_ASPECT,
        MATCH_ENTRANCE_APERTURE,
        OLD_PORTAL_PROJECTION_IN_PORTAL_VIEWPORT,
        LEGACY_CAMERA_AND_PROJECTION,
        LEGACY_PROJECTIVE_EQUIVALENT
    }

    public enum PortalProjectionHandednessMode {
        ORIGINAL_FLIPPED_RIGHT,
        UNFLIPPED_RIGHT
    }

}



