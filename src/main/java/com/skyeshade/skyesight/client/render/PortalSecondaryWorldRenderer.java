package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
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
import com.skyeshade.skyesight.client.render.sodium.SkyesightSodiumRenderContext;
import com.skyeshade.skyesight.client.render.sodium.SameDimMainSodiumSectionReuse;
import com.skyeshade.skyesight.client.render.sodium.SameDimPortalTerrainPrimer;
import com.skyeshade.skyesight.client.render.sodium.PortalSodiumRendererPool;
import com.skyeshade.skyesight.client.render.state.PortalSecondaryRenderState;
import com.skyeshade.skyesight.client.render.state.PortalSodiumReflectionState;
import com.skyeshade.skyesight.client.render.state.PortalRemoteChunkRuntimeState;
import com.skyeshade.skyesight.mixin.client.CameraInvoker;
import com.skyeshade.skyesight.mixin.client.GameRendererSetupInvoker;
import com.skyeshade.skyesight.mixin.client.GameRendererStateAccessor;
import com.skyeshade.skyesight.server.SkyesightSecondaryWatchRegion;
import com.skyeshade.skyesight.server.SkyesightSecondaryChunkWatchRegion;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
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
                    "[Skyesight] PORTAL_TARGET_REDUNDANT_RESIZE_BUG view={} previous={}x{} requested={}x{} actualAfter={}x{} stage=getOrCreatePortalRenderTarget",
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
            SecondarySodiumTerrainPass.render(frame, context, minecraft, event);

            return output;
        } finally {
            recordSecondarySharedStateAfter(minecraft);
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
        recordSecondarySharedStateBefore(minecraft);
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
                        SecondarySodiumTerrainPass.render(frame, context, minecraft, event);
                    }
                } finally {
                    if (PortalSecondaryRenderState.directTerrainRestoreAfterEachPortal) {
                        terrainState.restore();
                    }
                }
            }
            PortalSecondaryRenderState.lastException = "";
        } finally {
            recordSecondarySharedStateAfter(minecraft);
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


    private static void recordSecondarySharedStateBefore(Minecraft minecraft) {
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



    private static void recordSecondarySharedStateAfter(Minecraft minecraft) {
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
        float farPlane = minecraft.options.getEffectiveRenderDistance() * 16.0F;
        Matrix4f normalProjection = SkyesightProjectionMatrices.perspective(
                PortalProjectionConfig.VIEW_FOV,
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

    private static void renderFullGameRendererBackend(
            Minecraft minecraft,
            RenderLevelStageEvent event,
            TextureTarget output
    ) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera camera = configureSecondaryCamera(minecraft, partialTick);
        GameRenderer gameRenderer = minecraft.gameRenderer;
        GameRendererStateAccessor gameRendererState = (GameRendererStateAccessor) gameRenderer;
        Camera actualMainCamera = gameRendererState.skyesight$getMainCameraField();

        boolean previousRenderHand = gameRendererState.skyesight$getRenderHand();
        boolean previousRenderBlockOutline = gameRendererState.skyesight$getRenderBlockOutline();
        HitResult previousHitResult = minecraft.hitResult;
        Entity previousCrosshairPickEntity = minecraft.crosshairPickEntity;
        Vec3 previousMainCameraPosition = actualMainCamera.getPosition();
        float previousMainCameraYaw = actualMainCamera.getYRot();
        float previousMainCameraPitch = actualMainCamera.getXRot();
        float previousMainCameraRoll = actualMainCamera.getRoll();

        try (SkyesightSecondaryRenderContext.Scope ignored =
                     SkyesightSecondaryRenderContext.push(output, camera, minecraft.getMainRenderTarget())) {
            gameRenderer.setRenderHand(false);
            gameRenderer.setRenderBlockOutline(false);

            gameRenderer.renderLevel(event.getPartialTick());
        } catch (RuntimeException exception) {
            throw exception;
        } finally {
            gameRenderer.setRenderHand(previousRenderHand);
            gameRenderer.setRenderBlockOutline(previousRenderBlockOutline);
            minecraft.hitResult = previousHitResult;
            minecraft.crosshairPickEntity = previousCrosshairPickEntity;
            ((CameraInvoker) actualMainCamera).skyesight$setPosition(previousMainCameraPosition);
            ((CameraInvoker) actualMainCamera).skyesight$setRotation(
                    previousMainCameraYaw,
                    previousMainCameraPitch,
                    previousMainCameraRoll
            );
        }
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
                java.util.Locale.ROOT,
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

    static void renderSodiumTerrainOnly(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event
    ) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera camera = frame.camera();
        PortalSecondaryRenderState.activeRemoteTerrainChunkRadius = configuredSameDimRenderChunkRadius(frame);
        Matrix4f projection = frame.projectionMatrix();
        Matrix4f modelView = frame.modelViewMatrix();
        Frustum frustum = frame.frustum();

        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        if (!frame.diagnostics().renderToCurrentTarget()
                || frame.diagnostics().renderSkyInCurrentTarget()) {
            renderSecondarySkyIfEnabled(frame, minecraft, partialTick);
        } else {
            resetSecondaryFeatureDiagnosticsForDirectRender();
        }

        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        ResourceLocation viewId = frame.diagnostics().entityWatchRegionId();
        String stickFlashMode = stickFlashTestModeFor(viewId);
        if ("no_sodium".equals(stickFlashMode)) {
            return;
        }
        if (SkyesightDebugConfig.DEBUG_DISABLE_SAME_DIM_PORTAL_TERRAIN_FOR_FLASH_TEST
                && frame.diagnostics().renderToCurrentTarget()) {
            return;
        }

        int sectionCount = Math.max(1, minecraft.level.getSectionsCount());
        int portalOwnedRadius = configuredPortalOwnedRenderRadiusChunks(frame);
        int targetReuseRadius = configuredSameDimPlayerLoadedReuseRadiusChunks(frame);
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
            return;
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
            context.sodiumChunkSource().updateReadyChunks(
                    minecraft.level,
                    context.sodiumChunkTracker(),
                    camera.getPosition(),
                    portalOwnedRadius,
                    effectiveReuseRadius,
                    configuredReusePlayerLoadedChunksForSameDim(frame),
                    PortalSodiumRenderConfig.DEFAULT_MAX_SAME_DIM_READY_CHUNKS_ADDED_PER_FRAME,
                    maxScannedChunks
            );
        }
        PortalRenderCostAudit.record(viewId, "readyChunks", readyChunkStart);
        PortalRenderCostAudit.record(viewId, "terrainChunkReadyUpdate", readyChunkStart);
        int candidateSectionsAfterBudget = chunkTrackerSkippedForStickTest
                ? 0
                : context.sodiumChunkSource().lastScannedChunkCount() * sectionCount;
        int readyChunksAdded = chunkTrackerSkippedForStickTest
                ? 0
                : context.sodiumChunkSource().lastAddedChunkCount();
        boolean budgetLimited = !chunkTrackerSkippedForStickTest
                && (!context.sodiumChunkSource().lastScanCompletedCycle()
                || context.sodiumChunkSource().lastBudgetSkippedChunkCount() > 0);
        context.recordSameDimTerrainPopulation(candidateSectionsAfterBudget, readyChunksAdded);
        if (budgetLimited && !warmupCompleteBeforeWork) {
            return;
        }
        long sodiumAcquireStart = PortalRenderCostAudit.start();
        SodiumWorldRenderer renderer = frame.diagnostics().renderToCurrentTarget()
                ? getOrCreateSodiumRenderer(minecraft, minecraft.level, context, frame.diagnostics().entityWatchRegionId())
                : getSodiumRendererForSecondaryDebug(minecraft, minecraft.level, context, frame.diagnostics().entityWatchRegionId());
        PortalRenderCostAudit.record(viewId, "sodiumAcquire", sodiumAcquireStart);
        if (renderer == null) {
            return;
        }
        boolean usingMainSodiumRenderer = SodiumWorldRenderer.instanceNullable() == renderer;
        if (shouldSkipNewPortalTerrainWarmup(frame, context)) {
            context.incrementFirstVisibleTerrainFramesSkipped();
            context.incrementSodiumRendererReadyAgeFrames();
            return;
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
                frame.diagnostics().entityWatchRegionId(),
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
                             context.sodiumChunkTracker(),
                             PortalSodiumRenderConfig.SODIUM_DISABLE_OCCLUSION_CULLING_FOR_SECONDARY
                     )) {
            int pendingRebuildChunkCountGlobal = context.pendingSodiumRebuildChunks().size();
            int pendingRebuildChunkCountForView = countPendingRebuildChunksForTrackedTerrain(context);
            long pendingRebuildChunkSignatureForView = pendingRebuildChunkSignatureForTrackedTerrain(context);
            int pendingBlockUpdateChunkCount = context.pendingSodiumBlockUpdateChunks().size();
            int pendingBlockUpdateChunkCountForView = countPendingBlockUpdatesForTrackedTerrain(context);
            long pendingBlockUpdateSignatureForView = pendingBlockUpdateSignatureForTrackedTerrain(context);
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
                            configuredReusePlayerLoadedChunksForSameDim(frame),
                            frame.diagnostics().renderTranslucent(),
                            context.sodiumChunkSource().trackedChunkCount(),
                            context.sodiumChunkSource().trackedChunkSignature(),
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
                    context.sodiumChunkSource().lastAddedChunkCount(),
                    context.sodiumChunkSource().lastRemovedChunkCount(),
                    setupReuseDecision.reason()
            );
            BlockPos sharedRemoteCenter = secondaryRemoteCenterBlockPos(context, frame);
            scheduleRemoteSodiumRebuildsIfNeeded(renderer, minecraft.level, sharedRemoteCenter, context);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderType.solid().setupRenderState();

            try {
                if (frame.diagnostics().renderToCurrentTarget()) {
                    applyDirectDepthModeAtSodiumDrawPoint();
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
                            frame.diagnostics().entityWatchRegionId(),
                            minecraft.level,
                            renderer,
                            cameraPosition,
                            portalOwnedRadius,
                            effectiveReuseRadius,
                            configuredReusePlayerLoadedChunksForSameDim(frame),
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
                renderDirectBlockEntitiesIfEnabled(frame, context, minecraft, partialTick);
                if (!PortalSecondaryRenderConfig.PORTAL_PARTICLES_ALL_AFTER_ENTITIES && !PortalSecondaryRenderConfig.PORTAL_PARTICLES_AFTER_ENTITIES) {
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

            if (!frame.diagnostics().renderToCurrentTarget() && !PortalSecondaryRenderConfig.SECONDARY_RENDER_ENTITIES_AFTER_TRANSLUCENT) {
                renderSecondaryBlockEntitiesIfEnabled(frame, context, minecraft, partialTick);
                renderSecondaryEntitiesIfEnabled(frame, context, minecraft, partialTick);
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

            if (!frame.diagnostics().renderToCurrentTarget() && PortalSecondaryRenderConfig.SECONDARY_RENDER_ENTITIES_AFTER_TRANSLUCENT) {
                renderSecondaryBlockEntitiesIfEnabled(frame, context, minecraft, partialTick);
                renderSecondaryEntitiesIfEnabled(frame, context, minecraft, partialTick);
            }

            if (!frame.diagnostics().renderToCurrentTarget()) {
                renderSecondaryParticlesIfEnabled(frame, minecraft, partialTick);
            }
        } finally {
            RenderDevice.exitManagedCode();
        }
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

    private static void renderDirectBlockEntitiesIfEnabled(
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
            int stencilRef = directPortalStencilRef(frame);
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

    private static int directPortalStencilRef(SecondaryViewFrame frame) {
        String portalId = frame == null ? "" : frame.diagnostics().portalInstanceId();
        int explicitRef = frame == null ? 0 : frame.diagnostics().portalStencilRef();
        if (explicitRef > 0) {
            return explicitRef;
        }

        // Btw this here is legacy fallback for older/manual debug frames that do not carry an explicit registered view stencil ref yet.
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

    private static void applyDirectDepthModeAtSodiumDrawPoint() {
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

    private static void renderSecondarySkyIfEnabled(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick
    ) {
        secondaryEntityPassAttempted = false;
        if (!PortalSecondaryRenderConfig.SECONDARY_FEATURE_SKY || minecraft.level == null) {
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

    private static void resetSecondaryFeatureDiagnosticsForDirectRender() {
        secondaryEntityPassAttempted = false;
    }

    private static void renderSecondaryBlockEntitiesIfEnabled(
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

    private static BlockPos secondaryRemoteCenterBlockPos(SecondaryViewContext context, SecondaryViewFrame frame) {
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

    private static void renderSecondaryEntitiesIfEnabled(
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

    private static int configuredTerrainChunkRadius(SecondaryViewFrame frame) {
        return frame == null || frame.diagnostics().terrainChunkRadius() <= 0
                ? PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS
                : frame.diagnostics().terrainChunkRadius();
    }

    private static int configuredPortalOwnedRenderRadiusChunks(SecondaryViewFrame frame) {
        return frame == null || frame.diagnostics().portalOwnedRenderRadiusChunks() <= 0
                ? configuredTerrainChunkRadius(frame)
                : frame.diagnostics().portalOwnedRenderRadiusChunks();
    }

    private static int configuredSameDimPlayerLoadedReuseRadiusChunks(SecondaryViewFrame frame) {
        return frame == null || frame.diagnostics().sameDimPlayerLoadedReuseRadiusChunks() <= 0
                ? com.skyeshade.skyesight.api.PortalRenderSettings.DEFAULT_SAME_DIM_PLAYER_LOADED_REUSE_RADIUS_CHUNKS
                : frame.diagnostics().sameDimPlayerLoadedReuseRadiusChunks();
    }

    private static boolean configuredReusePlayerLoadedChunksForSameDim(SecondaryViewFrame frame) {
        return frame != null && frame.diagnostics().reusePlayerLoadedChunksForSameDim();
    }

    private static boolean shouldSkipNewPortalTerrainWarmup(SecondaryViewFrame frame, SecondaryViewContext context) {
        return frame != null
                && context != null
                && frame.diagnostics().renderToCurrentTarget()
                && PortalSodiumRenderConfig.DEFAULT_NEW_PORTAL_TERRAIN_SKIP_FRAMES > 0
                && context.firstVisibleTerrainFramesSkipped() < PortalSodiumRenderConfig.DEFAULT_NEW_PORTAL_TERRAIN_SKIP_FRAMES;
    }

    private static int configuredSameDimRenderChunkRadius(SecondaryViewFrame frame) {
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

    private static void renderSecondaryParticlesIfEnabled(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick
    ) {
        renderSecondaryParticlesIfEnabled(frame, minecraft, partialTick, SecondaryParticlePass.RenderGroup.ALL);
    }

    private static void renderSecondaryParticlesIfEnabled(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick,
            SecondaryParticlePass.RenderGroup renderGroup
    ) {
        if (!PortalSecondaryRenderConfig.SECONDARY_RENDER_PARTICLES) {
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

    private static String stickFlashTestModeFor(ResourceLocation viewId) {
        if (viewId == null) {
            return "normal";
        }
        RegisteredPortalView view = SkyesightPortalApi.getPortal(viewId.toString());
        if (view == null || !"debug-stick".equals(view.sourceTag())) {
            return "normal";
        }
        if (SkyesightDebugConfig.DEBUG_DISABLE_NEW_STICK_PORTAL_RENDER_FOR_FLASH_TEST) {
            return "hard_block";
        }
        if (SkyesightDebugConfig.DEBUG_STICK_RENDER_MASK_ONLY_FOR_FLASH_TEST) {
            return "mask_only";
        }
        if (SkyesightDebugConfig.DEBUG_STICK_RENDER_MASK_AND_SKY_ONLY_FOR_FLASH_TEST) {
            return "mask_sky_only";
        }
        if (SkyesightDebugConfig.DEBUG_STICK_RENDER_NO_TERRAIN_FOR_FLASH_TEST) {
            return "no_terrain";
        }
        if (SkyesightDebugConfig.DEBUG_STICK_RENDER_NO_SODIUM_RENDERER_FOR_FLASH_TEST) {
            return "no_sodium";
        }
        if (SkyesightDebugConfig.DEBUG_STICK_RENDER_NO_CHUNK_TRACKER_UPDATE_FOR_FLASH_TEST) {
            return "no_chunk_tracker";
        }
        return "normal";
    }

    private static int candidateSectionCountForRadius(int radiusChunks, int sectionCount) {
        int radius = Math.max(0, radiusChunks);
        int side = radius * 2 + 3;
        return side * side * Math.max(1, sectionCount);
    }

    private static SodiumWorldRenderer createPortalSodiumRendererInRenderStage(
            Minecraft minecraft,
            ClientLevel level,
            SecondaryViewContext context,
            ResourceLocation viewId
    ) {
        SodiumWorldRenderer previousRenderer = context.sodiumRenderer();
        if (previousRenderer != null) {
            return previousRenderer;
        }
        SodiumWorldRenderer pooled = PortalSodiumRendererPool.acquire(viewId, level);
        if (pooled != null) {
            context.setSodiumRenderer(pooled);
            context.setSodiumRendererLevel(level);
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
            context.setSodiumRenderer(renderer);
            context.setSodiumRendererLevel(level);
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
        SodiumWorldRenderer sodiumRenderer = context.sodiumRenderer();
        ClientLevel sodiumRendererLevel = context.sodiumRendererLevel();

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

    @SuppressWarnings("unchecked")
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

    private static boolean scheduleRemoteSodiumRebuildsIfNeeded(
            SodiumWorldRenderer renderer,
            ClientLevel level,
            BlockPos cameraBlockPos,
            SecondaryViewContext context
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

    private static int countPendingRebuildChunksForTrackedTerrain(SecondaryViewContext context) {
        if (context == null) {
            return 0;
        }
        int count = 0;
        for (ChunkPos chunk : context.pendingSodiumRebuildChunks()) {
            if (context.sodiumChunkSource().isTracked(chunk)) {
                count++;
            }
        }
        return count;
    }

    private static long pendingRebuildChunkSignatureForTrackedTerrain(SecondaryViewContext context) {
        if (context == null) {
            return 0L;
        }
        long signature = 0L;
        for (ChunkPos chunk : context.pendingSodiumRebuildChunks()) {
            if (context.sodiumChunkSource().isTracked(chunk)) {
                signature += mixPackedChunk(ChunkPos.asLong(chunk.x, chunk.z));
            }
        }
        return signature;
    }

    private static int countPendingBlockUpdatesForTrackedTerrain(SecondaryViewContext context) {
        if (context == null) {
            return 0;
        }
        int count = 0;
        for (long packed : context.pendingSodiumBlockUpdateChunks()) {
            if (context.sodiumChunkSource().isTracked(packed)) {
                count++;
            }
        }
        return count;
    }

    private static long pendingBlockUpdateSignatureForTrackedTerrain(SecondaryViewContext context) {
        if (context == null) {
            return 0L;
        }
        long signature = 0L;
        for (long packed : context.pendingSodiumBlockUpdateChunks()) {
            if (context.sodiumChunkSource().isTracked(packed)) {
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
    private static int expectedRemoteChunkCount() {
        int width = PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS * 2 + 1;
        return width * width;
    }

    private static int expectedRequiredRemoteChunkCount() {
        int width = PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS * 2 + 1;
        return width * width;
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

    private static Camera configureSecondaryCamera(Minecraft minecraft, float partialTick) {
        LocalPlayer player = minecraft.player;

        var camera = PortalSecondaryRenderState.SECONDARY_VIEW.camera();
        camera.setup(
                minecraft.level,
                player,
                false,
                false,
                partialTick
        );

        var targetPosition = player.getEyePosition(partialTick);
        var cameraPosition = targetPosition.add(
                PortalProjectionConfig.REMOTE_CAMERA_X_OFFSET,
                0.0D,
                PortalProjectionConfig.REMOTE_CAMERA_Z_OFFSET
        );
        if (PortalProjectionConfig.FREEZE_SECONDARY_REMOTE_CENTER && minecraft.level != null) {
            cameraPosition = PortalSecondaryRenderState.SECONDARY_VIEW.getOrSetFrozenRemoteCameraPosition(minecraft.level, cameraPosition);
        }
        var lookDirection = targetPosition.subtract(cameraPosition);

        camera.setPositionPublic(cameraPosition);
        applySecondaryCameraRotation(minecraft, player, camera, lookDirection, partialTick);

        BlockPos blockPos = BlockPos.containing(cameraPosition);
        ChunkPos chunkPos = new ChunkPos(blockPos);
        PortalSecondaryRenderState.SECONDARY_VIEW.setRemoteChunkCenter(chunkPos);

        if (!PortalRemoteChunkConfig.DIRECT_DISABLE_REMOTE_CLIENT_CACHE_EXPANSION) {
            updateClientChunkCacheExpansion(minecraft, chunkPos);
        }
        PortalRemoteChunkController.updateRemoteChunkForceLoading(minecraft, chunkPos);

        PortalRemoteChunkRuntimeState.loadedChunksInRadius = PortalRemoteChunkController.countClientLoadedChunksInRadius(
                minecraft,
                chunkPos,
                PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS
        );
        PortalRemoteChunkRuntimeState.requiredLoadedChunksInRadius = PortalRemoteChunkController.countClientLoadedChunksInRequiredRadius(
                minecraft,
                chunkPos,
                PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS
        );
        PortalRemoteChunkController.updateRemoteClientChunkReadiness(minecraft, chunkPos);

        return camera;
    }

    private static void applySecondaryCameraRotation(
            Minecraft minecraft,
            LocalPlayer player,
            com.skyeshade.skyesight.client.view.SkyesightMutableCamera camera,
            Vec3 lookDirection,
            float partialTick
    ) {
        switch (PortalProjectionConfig.SECONDARY_CAMERA_ROTATION_MODE) {
            case COPY_MAIN_CAMERA -> {
                Camera mainCamera = minecraft.gameRenderer.getMainCamera();
                camera.setRotationPublic(new Quaternionf(mainCamera.rotation()));
            }
            case COPY_PLAYER_VIEW -> camera.setRotationPublic(
                    player.getViewYRot(partialTick),
                    player.getViewXRot(partialTick),
                    0.0F
            );
            case LOOK_AT_PLAYER -> {
                double horizontalLength = Math.sqrt(
                        lookDirection.x() * lookDirection.x()
                                + lookDirection.z() * lookDirection.z()
                );

                float yaw = (float) Math.toDegrees(Math.atan2(-lookDirection.x(), lookDirection.z()));
                float pitch = (float) Math.toDegrees(Math.atan2(-lookDirection.y(), horizontalLength));

                camera.setRotationPublic(yaw, pitch, 0.0F);
            }
        }
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


    public enum Backend {
        NONE,
        SODIUM_TERRAIN_ONLY,
        FULL_GAME_RENDERER,
        FULL_LEVEL_RENDERER,
        SODIUM
    }

    public enum SecondaryCameraRotationMode {
        LOOK_AT_PLAYER,
        COPY_MAIN_CAMERA,
        COPY_PLAYER_VIEW
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



