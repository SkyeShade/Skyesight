package com.skyeshade.skyesight.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightClientConfig;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.SkyesightPortalEntityPoolConfig;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.PortalRenderSettings;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightClipPlane;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalChunkStorage;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalRenderLevelView;
import com.skyeshade.skyesight.client.render.MainTerrainStateSnapshot;
import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;
import com.skyeshade.skyesight.client.render.PortalVisualDisplayTickDriver;
import com.skyeshade.skyesight.client.render.SecondaryEntityPass;
import com.skyeshade.skyesight.client.render.SecondaryParticlePass;
import com.skyeshade.skyesight.client.render.SecondarySodiumTerrainPass;
import com.skyeshade.skyesight.client.render.SecondaryViewContext;
import com.skyeshade.skyesight.client.render.SecondaryViewFrame;
import com.skyeshade.skyesight.client.render.SkyesightSecondaryRenderContext;
import com.skyeshade.skyesight.client.render.entity.PortalDimensionEntitySources;
import com.skyeshade.skyesight.client.render.entity.PortalRenderableEntity;
import com.skyeshade.skyesight.client.render.light.SkyesightLightTextureUpdater;
import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPool;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.mixin.client.EntityRenderDispatcherAccessor;
import com.skyeshade.skyesight.mixin.client.LevelRendererSkyBufferAccessor;
import com.skyeshade.skyesight.server.SkyesightSecondaryChunkWatchRegion;
import com.skyeshade.skyesight.server.SkyesightSecondaryWatchRegion;
import com.skyeshade.skyesight.server.SkyesightServerViewTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.CROSS_DIM_PORTAL_PARTICLES_ENABLED;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_DISABLE_ALL_PORTAL_SUBPASSES_AFTER_MASK;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_DISABLE_PORTAL_BLOCK_ENTITIES;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_DISABLE_PORTAL_DEPTH_CLEAR;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_DISABLE_PORTAL_ENTITIES;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_DISABLE_PORTAL_SKY_FILL;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_DISABLE_PORTAL_TERRAIN;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_DISABLE_PORTAL_TRANSPARENT;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_PORTAL_SKY_AT_AFTER_SKY;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_RENDER_ENTITIES;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_RENDER_ENTITIES_ONE_PORTAL_ONLY;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_RENDER_SIMPLE_SKY_FILL;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_RENDER_SKY_ONE_PORTAL_ONLY;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_RENDER_TERRAIN;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_SKY_CAPTURE_BYPASS_STENCIL;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_SKY_CAPTURE_COMPOSITE_ENABLED;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_SKY_CAPTURE_ENABLED;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_SKY_CAPTURE_FALLBACK_SIMPLE_COLOR;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_SKY_DISABLE_SIMPLE_PREFILL;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_SKY_MASK_ONLY;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_STENCIL_DRAW_PROOF_COLOR;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.DIRECT_STENCIL_RENDER_TERRAIN;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.ENABLE_PORTAL_ENTITY_POOL_RENDERING;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.FAR_PORTAL_RENDER_BLOCK_ENTITIES;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.FLUSH_MAIN_BUFFERS_BEFORE_PORTAL_MASK;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.PORTAL_CAMERA_EXIT_PUSH_EPSILON_BLOCKS;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.PORTAL_MAIN_PARTICLE_OCCLUSION_FIX;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.PORTAL_SKY_COMPOSITE_OPAQUE;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.RENDER_SECONDARY_PORTAL_COMPOSITE;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.STENCIL_MASK_AT_WORLD_STAGE;
import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.USE_STABLE_TERRAIN_INVOCATION_PATH;

public final class PortalDirectStencilRenderer {
    private static final DirectSkyMode DIRECT_SKY_MODE = DirectSkyMode.CLONED_VANILLA_CELESTIAL;
    private static final PortalSkyCaptureManager.Mode DIRECT_SKY_CAPTURE_MODE =
            PortalSkyCaptureManager.Mode.PORTAL_CAMERA_RENDER;
    private static final int DEFAULT_MAX_PENDING_PORTAL_BLOCK_UPDATE_CHUNKS = 256;
    private static final int DEFAULT_MAX_PENDING_PORTAL_BLOCK_UPDATES_FLUSH_PER_FRAME = 16;
    private static final DirectPortalRenderDebugMode DIRECT_PORTAL_RENDER_MODE =
            DirectPortalRenderDebugMode.TERRAIN_NORMAL_PERSPECTIVE;
    private static final DirectPortalDepthMode DIRECT_PORTAL_DEPTH_MODE =
            DirectPortalDepthMode.CLEAR_PORTAL_DEPTH_THEN_LEQUAL;
    private static final PortalMaskDepthMode PORTAL_MASK_DEPTH_MODE =
            PortalMaskDepthMode.DEPTH_TEST_NO_DEPTH_WRITE;
    private static final DirectPortalCameraMode DIRECT_PORTAL_CAMERA_MODE =
            DirectPortalCameraMode.PORTAL_TRANSFORM;
    private static final PortalSecondaryRotationMode PORTAL_SECONDARY_ROTATION_MODE =
            PortalSecondaryRotationMode.OLD_EXIT_PORTAL_RENDER_ROTATION;

    private static final Map<ResourceLocation, SecondaryViewContext> PORTAL_VIEW_CONTEXTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Boolean> portalMaskWroteThisFrameByView = new HashMap<>();
    private static final Map<ResourceLocation, Matrix4f> portalMainViewProjectionByView = new HashMap<>();
    private static final Map<ResourceLocation, PortalVisibilityCull.PortalVisibilityResult> portalVisibilityThisFrameByView = new HashMap<>();
    private static final Map<ResourceLocation, PortalFrameVisibilityState> portalFrameVisibilityStateByView = new HashMap<>();
    private static final Set<ResourceLocation> portalRenderedThisFrameByView = new HashSet<>();
    private static final Set<ResourceLocation> invalidPortalStencilRefWarnings = new HashSet<>();

    public static void invalidateViewCaches(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        SecondaryViewContext context = PORTAL_VIEW_CONTEXTS.remove(viewId);
        if (context != null) {
            context.close();
        }
        portalMaskWroteThisFrameByView.remove(viewId);
        portalMainViewProjectionByView.remove(viewId);
        portalVisibilityThisFrameByView.remove(viewId);
        portalFrameVisibilityStateByView.remove(viewId);
        portalRenderedThisFrameByView.remove(viewId);
        invalidPortalStencilRefWarnings.remove(viewId);
        PortalStickSkyWarmup.clear(viewId);
        PortalRenderTargetBounds.clear(viewId);
        clearCrossDimEntitySourceLog(viewId);
    }

    public static int invalidateLevelBoundCaches(String reason) {
        int contextCount = PORTAL_VIEW_CONTEXTS.size();
        for (SecondaryViewContext context : PORTAL_VIEW_CONTEXTS.values()) {
            context.close();
        }
        PORTAL_VIEW_CONTEXTS.clear();
        portalMaskWroteThisFrameByView.clear();
        portalMainViewProjectionByView.clear();
        portalVisibilityThisFrameByView.clear();
        portalFrameVisibilityStateByView.clear();
        portalRenderedThisFrameByView.clear();
        invalidPortalStencilRefWarnings.clear();
        PortalStickSkyWarmup.clearAll();
        PortalRenderTargetBounds.clearAll();
        CROSS_DIM_ENTITY_SOURCE_LOGGED.clear();
        PORTAL_SKY_CAPTURE_MANAGER.close();
        return contextCount;
    }

    public static void softReplaceViewCaches(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        portalMaskWroteThisFrameByView.remove(viewId);
        portalMainViewProjectionByView.remove(viewId);
        portalVisibilityThisFrameByView.remove(viewId);
        portalFrameVisibilityStateByView.remove(viewId);
        portalRenderedThisFrameByView.remove(viewId);
        invalidPortalStencilRefWarnings.remove(viewId);
        PortalRenderTargetBounds.clear(viewId);
        clearCrossDimEntitySourceLog(viewId);
    }

    private static List<RegisteredRenderView> activeRenderViews(ResourceKey<Level> displayDimension, Camera camera, String stage) {
        return activeRenderViews(displayDimension, camera, stage, null, false);
    }

    private static List<RegisteredRenderView> activeRenderViews(
            ResourceKey<Level> displayDimension,
            Camera camera,
            String stage,
            Frustum mainFrustum,
            boolean storeVisibilityForFrame
    ) {
        List<RegisteredRenderView> views = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        double maxRenderDistance = SkyesightClientConfig.portalRenderDistanceBlocks();

        for (RegisteredPortalView view : SkyesightPortalApi.getAllPortals()) {
            PortalRenderSettings settings = view.renderSettings();
            if (settings == null || !settings.enabled() || !settings.rendersView() || !view.active()) {
                continue;
            }
            if (hasInvalidRegisteredStencilRef(view)) {
                storeVisibilityState(view.id(), PortalFrameVisibilityState.noContent(view.id()));
                continue;
            }
            if (!viewHasRenderableContent(settings)) {
                storeVisibilityState(view.id(), PortalFrameVisibilityState.noContent(view.id()));
                continue;
            }
            if (displayDimension != null && !displayDimension.equals(view.source().dimension())) {
                continue;
            }
            boolean withinRenderDistance = PortalRenderDistanceGate.shouldRenderPortalFromCamera(
                    minecraft,
                    view,
                    camera,
                    maxRenderDistance
            );
            PortalRenderDistanceGate.logDecisionIfDue(
                    minecraft,
                    view,
                    camera,
                    maxRenderDistance,
                    withinRenderDistance,
                    stage,
                    withinRenderDistance ? "source-frame-within-client-render-distance" : "source-frame-too-far"
            );
            if (!withinRenderDistance) {
                storeVisibilityState(view.id(), PortalFrameVisibilityState.distanceCulled(view.id(), "distance"));
                continue;
            }
            if (shouldUseStoredVisibilityForActiveStage(stage)) {
                PortalFrameVisibilityState state = portalFrameVisibilityStateByView.get(view.id());
                if (state == null || !state.visibleThisFrame()) {
                    continue;
                }
            } else if (SkyesightClientConfig.enablePortalFrustumCulling() && mainFrustum != null) {
                PortalVisibilityCull.PortalVisibilityResult visibility = evaluatePortalVisibility(
                        minecraft,
                        view,
                        camera,
                        mainFrustum,
                        stage,
                        storeVisibilityForFrame
                );
                if (shouldEnforceVisibilityCull(visibility)) {
                    continue;
                }
            }
            if (!portalPhysicalSideVisible(view, camera, stage)) {
                continue;
            }
            views.add(registeredRenderView(view));
        }

        return views;
    }

    private static boolean hasInvalidRegisteredStencilRef(RegisteredPortalView view) {
        if (view == null || view.renderSettings() == null || view.renderSettings().stencilRef() > 0) {
            return false;
        }
        logInvalidPortalStencilRef(view.id(), view.renderSettings().stencilRef());
        return true;
    }

    private static void logInvalidPortalStencilRef(ResourceLocation viewId, int stencilRef) {
        if (viewId != null && !invalidPortalStencilRefWarnings.add(viewId)) {
            return;
        }
        Skyesight.LOGGER.warn(
                "[Skyesight] INVALID_PORTAL_STENCIL_REF view={} stencilRef={} action=skip-render",
                viewId == null ? "unknown" : viewId,
                stencilRef
        );
    }

    private static boolean portalPhysicalSideVisible(RegisteredPortalView view, Camera camera, String stage) {
        if (view == null || view.renderBackface() || view.source() == null || camera == null) {
            return true;
        }
        Vec3 normal = facingNormal(view.source().facing());
        Vec3 cameraOffset = camera.getPosition().subtract(view.source().center());
        double signedSide = cameraOffset.dot(normal);
        boolean visible = signedSide <= 0.01D;
        return visible;
    }

    private static Vec3 facingNormal(Direction facing) {
        if (facing == null) {
            return Vec3.ZERO;
        }
        return new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private record PortalFrameVisibilityState(
            ResourceLocation viewId,
            boolean distanceAllowed,
            boolean frustumCullEnabled,
            boolean frustumVisible,
            boolean culledByFrustum,
            boolean visibleThisFrame,
            String reason,
            PortalVisibilityCull.PortalVisibilityResult result
    ) {
        private static PortalFrameVisibilityState fromResult(ResourceLocation viewId, PortalVisibilityCull.PortalVisibilityResult result) {
            boolean distanceAllowed = result != null && result.distancePass();
            boolean frustumVisible = result != null && result.frustumPass() && result.visibleEnoughForHeavyRender();
            boolean visibleThisFrame = result != null && result.visibleEnoughForHeavyRender();
            boolean culledByFrustum = SkyesightClientConfig.enablePortalFrustumCulling()
                    && result != null
                    && distanceAllowed
                    && !visibleThisFrame;
            return new PortalFrameVisibilityState(
                    viewId,
                    distanceAllowed,
                    SkyesightClientConfig.enablePortalFrustumCulling(),
                    frustumVisible,
                    culledByFrustum,
                    visibleThisFrame,
                    result == null ? "missing-visibility-result" : result.reason(),
                    result
            );
        }

        private static PortalFrameVisibilityState distanceCulled(ResourceLocation viewId, String reason) {
            return new PortalFrameVisibilityState(
                    viewId,
                    false,
                    SkyesightClientConfig.enablePortalFrustumCulling(),
                    true,
                    false,
                    false,
                    reason == null || reason.isBlank() ? "distance" : reason,
                    null
            );
        }

        private static PortalFrameVisibilityState frustumCulled(ResourceLocation viewId, String reason) {
            return new PortalFrameVisibilityState(
                    viewId,
                    true,
                    SkyesightClientConfig.enablePortalFrustumCulling(),
                    false,
                    true,
                    false,
                    reason == null || reason.isBlank() ? "frustum" : reason,
                    null
            );
        }

        private static PortalFrameVisibilityState noContent(ResourceLocation viewId) {
            return new PortalFrameVisibilityState(
                    viewId,
                    true,
                    SkyesightClientConfig.enablePortalFrustumCulling(),
                    true,
                    false,
                    false,
                    "no-renderable-content",
                    null
            );
        }
    }

    private static List<RegisteredRenderView> activeRenderViews(ResourceKey<Level> displayDimension) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft == null || minecraft.gameRenderer == null ? null : minecraft.gameRenderer.getMainCamera();
        return activeRenderViews(displayDimension, camera, "unknown");
    }

    private static List<RegisteredRenderView> activeCrossDimRenderViews(ResourceKey<Level> displayDimension, Camera camera, String stage) {
        return activeRenderViews(displayDimension, camera, stage).stream()
                .filter(view -> !view.definition().source().dimension().equals(view.definition().target().dimension()))
                .toList();
    }

    private static RegisteredRenderView registeredRenderView(RegisteredPortalView view) {
        SecondaryViewContext context = PORTAL_VIEW_CONTEXTS.get(view.id());
        if (context == null) {
            context = new SecondaryViewContext();
            PORTAL_VIEW_CONTEXTS.put(view.id(), context);
        }
        context.setViewId(view.id());
        DebugPortalRenderConfig config = renderConfigFromSettings(view.renderSettings());
        PortalRenderView renderView = new PortalRenderView(
                portalFrame(view.source()),
                portalFrame(view.target()),
                context,
                config
        );
        return new RegisteredRenderView(view, renderView, portalLabel(view));
    }

    private static PortalFrame portalFrame(PortalEndpoint endpoint) {
        return new PortalFrame(
                endpoint.center(),
                endpoint.rotation(),
                endpoint.width(),
                endpoint.height()
        );
    }

    private static boolean isDebugStickView(RegisteredPortalView view) {
        return view != null && "debug-stick".equals(view.sourceTag());
    }

    private static boolean isDebugStickView(RegisteredRenderView view) {
        return view != null && isDebugStickView(view.definition());
    }

    private static ResourceLocation unknownPortalViewId() {
        return ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "unknown");
    }

    private static boolean shouldSkipStickSkyCapture(RegisteredRenderView view) {
        return isDebugStickView(view) && shouldSkipStickSkyWarmup(view.definition().id());
    }

    private static boolean shouldSkipStickSkyComposite(ResourceLocation viewId) {
        RegisteredPortalView view = viewId == null ? null : SkyesightPortalApi.getPortal(viewId.toString());
        return isDebugStickView(view) && shouldSkipStickSkyWarmup(viewId);
    }

    private static boolean shouldSkipStickSkyWarmup(ResourceLocation viewId) {
        RegisteredPortalView view = viewId == null ? null : SkyesightPortalApi.getPortal(viewId.toString());
        if (!isDebugStickView(view)) {
            return false;
        }
        long frame = Math.max(portalMaskFrameId, compositeFrameId);
        return PortalStickSkyWarmup.shouldSkip(viewId, frame);
    }

    private static DebugPortalRenderConfig renderConfigFromSettings(PortalRenderSettings settings) {
        return new DebugPortalRenderConfig(
                settings.enabled(),
                settings.rendersView(),
                settings.stencilRef(),
                settings.terrainChunkRadius(),
                settings.portalOwnedRenderRadiusChunks(),
                settings.sameDimPlayerLoadedReuseRadiusChunks(),
                settings.reusePlayerLoadedChunksForSameDim(),
                settings.entityChunkRadius(),
                settings.blockEntityChunkRadius(),
                settings.blockUpdateChunkRadius(),
                settings.renderSky(),
                settings.renderTerrain(),
                settings.renderTranslucent(),
                settings.renderEntities(),
                settings.renderBlockEntities(),
                settings.renderParticles(),
                settings.stencilMask()
        );
    }

    private static boolean viewHasRenderableContent(PortalRenderSettings settings) {
        return settings != null
                && (settings.renderSky()
                || settings.renderTerrain()
                || settings.renderTranslucent()
                || settings.renderEntities()
                || settings.renderBlockEntities()
                || settings.renderParticles());
    }

    private static boolean viewHasRenderableContent(DebugPortalRenderConfig config) {
        return config != null
                && (config.renderSky()
                || config.renderTerrain()
                || config.renderTranslucent()
                || config.renderEntities()
                || config.renderBlockEntities()
                || config.renderParticles());
    }

    private static boolean viewNeedsDirectPostSkyPass(DebugPortalRenderConfig config) {
        return config != null
                && (config.renderTerrain()
                || config.renderTranslucent()
                || config.renderEntities()
                || config.renderBlockEntities()
                || config.renderParticles());
    }

    private static String portalLabel(RegisteredPortalView view) {
        String endpointId = view.source().id();
        if (endpointId != null && !endpointId.isBlank()) {
            return endpointId;
        }
        String path = view.id().getPath();
        int index = path.lastIndexOf('_');
        return index >= 0 && index + 1 < path.length()
                ? path.substring(index + 1).toUpperCase(Locale.ROOT)
                : path;
    }

    private static String skyCaptureKey(ResourceLocation viewId) {
        return viewId == null ? "unknown" : viewId.toString();
    }

    private static boolean maskWrote(ResourceLocation viewId) {
        return portalMaskWroteThisFrameByView.getOrDefault(viewId, false);
    }

    private static Matrix4f mainViewProjectionFor(ResourceLocation viewId, Matrix4f fallback) {
        Matrix4f matrix = portalMainViewProjectionByView.get(viewId);
        return matrix == null ? fallback : new Matrix4f(matrix);
    }

    private static PortalVisibilityCull.PortalVisibilityResult evaluatePortalVisibility(
            Minecraft minecraft,
            RegisteredPortalView view,
            Camera camera,
            Frustum mainFrustum,
            String stage,
            boolean storeForFrame
    ) {
        int width = minecraft == null || minecraft.getMainRenderTarget() == null
                ? 0
                : minecraft.getMainRenderTarget().width;
        int height = minecraft == null || minecraft.getMainRenderTarget() == null
                ? 0
                : minecraft.getMainRenderTarget().height;
        PortalVisibilityCull.PortalVisibilityResult result = PortalVisibilityCull.evaluate(
                minecraft,
                view,
                camera,
                mainFrustum,
                width,
                height
        );
        ResourceLocation viewId = view == null ? null : view.id();
        if (storeForFrame && viewId != null) {
            portalVisibilityThisFrameByView.put(viewId, result);
            storeVisibilityState(viewId, PortalFrameVisibilityState.fromResult(viewId, result));
        }
        return result;
    }

    private static boolean visibleEnoughThisFrame(ResourceLocation viewId, String stage) {
        if (!SkyesightClientConfig.enablePortalFrustumCulling()) {
            return true;
        }
        PortalFrameVisibilityState state = portalFrameVisibilityStateByView.get(viewId);
        PortalVisibilityCull.PortalVisibilityResult result = state == null ? portalVisibilityThisFrameByView.get(viewId) : state.result();
        return state != null ? state.visibleThisFrame() : result != null && result.visibleEnoughForHeavyRender();
    }

    private static boolean shouldEnforceVisibilityCull(PortalVisibilityCull.PortalVisibilityResult result) {
        return SkyesightClientConfig.enablePortalFrustumCulling()
                && result != null
                && !result.visibleEnoughForHeavyRender();
    }

    private static void storeVisibilityState(ResourceLocation viewId, PortalFrameVisibilityState state) {
        if (viewId != null && state != null) {
            portalFrameVisibilityStateByView.put(viewId, state);
        }
    }

    private static boolean shouldUseStoredVisibilityForActiveStage(String stage) {
        if (!SkyesightClientConfig.enablePortalFrustumCulling() || stage == null) {
            return false;
        }
        return "direct".equals(stage) || "crossdim-storage".equals(stage);
    }

    private static boolean clearPortalStencilForFrame(Minecraft minecraft, int stencilBits) {
        if (stencilBits <= 0) {
            return false;
        }

        try {
            minecraft.getMainRenderTarget().bindWrite(false);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.clearStencil(0);
            RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
            restorePortalStencilFrameState();
            return true;
        } catch (RuntimeException exception) {
            DIAGNOSTICS.lastStencilException = "stencil clear " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
            SecondaryPortalCompositePass.restoreStencilState();
            activePortalStencilRef = 0;
            return false;
        }
    }

    private static void restorePortalStencilFrameState() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        activePortalStencilRef = 0;
    }

    private record RegisteredRenderView(
            RegisteredPortalView definition,
            PortalRenderView renderView,
            String label
    ) {
    }

    private static final DirectStencilFrameDiagnostics DIAGNOSTICS = new DirectStencilFrameDiagnostics();

    public static List<PortalLookMarkerDebugData.PortalLookDebugMarker> portalLookDebugMarkers(Minecraft minecraft, Camera camera) {
        if (minecraft == null || camera == null) {
            return List.of();
        }
        List<PortalLookMarkerDebugData.ViewConfig> viewConfigs = new ArrayList<>();
        ResourceKey<Level> displayDimension = minecraft.level == null ? null : minecraft.level.dimension();
        for (RegisteredRenderView view : activeRenderViews(displayDimension, camera, "look-marker")) {
            viewConfigs.add(new PortalLookMarkerDebugData.ViewConfig(
                    view.label(),
                    view.definition().id(),
                    view.renderView(),
                    view.definition().source().dimension(),
                    view.definition().target().dimension()
            ));
        }
        return PortalLookMarkerDebugData.buildMarkers(viewConfigs, camera);
    }

    private static volatile int compositeFrameId = -1;
    private static volatile int stencilBits;
    private static volatile String directPortalDepthMode = DIRECT_PORTAL_DEPTH_MODE.name();
    private static volatile int activePortalStencilRef;
    private static final PortalSkyCaptureManager PORTAL_SKY_CAPTURE_MANAGER = new PortalSkyCaptureManager();
    private static volatile int portalMaskFrameId = -1;
    private static volatile boolean stencilSurvivedToLateStage;

    private PortalDirectStencilRenderer() {}

    public static boolean portalEntityPoolRenderingEnabled() {
        return ENABLE_PORTAL_ENTITY_POOL_RENDERING;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!RENDER_SECONDARY_PORTAL_COMPOSITE
                || SkyesightSecondaryRenderContext.isActive()) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            onPortalSkyAfterSkyStage(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            onPortalStencilMaskWorldStage(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            updateCrossDimPortalStorage(Minecraft.getInstance(), event.getCamera());
            onPortalDirectLateStage(event);
        }
    }

    private static void onPortalSkyAfterSkyStage(RenderLevelStageEvent event) {
        PortalRenderTargetBounds.beginFrame(event.getRenderTick());
        capturePortalSkyAfterSky(event);

        if (!DIRECT_PORTAL_SKY_AT_AFTER_SKY
                || DIRECT_SKY_MODE == DirectSkyMode.SIMPLE_FILL
                || DIRECT_SKY_MODE == DirectSkyMode.CLONED_VANILLA_CELESTIAL
                || event.getCamera() == null
                || event.getPoseStack() == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (PortalSecondaryWorldRenderer.shaderPackActive()) {
            return;
        }
    }

    private static void capturePortalSkyAfterSky(RenderLevelStageEvent event) {
        if (!DIRECT_SKY_CAPTURE_ENABLED
                || event.getCamera() == null
                || DIRECT_SKY_MODE == DirectSkyMode.SIMPLE_FILL) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (DIRECT_SKY_CAPTURE_MODE == PortalSkyCaptureManager.Mode.MAIN_CAMERA_COPY) {
            PORTAL_SKY_CAPTURE_MANAGER.captureMainCameraCopy(minecraft, event);
            return;
        }

        for (RegisteredRenderView view : activeRenderViews(minecraft.level.dimension(), event.getCamera(), "sky", event.getFrustum(), false)) {
            ResourceLocation viewId = view.definition().id();
            if (shouldSkipStickSkyCapture(view)) {
                continue;
            }
            capturePortalCameraSkyForInstance(
                    event,
                    minecraft,
                    skyCaptureKey(viewId),
                    viewId,
                    view.definition().target().dimension(),
                    view.renderView()
            );
        }

        PORTAL_SKY_CAPTURE_MANAGER.captureMainCameraCopy(minecraft, event);
    }

    private static void capturePortalCameraSkyForInstance(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            String key,
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            PortalRenderView instance
    ) {
        if (instance == null || !instance.renderConfig().enabled() || !instance.renderConfig().rendersView()) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        ClientLevel targetLevel = portalTargetEnvironmentLevel(minecraft, viewId, targetDimension);
        String targetLevelSource = portalTargetEnvironmentLevelSource(minecraft, viewId, targetDimension, targetLevel);
        DirectStencilPortalMath.PortalCameraPose pose = transformPortalViewCamera(
                event.getCamera(),
                instance.entrancePortal(),
                instance.exitPortal(),
                viewId
        );
        var camera = instance.viewContext().camera();
        camera.setup(
                targetLevel == null ? minecraft.level : targetLevel,
                minecraft.player,
                false,
                false,
                partialTick
        );
        camera.setPositionPublic(pose.position());
        camera.setRotationPublic(pose.rotation());
        Matrix4f mainProjection = new Matrix4f(event.getProjectionMatrix());
        Matrix4f mainViewProjection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        var mainTarget = minecraft.getMainRenderTarget();
        PortalRenderTargetBounds.TargetSize skyTargetSize = PortalRenderTargetBounds.resolveTargetSize(
                viewId,
                mainViewProjection,
                instance.entrancePortal(),
                event.getCamera() == null ? null : event.getCamera().getPosition(),
                mainTarget == null ? 0 : mainTarget.width,
                mainTarget == null ? 0 : mainTarget.height,
                mainTarget == null ? 0 : mainTarget.width,
                mainTarget == null ? 0 : mainTarget.height
        );
        SecondaryViewFrame terrainFrame = PortalSecondaryWorldRenderer.createDirectPortalFrameForCapture(
                instance.viewContext(),
                minecraft,
                event,
                pose.position(),
                pose.rotation(),
                instance.entrancePortal(),
                instance.exitPortal(),
                DirectStencilPortalMath.exitClipPlane(instance.exitPortal()),
                mainViewProjection,
                mainProjection,
                "skyCapture:" + key + ":" + event.getRenderTick()
        );

        PORTAL_SKY_CAPTURE_MANAGER.capturePortalCameraSky(
                minecraft,
                event,
                key,
                targetLevel,
                targetLevelSource,
                camera,
                pose.rotation(),
                terrainFrame.projectionMatrix(),
                terrainFrame.diagnostics().projectionSummary(),
                PortalSecondaryWorldRenderer.directPortalProjectionFov(),
                skyTargetSize.width(),
                skyTargetSize.height()
        );
    }

    private static ClientLevel portalTargetEnvironmentLevel(
            Minecraft minecraft,
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension
    ) {
        if (minecraft.level != null
                && targetDimension != null
                && !targetDimension.equals(minecraft.level.dimension())) {
            SkyesightVisualWorld visualWorld =
                    SkyesightVisualWorldManager.getOrCreate(viewId, targetDimension);

            if (visualWorld != null && !visualWorld.isClosed()) {
                return visualWorld.level();
            }
        }

        return minecraft.level;
    }

    private static String portalTargetEnvironmentLevelSource(
            Minecraft minecraft,
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            ClientLevel targetLevel
    ) {
        if (minecraft.level != null
                && targetDimension != null
                && !targetDimension.equals(minecraft.level.dimension())) {
            return targetLevel != null
                    && minecraft.level != null
                    && targetLevel != minecraft.level
                    ? "visualWorld"
                    : "mainClientLevelFallback";
        }

        return "mainClientLevel";
    }

    private static void updateCrossDimPortalStorage(Minecraft minecraft, Camera camera) {
        ResourceKey<Level> displayDimension = minecraft.level == null ? null : minecraft.level.dimension();
        for (RegisteredRenderView view : activeCrossDimRenderViews(displayDimension, camera, "crossdim-storage")) {
            if (!visibleEnoughThisFrame(view.definition().id(), "crossdim-storage")) {
                continue;
            }
            CrossDimPortalViewUpdater.updateStorageForView(
                    minecraft,
                    camera,
                    view.definition(),
                    view.renderView()
            );
        }
    }

    private static void onPortalStencilMaskWorldStage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        resetStencilMaskFrameDiagnosticFields(event.getRenderTick());
        portalMainViewProjectionByView.clear();
        portalMaskWroteThisFrameByView.clear();
        portalVisibilityThisFrameByView.clear();
        portalFrameVisibilityStateByView.clear();
        portalRenderedThisFrameByView.clear();
        PortalRenderTargetBounds.beginFrame(event.getRenderTick());
        flushMainBuffersBeforeMaskIfEnabled(minecraft);

        if (minecraft.level == null
                || minecraft.player == null
                || camera == null
                || event.getPoseStack() == null) {
            return;
        }

        int maskStencilBits = ensureMainTargetStencilBits();
        clearPortalStencilForFrame(minecraft, maskStencilBits);
        Matrix4f mainViewProjection = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(event.getPoseStack().last().pose());

        List<RegisteredRenderView> views = activeRenderViews(minecraft.level.dimension(), camera, "mask", event.getFrustum(), true);
        var mainTarget = minecraft.getMainRenderTarget();
        for (RegisteredRenderView view : views) {
            int stencilRef = view.renderView().renderConfig().stencilRef();
            portalMainViewProjectionByView.put(view.definition().id(), new Matrix4f(mainViewProjection));
            PortalRenderTargetBounds.captureDiagnosticBounds(
                    view.definition().id(),
                    mainViewProjection,
                    view.renderView().entrancePortal(),
                    camera,
                    mainTarget == null ? 0 : mainTarget.width,
                    mainTarget == null ? 0 : mainTarget.height
            );
            SecondaryPortalCompositePass.StencilResult result = SecondaryPortalCompositePass.writeStencilApertureMask(
                    event.getPoseStack(),
                    camera,
                    view.renderView().entrancePortal(),
                    maskStencilBits,
                    stencilRef,
                    false,
                    maskPassUsesDepthTest(),
                    maskPassWritesPortalDepth(),
                    view.renderView().renderConfig().stencilMask(),
                    view.definition().id()
            );
            DIAGNOSTICS.accumulateStencilResult(result);
            portalMaskWroteThisFrameByView.put(view.definition().id(), result.succeeded());
            restorePortalStencilFrameState();
        }
        restorePortalStencilFrameState();
        stencilBits = maskStencilBits;
        DIAGNOSTICS.portalMaskWroteThisFrame = DIAGNOSTICS.stencilSucceeded;
    }

    private static void onPortalDirectLateStage(RenderLevelStageEvent event) {
        resetDirectFrameDiagnosticFields(event.getRenderTick());

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();

        if (minecraft.level == null
                || minecraft.player == null
                || camera == null
                || event.getPoseStack() == null) {
            return;
        }
        PortalSecondaryWorldRenderer.beginPortalRenderFrame();
        PortalSecondaryWorldRenderer.prewarmPortalSodiumRenderersIfNeeded(minecraft);

        MainTerrainStateSnapshot mainTerrainBeforePortalStage =
                SecondarySodiumTerrainPass.captureMainTerrainState(minecraft);
        try {
            List<RegisteredRenderView> views = activeRenderViews(minecraft.level.dimension(), camera, "direct");
            for (RegisteredRenderView view : views) {
                if (!maskWrote(view.definition().id()) || portalMaskFrameId != event.getRenderTick()) {
                    continue;
                }
                if (!visibleEnoughThisFrame(view.definition().id(), "direct")) {
                    continue;
                }
                Matrix4f mainViewProjection = mainViewProjectionFor(view.definition().id(), new Matrix4f());
                renderPortalInstanceDirect(
                        event,
                        camera,
                        view.renderView(),
                        view.renderView().renderConfig().stencilRef(),
                        maskWrote(view.definition().id()),
                        view.label(),
                        mainViewProjection,
                        view.definition().id()
                );
                portalRenderedThisFrameByView.add(view.definition().id());
                MainTerrainStateSnapshot mainTerrainAfterView =
                        SecondarySodiumTerrainPass.captureMainTerrainState(minecraft);
                if (mainTerrainAfterView.mainRenderListsIdentity() != mainTerrainBeforePortalStage.mainRenderListsIdentity()
                        || mainTerrainAfterView.mainRenderListsSize() != mainTerrainBeforePortalStage.mainRenderListsSize()) {
                    DIAGNOSTICS.lastDirectRenderException = "main terrain render lists changed during portal stage";
                    break;
                }
            }
        } catch (Exception exception) {
            String exceptionSummary = "late direct " + exception.getClass().getSimpleName() + ": " + exception.getMessage();

            if (DIAGNOSTICS.recordLateDirectException(exceptionSummary)) {
                Skyesight.LOGGER.warn("[Skyesight] Late direct portal render failed", exception);
            }
        } finally {
            restorePortalStencilFrameState();
        }
    }

    private static void resetStencilMaskFrameDiagnosticFields(int renderTick) {
        activePortalStencilRef = 0;
        stencilBits = 0;
        portalMaskFrameId = renderTick;
        DIAGNOSTICS.resetStencilMaskFrame(PORTAL_MASK_DEPTH_MODE.name());
    }

    private static void resetDirectFrameDiagnosticFields(int renderTick) {
        stencilBits = 0;
        directPortalDepthMode = DIRECT_PORTAL_DEPTH_MODE.name();
        compositeFrameId = renderTick;
        stencilSurvivedToLateStage = false;
        DIAGNOSTICS.resetDirectFrame(
                PORTAL_SECONDARY_ROTATION_MODE.name(),
                STENCIL_MASK_AT_WORLD_STAGE
        );
    }




    private static boolean useStableTerrainInvocationPath(ResourceLocation viewId) {
        return USE_STABLE_TERRAIN_INVOCATION_PATH;
    }

    private static int directTerrainRadiusForRender(ResourceLocation viewId, PortalRenderView instance) {
        return instance.renderConfig().terrainChunkRadius();
    }

    private static boolean directTerrainEnabledForRender(ResourceLocation viewId, PortalRenderView instance) {
        return useStableTerrainInvocationPath(viewId)
                ? !DIRECT_DISABLE_PORTAL_TERRAIN
                : instance.renderConfig().renderTerrain() && !DIRECT_DISABLE_PORTAL_TERRAIN;
    }

    private static boolean directSkyEnabledForTerrainFrame(ResourceLocation viewId, PortalRenderView instance) {
        // Direct current-target portal views composite captured sky through the portal stencil.
        return false;
    }

    private static void renderCrossDimPortalSlotsInSharedCompositor(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            String portalName,
            ResourceLocation regionId,
            ResourceKey<Level> targetDimension,
            PortalRenderView instance,
            DirectStencilPortalMath.PortalCameraPose directPose,
            Quaternionf selectedRotation,
            SkyesightClipPlane directClipPlane,
            Matrix4f mainViewProjection,
            String beforeTerrainState,
            int directStencilBits,
            int stencilRef
    ) {
        SecondaryViewFrame frame = PortalSecondaryWorldRenderer.createDirectPortalFrameForCapture(
                instance.viewContext(),
                minecraft,
                event,
                directPose.position(),
                selectedRotation,
                instance.entrancePortal(),
                instance.exitPortal(),
                directClipPlane,
                mainViewProjection,
                new Matrix4f(event.getProjectionMatrix()),
                "direct:" + skyCaptureKey(regionId) + ":cross-dim:" + compositeFrameId
        );

        if (instance.renderConfig().renderEntities()) {
            CrossDimPortalViewUpdater.updateEntityWatchRegion(
                    minecraft,
                    regionId,
                    targetDimension,
                    directPose.position(),
                    instance.renderConfig().entityChunkRadius() * 16.0D,
                    instance.renderConfig().entityChunkRadius()
            );
        } else {
            CrossDimPortalViewUpdater.removeEntityWatchRegion(minecraft, regionId);
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        int portalContentFramebufferBeforeTerrain = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        if (instance.renderConfig().renderTerrain()) {
            renderCrossDimPortalTerrain(
                    portalName,
                    regionId,
                    targetDimension,
                    instance,
                    frame,
                    partialTick,
                    beforeTerrainState,
                    instance.renderConfig().renderTranslucent()
            );
        }

        int portalContentFramebufferAfterTerrain = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (portalContentFramebufferAfterTerrain != portalContentFramebufferBeforeTerrain) {
            Skyesight.LOGGER.error(
                    "[Skyesight] Cross-dim portal terrain left wrong framebuffer bound viewId={} expected={} actual={} rebinding visible portal framebuffer",
                    regionId,
                    portalContentFramebufferBeforeTerrain,
                    portalContentFramebufferAfterTerrain
            );
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, portalContentFramebufferBeforeTerrain);
        }

        if (instance.renderConfig().renderBlockEntities()) {
            beginPortalStencilReadOrThrow(directStencilBits, stencilRef);
            renderCrossDimPortalVisualBlockEntities(
                    portalName,
                    regionId,
                    targetDimension,
                    frame,
                    partialTick
            );
        }

        if (instance.renderConfig().renderEntities()) {
            beginPortalStencilReadOrThrow(directStencilBits, stencilRef);
            renderCrossDimPortalVisualEntities(
                    portalName,
                    regionId,
                    targetDimension,
                    instance,
                    frame,
                    partialTick,
                    portalContentFramebufferBeforeTerrain
            );
        }

        if (instance.renderConfig().renderParticles()) {
            renderCrossDimPortalParticlesIfEnabled(
                    portalName,
                    regionId,
                    targetDimension,
                    frame,
                    partialTick,
                    portalContentFramebufferBeforeTerrain,
                    stencilRef
            );
        }
    }

    private static void renderPortalInstanceDirect(
            RenderLevelStageEvent event,
            Camera mainCamera,
            PortalRenderView instance,
            int stencilRef,
            boolean maskWrote,
            String portalName,
            Matrix4f mainViewProjection,
            ResourceLocation regionId
    ) {
        ResourceLocation behaviorViewId = regionId;
        RegisteredPortalView registeredView = behaviorViewId == null ? null : SkyesightPortalApi.getPortal(behaviorViewId.toString());
        boolean crossDimView = registeredView != null && registeredView.isCrossDimension();
        ResourceKey<Level> targetDimension = registeredView == null ? null : registeredView.target().dimension();
        Minecraft minecraft = Minecraft.getInstance();
        DirectMainState state = DirectMainState.capture(minecraft);
        DIAGNOSTICS.directRenderAttempted = true;
        if (stencilRef <= 0) {
            logInvalidPortalStencilRef(behaviorViewId, stencilRef);
            DIAGNOSTICS.directRenderSucceeded = false;
            DIAGNOSTICS.lastDirectRenderException = "invalid stencilRef " + stencilRef;
            return;
        }
        activePortalStencilRef = stencilRef;
        int maskWriteRef = instance.renderConfig().stencilRef();
        DIAGNOSTICS.framebufferBeforeDirect = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        int directStencilBits = ensureMainTargetStencilBits();
        boolean useExistingStencil = STENCIL_MASK_AT_WORLD_STAGE;
        if (!instance.renderConfig().enabled() || !instance.renderConfig().rendersView()) {
            return;
        }

        if (!viewHasRenderableContent(instance.renderConfig())) {
            DIAGNOSTICS.directRenderSucceeded = true;
            DIAGNOSTICS.lastDirectRenderException = "";
            return;
        }

        if (maskWriteRef != stencilRef) {
            return;
        }

        if (!useExistingStencil || !maskWrote || portalMaskFrameId != event.getRenderTick()) {
            return;
        }

        minecraft.getMainRenderTarget().bindWrite(false);
        stencilSurvivedToLateStage = useExistingStencil
                && maskWrote
                && portalMaskFrameId == event.getRenderTick();

        SecondaryPortalCompositePass.StencilResult stencil;

        if (useExistingStencil) {
            DIAGNOSTICS.directRenderUsedExistingStencil = true;
            stencil = stencilSurvivedToLateStage
                    ? SecondaryPortalCompositePass.beginExistingStencilApertureRead(directStencilBits, stencilRef)
                    : new SecondaryPortalCompositePass.StencilResult(true, false, true, directStencilBits, "world-stage stencil mask missing");
        } else {
            DIAGNOSTICS.directRenderUsedExistingStencil = false;
            DIAGNOSTICS.directMaskStage = "AFTER_LEVEL";
            DIAGNOSTICS.apertureDrawUsedEventPoseStack = true;
            DIAGNOSTICS.apertureDrawUsedManualCameraMatrix = false;
            stencil = SecondaryPortalCompositePass.beginStencilAperture(
                    event.getPoseStack(),
                    mainCamera,
                    instance.entrancePortal(),
                    directStencilBits
            );
        }

        stencilBits = stencil.stencilBits();
        DIAGNOSTICS.recordStencilResult(stencil);

        if (!stencil.succeeded() || stencil.fallbackUsed()) {
            DIAGNOSTICS.directRenderFallbackUsed = true;
            if (stencilBits <= 0 && DIAGNOSTICS.shouldLogStencilUnavailable()) {
                Skyesight.LOGGER.warn("[Skyesight] Portal direct stencil render unavailable; stencilBits=0");
            }
            return;
        }

        try {
            beginPortalStencilReadOrThrow(directStencilBits, stencilRef);
            if (DIRECT_DISABLE_ALL_PORTAL_SUBPASSES_AFTER_MASK) {
                DIAGNOSTICS.directRenderSucceeded = true;
                DIAGNOSTICS.lastDirectRenderException = "";
                SecondaryPortalCompositePass.restoreStencilState();
                restoreMainStateAfterDirect(minecraft, state);
                restorePortalViewportState(state);
                return;
            }
            PortalViewPlacement placement = PortalFrameMath.placeCamera(
                    mainCamera,
                    instance.entrancePortal(),
                    instance.exitPortal()
            );
            DirectStencilPortalMath.PortalCameraPose directPose = transformPortalViewCamera(
                    mainCamera,
                    instance.entrancePortal(),
                    instance.exitPortal(),
                    behaviorViewId
            );
            SkyesightClipPlane directClipPlane = DirectStencilPortalMath.exitClipPlane(instance.exitPortal());
            Quaternionf transformedMainRotation = new Quaternionf(directPose.rotation());
            Quaternionf exitRenderRotation = PortalFrameMath.portalRenderRotation(instance.exitPortal());
            ProjectionBasis projectionBasis = projectionBasis(instance.exitPortal());
            Quaternionf selectedRotation = resolveDirectCameraRotation(
                    transformedMainRotation,
                    exitRenderRotation,
                    projectionBasis
            );
            renderDirectSkyIfEnabled(
                    event,
                    minecraft,
                    instance,
                    behaviorViewId,
                    portalName,
                    stencilRef,
                    maskWrote,
                    directPose,
                    selectedRotation,
                    mainViewProjection
            );
            boolean renderSkyInCurrentTarget = directSkyEnabledForTerrainFrame(behaviorViewId, instance);
            if (!viewNeedsDirectPostSkyPass(instance.renderConfig())) {
                DIAGNOSTICS.directRenderSucceeded = true;
                DIAGNOSTICS.lastDirectRenderException = "";
                DIAGNOSTICS.instancesRendered++;
                SecondaryPortalCompositePass.restoreStencilState();
                restoreMainStateAfterDirect(minecraft, state);
                restorePortalViewportState(state);
                return;
            }
            beginPortalStencilReadOrThrow(directStencilBits, stencilRef);
            if (!DIRECT_DISABLE_PORTAL_DEPTH_CLEAR) {
                clearDirectPortalDepthIfEnabled();
            } else {
                DIAGNOSTICS.directPortalDepthClearRan = false;
            }
            if (DIRECT_SKY_MASK_ONLY) {
                DIAGNOSTICS.directRenderSucceeded = true;
                DIAGNOSTICS.lastDirectRenderException = "";
                DIAGNOSTICS.instancesRendered++;
                return;
            }

            beginPortalStencilReadOrThrow(directStencilBits, stencilRef);

            if (DIRECT_PORTAL_RENDER_MODE == DirectPortalRenderDebugMode.SOLID_COLOR_ONLY
                    || DIRECT_STENCIL_DRAW_PROOF_COLOR
                    || !DIRECT_RENDER_TERRAIN) {
                beginPortalStencilReadOrThrow(directStencilBits, stencilRef);
                SecondaryPortalCompositePass.drawStencilMagentaProof();
            }

            if (DIRECT_PORTAL_RENDER_MODE == DirectPortalRenderDebugMode.SOLID_COLOR_ONLY
                    || !DIRECT_STENCIL_RENDER_TERRAIN
                    || !DIRECT_RENDER_TERRAIN
                    || DIRECT_DISABLE_PORTAL_TERRAIN) {
                DIAGNOSTICS.directRenderSucceeded = true;
                DIAGNOSTICS.lastDirectRenderException = "";
                DIAGNOSTICS.instancesRendered++;
                SecondaryPortalCompositePass.restoreStencilState();
                restoreMainStateAfterDirect(minecraft, state);
                restorePortalViewportState(state);
                return;
            }

            if (DIRECT_STENCIL_RENDER_TERRAIN) {
                DIAGNOSTICS.directTerrainRan = true;
                DIAGNOSTICS.directRenderUsedPortalContext = true;
                applyDirectDepthMode();
                PortalScreenRect screenRect = computePortalScreenRect(
                        mainViewProjection,
                        instance.entrancePortal(),
                        minecraft.getWindow().getWidth(),
                        minecraft.getWindow().getHeight()
                );
                DIAGNOSTICS.portalScreenRect = screenRect.toString();
                DIAGNOSTICS.portalViewportRestored = true;
                if (crossDimView && targetDimension != null) {
                    renderCrossDimPortalSlotsInSharedCompositor(
                            event,
                            minecraft,
                            portalName,
                            behaviorViewId,
                            targetDimension,
                            instance,
                            directPose,
                            selectedRotation,
                            directClipPlane,
                            mainViewProjection,
                            "-",
                            directStencilBits,
                            stencilRef
                    );
                } else {
                    String secondaryFrameKey = "direct:" + skyCaptureKey(behaviorViewId) + ":" + compositeFrameId;
                    PortalSecondaryWorldRenderer.renderSecondaryViewDirectToCurrentTargetFromPose(
                        instance.viewContext(),
                        minecraft,
                        event,
                        directPose.position(),
                        selectedRotation,
                        instance.entrancePortal(),
                        instance.exitPortal(),
                        mainViewProjection,
                        new Matrix4f(event.getProjectionMatrix()),
                        directClipPlane,
                        directEntityRegionEnabled(behaviorViewId, instance) && instance.renderConfig().renderEntities(),
                        behaviorViewId == null ? directEntityRegionId(portalName) : behaviorViewId,
                        directEntityRegionEnabled(behaviorViewId, instance) && instance.renderConfig().renderEntities(),
                        secondaryFrameKey,
                        renderSkyInCurrentTarget,
                        stencilRef,
                        directTerrainRadiusForRender(behaviorViewId, instance),
                        instance.renderConfig().portalOwnedRenderRadiusChunks(),
                        instance.renderConfig().sameDimPlayerLoadedReuseRadiusChunks(),
                        instance.renderConfig().reusePlayerLoadedChunksForSameDim(),
                        instance.renderConfig().entityChunkRadius(),
                        instance.renderConfig().blockEntityChunkRadius(),
                        instance.renderConfig().blockUpdateChunkRadius(),
                        directTerrainEnabledForRender(behaviorViewId, instance),
                        instance.renderConfig().renderTranslucent() && !DIRECT_DISABLE_PORTAL_TRANSPARENT,
                        instance.renderConfig().renderEntities() && !DIRECT_DISABLE_PORTAL_ENTITIES,
                        instance.renderConfig().renderBlockEntities() && !DIRECT_DISABLE_PORTAL_BLOCK_ENTITIES
                    );
                    renderMainForegroundParticlesOverPortalIfEnabled(
                            event,
                            minecraft,
                            mainCamera,
                            instance,
                            directStencilBits,
                            stencilRef,
                            portalName
                    );
                }
            }
            DIAGNOSTICS.directRenderSucceeded = true;
            DIAGNOSTICS.lastDirectRenderException = "";
            DIAGNOSTICS.recordSecondaryTargetBindObservation(
                    PortalSecondaryWorldRenderer.secondaryContextNonSecondaryTargetBindCount(),
                    PortalSecondaryWorldRenderer.secondaryContextLastNonSecondaryBind()
            );
            DIAGNOSTICS.instancesRendered++;
        } catch (RuntimeException exception) {
            DIAGNOSTICS.directRenderSucceeded = false;
            DIAGNOSTICS.directRenderFailed = true;
            DIAGNOSTICS.directRenderFallbackUsed = true;
            DIAGNOSTICS.lastDirectRenderException = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        } finally {
            DIAGNOSTICS.accumulateSecondaryTargetBindObservation(
                    PortalSecondaryWorldRenderer.secondaryContextNonSecondaryTargetBindCount(),
                    PortalSecondaryWorldRenderer.secondaryContextLastNonSecondaryBind()
            );
            DIAGNOSTICS.framebufferAfterDirect = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            restoreDirectDepthMode();
            SecondaryPortalCompositePass.restoreStencilState();
            restoreMainStateAfterDirect(minecraft, state);
            restorePortalViewportState(state);
        }
    }

    private static boolean renderCrossDimPortalTerrain(
            String portalName,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            PortalRenderView instance,
            SecondaryViewFrame frame,
            float partialTick,
            String beforeTerrainDepthState,
            boolean renderTranslucent
    ) {
        SkyesightVisualWorld visualWorld = SkyesightVisualWorldManager.getOrCreate(regionId, dimension);

        if (visualWorld == null || visualWorld.isClosed()) {
            return false;
        }

        DirectSkyFillStateScope scope = DirectSkyFillStateScope.capture();
        int glBefore = GL11.glGetError();
        int framebufferBeforeTerrain = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            SkyesightLightTextureUpdater.updateFor(visualWorld.level(), frame.camera(), partialTick);
            visualWorld.renderTerrain(
                    frame.camera(),
                    frame.frustum(),
                    frame.modelViewMatrix(),
                    frame.projectionMatrix(),
                    instance.renderConfig().terrainChunkRadius(),
                    renderTranslucent
            );
        } finally {
            SkyesightLightTextureUpdater.restoreMain(partialTick);
            scope.restore();
        }

        int glAfter = GL11.glGetError();
        int loadedChunks = visualWorld.level().getChunkSource().getLoadedChunksCount();
        int visibleSections = visualWorld.visibleChunkCount();
        return loadedChunks > 0 && (visibleSections > 0 || glAfter == GL11.GL_NO_ERROR);
    }

    private static void renderCrossDimPortalVisualBlockEntities(
            String portalName,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            SecondaryViewFrame frame,
            float partialTick
    ) {
        SkyesightVisualWorld visualWorld = SkyesightVisualWorldManager.get(regionId);
        if (visualWorld == null || visualWorld.isClosed()) {
            return;
        }

        int storedCount = visualWorld.chunkReceiver().countBlockEntities();

        if (storedCount <= 0) {
            return;
        }

        try {
            SkyesightLightTextureUpdater.updateFor(visualWorld.level(), frame.camera(), partialTick);
            visualWorld.renderBlockEntities(
                    regionId,
                    frame.camera(),
                    frame.modelViewMatrix(),
                    frame.projectionMatrix(),
                    partialTick
            );
        } catch (RuntimeException exception) {
            throw exception;
        } finally {
            SkyesightLightTextureUpdater.restoreMain(partialTick);
        }
    }

    private static void renderCrossDimPortalVisualEntities(
            String portalName,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            PortalRenderView instance,
            SecondaryViewFrame frame,
            float partialTick,
            int expectedFramebufferId
    ) {
        SkyesightVisualWorld visualWorld = SkyesightVisualWorldManager.get(regionId);

        if (visualWorld == null || visualWorld.isClosed()) {
            return;
        }

        Vec3 cameraPos = frame.camera().getPosition();
        double radiusBlocks = instance.renderConfig().entityChunkRadius() * 16.0D;
        AABB renderBounds = new AABB(
                cameraPos.x() - radiusBlocks,
                visualWorld.level().getMinBuildHeight(),
                cameraPos.z() - radiusBlocks,
                cameraPos.x() + radiusBlocks,
                visualWorld.level().getMaxBuildHeight(),
                cameraPos.z() + radiusBlocks
        );
        List<PortalRenderableEntity> renderableEntities;
        int poolEntityCount = (ENABLE_PORTAL_ENTITY_POOL_RENDERING || SkyesightDebugConfig.SOURCE_MAP)
                ? SkyesightPortalEntityPool.count(regionId, dimension)
                : 0;
        int snapshotEntityCount = SkyesightDebugConfig.SOURCE_MAP ? visualWorld.entityStore().size() : 0;
        String renderSource;
        String renderSourceReason;
        boolean snapshotSuppressed = false;
        if (ENABLE_PORTAL_ENTITY_POOL_RENDERING) {
            renderableEntities = PortalDimensionEntitySources.renderablePortalEntityPoolForDimension(
                    regionId,
                    dimension,
                    renderBounds,
                    frame.frustum(),
                    visualWorld
            );
            if (renderableEntities.isEmpty()) {
                renderSource = "snapshot";
                renderSourceReason = "pool_empty_or_ineligible";
                renderableEntities = PortalDimensionEntitySources.renderableVisualEntitiesForDimension(
                        regionId,
                        visualWorld,
                        dimension,
                        renderBounds,
                        frame.frustum()
                );
            } else {
                renderSource = "portal_entity_pool";
                renderSourceReason = "pool_enabled_and_eligible";
                snapshotSuppressed = true;
            }
        } else {
            renderSource = "snapshot";
            renderSourceReason = "pool_rendering_disabled";
            renderableEntities = PortalDimensionEntitySources.renderableVisualEntitiesForDimension(
                    regionId,
                    visualWorld,
                    dimension,
                    renderBounds,
                    frame.frustum()
            );
        }
        logCrossDimEntityRenderSource(
                regionId,
                renderSource,
                renderSourceReason,
                snapshotSuppressed,
                poolEntityCount,
                snapshotEntityCount
        );

        SecondaryEntityPass.renderPortalEntities(
                frame,
                Minecraft.getInstance(),
                visualWorld.level(),
                renderableEntities,
                instance.renderConfig().entityChunkRadius(),
                partialTick,
                false,
                true,
                false,
                expectedFramebufferId
        );
    }

    private static final Set<String> CROSS_DIM_ENTITY_SOURCE_LOGGED = new HashSet<>();

    private static void clearCrossDimEntitySourceLog(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        String prefix = viewId + ":";
        CROSS_DIM_ENTITY_SOURCE_LOGGED.removeIf(key -> key.startsWith(prefix));
    }

    private static void logCrossDimEntityRenderSource(
            ResourceLocation regionId,
            String renderSource,
            String reason,
            boolean snapshotSuppressed,
            int poolEntityCount,
            int snapshotEntityCount
    ) {
        if (!SkyesightDebugConfig.SOURCE_MAP) {
            return;
        }
        String key = regionId + ":" + renderSource + ":" + reason + ":" + snapshotSuppressed + ":" + ENABLE_PORTAL_ENTITY_POOL_RENDERING;
        if (!CROSS_DIM_ENTITY_SOURCE_LOGGED.add(key)) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] CROSS_DIM_ENTITY_RENDER_SOURCE view={} renderSource={} reason={} populationEnabled={} poolRenderingEnabled={} poolEntityCount={} snapshotEntityCount={} snapshotSuppressed={}",
                regionId,
                renderSource,
                reason,
                SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled(),
                ENABLE_PORTAL_ENTITY_POOL_RENDERING,
                poolEntityCount,
                snapshotEntityCount,
                snapshotSuppressed
        );
    }

    private static void renderCrossDimPortalParticlesIfEnabled(
            String portalName,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            SecondaryViewFrame frame,
            float partialTick,
            int expectedFramebufferId,
            int stencilRef
    ) {
        if (!CROSS_DIM_PORTAL_PARTICLES_ENABLED) {
            return;
        }

        SkyesightVisualWorld visualWorld = SkyesightVisualWorldManager.getOrCreate(regionId, dimension);

        if (visualWorld == null || visualWorld.isClosed()) {
            return;
        }

        beginPortalStencilReadOrThrow(ensureMainTargetStencilBits(), stencilRef);
        PortalVisualDisplayTickDriver.Result displayTickResult = PortalVisualDisplayTickDriver.tick(
                regionId,
                "cross-dim",
                visualWorld.level(),
                visualWorld.particles(),
                frame.camera().getPosition()
        );
        SecondaryParticlePass.renderVisualWorldParticles(
                frame,
                Minecraft.getInstance(),
                visualWorld.level(),
                visualWorld.particles(),
                partialTick,
                SecondaryParticlePass.RenderGroup.TRANSLUCENT,
                expectedFramebufferId,
                stencilRef
        );
    }

    private static void renderMainForegroundParticlesOverPortalIfEnabled(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            Camera mainCamera,
            PortalRenderView instance,
            int directStencilBits,
            int stencilRef,
            String portalName
    ) {
        if (!PORTAL_MAIN_PARTICLE_OCCLUSION_FIX || "E".equals(portalName)) {
            return;
        }

        int framebufferBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        SecondaryParticlePass.Result result;

        try {
            beginPortalStencilReadOrThrow(directStencilBits, stencilRef);
            result = SecondaryParticlePass.renderMainCameraForegroundOverlay(
                    minecraft,
                    mainCamera,
                    new Matrix4f(event.getProjectionMatrix()),
                    new Matrix4f(event.getModelViewMatrix()),
                    instance.entrancePortal(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(true),
                    stencilRef
            );
        } catch (RuntimeException exception) {
            result = new SecondaryParticlePass.Result(
                    true,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "-",
                    "-",
                    SecondaryParticlePass.RenderGroup.FOREGROUND_OVERLAY.name(),
                    framebufferBefore,
                    stencilRef,
                    "-",
                    "-",
                    GL11.GL_NO_ERROR,
                    GL11.glGetError(),
                    0,
                    0,
                    0,
                    0,
                    "-"
            );
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) != framebufferBefore) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
            }
        }

    }
    private static boolean directEntityRegionEnabled(ResourceLocation viewId, PortalRenderView instance) {
        if (!DIRECT_RENDER_ENTITIES) {
            return false;
        }

        if (instance == null || !instance.renderConfig().renderEntities()) {
            return false;
        }

        return !DIRECT_RENDER_ENTITIES_ONE_PORTAL_ONLY;
    }



    private static boolean renderDirectSkyIfEnabled(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            PortalRenderView instance,
            ResourceLocation viewId,
            String portalName,
            int stencilRef,
            boolean maskWroteThisFrame,
            DirectStencilPortalMath.PortalCameraPose directPose,
            Quaternionf selectedRotation,
            Matrix4f mainViewProjection
    ) {
        if (!instance.renderConfig().enabled()) {
            return false;
        }

        if (!instance.renderConfig().rendersView()) {
            return false;
        }

        if (!instance.renderConfig().renderSky()) {
            return false;
        }

        if (DIRECT_DISABLE_PORTAL_SKY_FILL) {
            return false;
        }

        if (DIRECT_SKY_MODE != DirectSkyMode.SIMPLE_FILL
                && DIRECT_SKY_MODE != DirectSkyMode.CLONED_VANILLA_CELESTIAL) {
            return false;
        }

        if (DIRECT_SKY_MODE == DirectSkyMode.SIMPLE_FILL && !DIRECT_RENDER_SIMPLE_SKY_FILL) {
            return false;
        }

        if (DIRECT_SKY_MODE == DirectSkyMode.SIMPLE_FILL && DIRECT_SKY_DISABLE_SIMPLE_PREFILL) {
            return false;
        }

        if (DIRECT_RENDER_SKY_ONE_PORTAL_ONLY) {
            return false;
        }

        if (!maskWroteThisFrame || portalMaskFrameId != event.getRenderTick()) {
            return false;
        }

        SecondaryPortalCompositePass.StencilResult read =
                SecondaryPortalCompositePass.beginExistingStencilApertureRead(stencilBits, stencilRef);
        int activeReadRef = read.succeeded() && !read.fallbackUsed() ? currentStencilRef() : -1;
        if (!read.succeeded() || read.fallbackUsed()) {
            return false;
        }

        if (activeReadRef != stencilRef) {
            return false;
        }

        DirectSkyFillStateScope state = DirectSkyFillStateScope.capture();
        boolean drawn = false;
        try {
            beginPortalStencilReadOrThrow(stencilBits, stencilRef);
            if (DIRECT_SKY_MODE == DirectSkyMode.CLONED_VANILLA_CELESTIAL) {
                if (shouldSkipStickSkyComposite(viewId)) {
                    return false;
                }
                drawn = renderDirectCapturedSkyComposite(
                        event,
                        minecraft,
                        viewId,
                        stencilRef,
                        directPose
                );
            } else {
                Vec3 color = directPortalSkyColor(event, minecraft, directPose);
                SecondaryPortalCompositePass.fillStencilApertureColor(
                        (float) color.x(),
                        (float) color.y(),
                        (float) color.z(),
                        1.0F
                );
                drawn = true;
            }
            drainGlErrors();
        } catch (RuntimeException exception) {
            drainGlErrors();
        } finally {
            boolean stateRestored = state.restore();
            if (!stateRestored) {
                Skyesight.LOGGER.error("[Skyesight] Direct portal sky fill state restore mismatch portal={} state={}", portalName, state.restoreSummary());
            }
            try {
                beginPortalStencilReadOrThrow(stencilBits, stencilRef);
            } catch (RuntimeException ignored) {
                // The terrain/depth path will reassert and report stencil state again.
            }
        }

        return drawn;
    }

    private static Vec3 directPortalSkyColor(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            DirectStencilPortalMath.PortalCameraPose directPose
    ) {
        if (minecraft.level == null) {
            return new Vec3(0.28D, 0.48D, 0.82D);
        }

        return minecraft.level.getSkyColor(
                directPose.position(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true)
        );
    }

    private static boolean renderDirectCapturedSkyComposite(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            ResourceLocation viewId,
            int stencilRef,
            DirectStencilPortalMath.PortalCameraPose directPose
    ) {
        String captureKey = skyCaptureKey(viewId);
        PortalSkyCaptureManager.Capture capture = PORTAL_SKY_CAPTURE_MANAGER.capture(captureKey);
        if (capture == null || !capture.validForFrame(event.getRenderTick())) {
            capture = PORTAL_SKY_CAPTURE_MANAGER.capture("main");
        }
        boolean frameMatches = capture != null && capture.validForFrame(event.getRenderTick());
        int captureTextureId = capture == null ? -1 : capture.textureId();
        boolean textureReady = captureTextureId > 0;
        boolean drawn = false;

        if (DIRECT_SKY_CAPTURE_COMPOSITE_ENABLED && frameMatches && textureReady) {
            try {
                boolean stencilOk;
                if (DIRECT_SKY_CAPTURE_BYPASS_STENCIL) {
                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                    stencilOk = true;
                } else {
                    stencilOk = directStencilReadOk(stencilRef);
                }

                if (stencilOk) {
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    if (PORTAL_SKY_COMPOSITE_OPAQUE) {
                        RenderSystem.disableBlend();
                    } else {
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                    }
                    RenderSystem.disableCull();
                    RenderSystem.setShader(GameRenderer::getPositionTexShader);
                    RenderSystem.setShaderTexture(0, captureTextureId);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    SecondaryPortalCompositePass.drawFullscreenTexture(captureTextureId);
                    drainGlErrors();
                    drawn = true;
                }
            } catch (RuntimeException exception) {
                drainGlErrors();
            }
        }

        if (!drawn && DIRECT_SKY_CAPTURE_FALLBACK_SIMPLE_COLOR) {
            try {
                beginPortalStencilReadOrThrow(stencilBits, stencilRef);
                Vec3 color = directPortalSkyColor(event, minecraft, directPose);
                SecondaryPortalCompositePass.fillStencilApertureColor(
                        (float) color.x(),
                        (float) color.y(),
                        (float) color.z(),
                        1.0F
                );
                drainGlErrors();
                drawn = true;
            } catch (RuntimeException exception) {
                drainGlErrors();
            }
        }
        return drawn;
    }




    private static boolean directStencilReadOk(int stencilRef) {
        SecondaryPortalCompositePass.StencilResult stencil =
                SecondaryPortalCompositePass.beginExistingStencilApertureRead(stencilBits, stencilRef);
        return stencil.succeeded()
                && !stencil.fallbackUsed()
                && currentStencilRef() == stencilRef;
    }

    private static void beginPortalStencilReadOrThrow(int stencilBits, int stencilRef) {
        SecondaryPortalCompositePass.StencilResult stencil =
                SecondaryPortalCompositePass.beginExistingStencilApertureRead(stencilBits, stencilRef);

        if (!stencil.succeeded() || stencil.fallbackUsed()) {
            throw new IllegalStateException("stencil read failed: " + stencil.exception());
        }
    }

    private static int currentStencilRef() {
        try {
            return GL11.glGetInteger(GL11.GL_STENCIL_REF);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static String drainGlErrors() {
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) {
                break;
            }

            if (!errors.isEmpty()) {
                errors.append(',');
            }

            errors.append(error);
        }

        return errors.isEmpty() ? "none" : errors.toString();
    }



    private static final class DirectSkyFillStateScope {
        private final int framebuffer;
        private final int[] viewport;
        private final Matrix4f projection;
        private final VertexSorting vertexSorting;
        private final Matrix4f modelView;
        private final boolean depthTest;
        private final int depthFunc;
        private final boolean depthMask;
        private final boolean[] colorMask;
        private final boolean stencilTest;
        private final int stencilFunc;
        private final int stencilRef;
        private final int stencilValueMask;
        private final int stencilWriteMask;
        private final int stencilFail;
        private final int stencilPassDepthFail;
        private final int stencilPassDepthPass;
        private final boolean blend;
        private final boolean cull;
        private final float[] shaderColor;
        private final ShaderInstance shader;
        private String restoreSummary = "not restored";

        private DirectSkyFillStateScope(
                int framebuffer,
                int[] viewport,
                Matrix4f projection,
                VertexSorting vertexSorting,
                Matrix4f modelView,
                boolean depthTest,
                int depthFunc,
                boolean depthMask,
                boolean[] colorMask,
                boolean stencilTest,
                int stencilFunc,
                int stencilRef,
                int stencilValueMask,
                int stencilWriteMask,
                int stencilFail,
                int stencilPassDepthFail,
                int stencilPassDepthPass,
                boolean blend,
                boolean cull,
                float[] shaderColor,
                ShaderInstance shader
        ) {
            this.framebuffer = framebuffer;
            this.viewport = viewport;
            this.projection = projection;
            this.vertexSorting = vertexSorting;
            this.modelView = modelView;
            this.depthTest = depthTest;
            this.depthFunc = depthFunc;
            this.depthMask = depthMask;
            this.colorMask = colorMask;
            this.stencilTest = stencilTest;
            this.stencilFunc = stencilFunc;
            this.stencilRef = stencilRef;
            this.stencilValueMask = stencilValueMask;
            this.stencilWriteMask = stencilWriteMask;
            this.stencilFail = stencilFail;
            this.stencilPassDepthFail = stencilPassDepthFail;
            this.stencilPassDepthPass = stencilPassDepthPass;
            this.blend = blend;
            this.cull = cull;
            this.shaderColor = shaderColor;
            this.shader = shader;
        }

        private static DirectSkyFillStateScope capture() {
            return new DirectSkyFillStateScope(
                    GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                    currentViewport(),
                    new Matrix4f(RenderSystem.getProjectionMatrix()),
                    RenderSystem.getVertexSorting(),
                    new Matrix4f(RenderSystem.getModelViewStack()),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    currentColorMask(),
                    GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                    GL11.glGetInteger(GL11.GL_STENCIL_FUNC),
                    GL11.glGetInteger(GL11.GL_STENCIL_REF),
                    GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK),
                    GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_STENCIL_FAIL),
                    GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL),
                    GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    RenderSystem.getShaderColor().clone(),
                    RenderSystem.getShader()
            );
        }

        private boolean restore() {
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
                RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
                RenderSystem.setProjectionMatrix(this.projection, this.vertexSorting);

                var modelViewStack = RenderSystem.getModelViewStack();
                modelViewStack.identity();
                modelViewStack.mul(this.modelView);
                RenderSystem.applyModelViewMatrix();

                if (this.depthTest) {
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.disableDepthTest();
                }
                RenderSystem.depthFunc(this.depthFunc);
                RenderSystem.depthMask(this.depthMask);
                RenderSystem.colorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);

                if (this.stencilTest) {
                    GL11.glEnable(GL11.GL_STENCIL_TEST);
                } else {
                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                }
                RenderSystem.stencilMask(this.stencilWriteMask);
                RenderSystem.stencilFunc(this.stencilFunc, this.stencilRef, this.stencilValueMask);
                RenderSystem.stencilOp(this.stencilFail, this.stencilPassDepthFail, this.stencilPassDepthPass);

                if (this.blend) {
                    RenderSystem.enableBlend();
                } else {
                    RenderSystem.disableBlend();
                }

                if (this.cull) {
                    RenderSystem.enableCull();
                } else {
                    RenderSystem.disableCull();
                }
                RenderSystem.setShaderColor(this.shaderColor[0], this.shaderColor[1], this.shaderColor[2], this.shaderColor[3]);
                if (this.shader != null) {
                    RenderSystem.setShader(() -> this.shader);
                }
                boolean restored = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == this.framebuffer
                        && viewportMatches(currentViewport(), this.viewport)
                        && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) == this.depthMask;
                this.restoreSummary = restored ? "yes" : "partial";
                return restored;
            } catch (RuntimeException exception) {
                this.restoreSummary = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                return false;
            }
        }

        private String restoreSummary() {
            return this.restoreSummary;
        }
    }

    private static boolean[] currentColorMask() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
        return new boolean[] {
                buffer.get(0) != 0,
                buffer.get(1) != 0,
                buffer.get(2) != 0,
                buffer.get(3) != 0
        };
    }



    private static boolean viewportMatches(int[] left, int[] right) {
        return left != null
                && right != null
                && left.length == right.length
                && left[0] == right[0]
                && left[1] == right[1]
                && left[2] == right[2]
                && left[3] == right[3];
    }

    private static void flushMainBuffersBeforeMaskIfEnabled(Minecraft minecraft) {
        DIAGNOSTICS.portalMaskBufferFlushAttempted = false;
        DIAGNOSTICS.portalMaskBufferFlushException = "";

        if (!FLUSH_MAIN_BUFFERS_BEFORE_PORTAL_MASK) {
            return;
        }

        DIAGNOSTICS.portalMaskBufferFlushAttempted = true;

        try {
            minecraft.renderBuffers().bufferSource().endBatch();
        } catch (RuntimeException exception) {
            DIAGNOSTICS.portalMaskBufferFlushException = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
    }

    private static boolean maskPassUsesDepthTest() {
        return PORTAL_MASK_DEPTH_MODE != PortalMaskDepthMode.NO_DEPTH_TEST;
    }

    private static boolean maskPassWritesPortalDepth() {
        return PORTAL_MASK_DEPTH_MODE == PortalMaskDepthMode.DEPTH_TEST_WITH_DEPTH_WRITE;
    }

    private static ResourceLocation directEntityRegionId(String portalName) {
        String safeName = portalName == null || portalName.isBlank()
                ? "unknown"
                : portalName.toLowerCase(Locale.ROOT);
        return ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, safeName);
    }


    private static void clearDirectPortalDepthIfEnabled() {
        DIAGNOSTICS.directPortalDepthClearRan = false;

        if (DIRECT_PORTAL_DEPTH_MODE != DirectPortalDepthMode.CLEAR_PORTAL_DEPTH_THEN_LEQUAL) {
            return;
        }

        SecondaryPortalCompositePass.clearStencilApertureDepthToFar();
        DIAGNOSTICS.directPortalDepthClearRan = true;
    }

    private static PortalScreenRect computePortalScreenRect(
            Matrix4f mainViewProjection,
            PortalFrame portal,
            int framebufferWidth,
            int framebufferHeight
    ) {
        Vec3 center = portal.position();
        Vec3 right = PortalFrameMath.right(portal).scale(-1.0D);
        Vec3 up = PortalFrameMath.up(portal);
        double halfWidth = portal.width() * 0.5D;
        double halfHeight = portal.height() * 0.5D;
        Vec3 bottomLeftWorld = center.subtract(right.scale(halfWidth)).subtract(up.scale(halfHeight));
        Vec3 bottomRightWorld = center.add(right.scale(halfWidth)).subtract(up.scale(halfHeight));
        Vec3 topRightWorld = center.add(right.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 topLeftWorld = center.subtract(right.scale(halfWidth)).add(up.scale(halfHeight));
        Vector3f bottomLeft = projectPortalCorner(
                mainViewProjection,
                bottomLeftWorld
        );
        Vector3f bottomRight = projectPortalCorner(
                mainViewProjection,
                bottomRightWorld
        );
        Vector3f topRight = projectPortalCorner(
                mainViewProjection,
                topRightWorld
        );
        Vector3f topLeft = projectPortalCorner(
                mainViewProjection,
                topLeftWorld
        );
        if (bottomLeft == null || bottomRight == null || topRight == null || topLeft == null) {
            DIAGNOSTICS.portalCornerNdc = "invalid";
            return PortalScreenRect.invalid();
        }

        float minX = Math.min(Math.min(bottomLeft.x, bottomRight.x), Math.min(topRight.x, topLeft.x));
        float maxX = Math.max(Math.max(bottomLeft.x, bottomRight.x), Math.max(topRight.x, topLeft.x));
        float minY = Math.min(Math.min(bottomLeft.y, bottomRight.y), Math.min(topRight.y, topLeft.y));
        float maxY = Math.max(Math.max(bottomLeft.y, bottomRight.y), Math.max(topRight.y, topLeft.y));

        DIAGNOSTICS.portalCornerNdc = String.format(
                Locale.ROOT,
                "bl %.2f,%.2f br %.2f,%.2f tr %.2f,%.2f tl %.2f,%.2f",
                bottomLeft.x,
                bottomLeft.y,
                bottomRight.x,
                bottomRight.y,
                topRight.x,
                topRight.y,
                topLeft.x,
                topLeft.y
        );

        int x = clamp((int) Math.floor((minX * 0.5F + 0.5F) * framebufferWidth), 0, framebufferWidth);
        int y = clamp((int) Math.floor((minY * 0.5F + 0.5F) * framebufferHeight), 0, framebufferHeight);
        int rightPixel = clamp((int) Math.ceil((maxX * 0.5F + 0.5F) * framebufferWidth), 0, framebufferWidth);
        int topPixel = clamp((int) Math.ceil((maxY * 0.5F + 0.5F) * framebufferHeight), 0, framebufferHeight);
        int width = Math.max(1, rightPixel - x);
        int height = Math.max(1, topPixel - y);

        return new PortalScreenRect(x, y, width, height, true);
    }

    private static Vector3f projectPortalCorner(Matrix4f viewProjection, Vec3 worldPosition) {
        Vector4f clip = new Vector4f(
                (float) worldPosition.x(),
                (float) worldPosition.y(),
                (float) worldPosition.z(),
                1.0F
        ).mul(viewProjection);

        if (Math.abs(clip.w) < 1.0E-5F || clip.w <= 0.0F) {
            return null;
        }

        return new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static DirectStencilPortalMath.PortalCameraPose transformPortalViewCamera(
            Camera sourceCamera,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            ResourceLocation viewId
    ) {
        DirectStencilPortalMath.PortalCameraPose pose = DirectStencilPortalMath.transformCamera(
                sourceCamera,
                entrancePortal,
                exitPortal
        );
        return applyPortalCameraExitPush(pose, exitPortal, viewId);
    }

    private static DirectStencilPortalMath.PortalCameraPose applyPortalCameraExitPush(
            DirectStencilPortalMath.PortalCameraPose pose,
            PortalFrame exitPortal,
            ResourceLocation viewId
    ) {
        if (pose == null || PORTAL_CAMERA_EXIT_PUSH_EPSILON_BLOCKS <= 0.0F) {
            return pose;
        }

        Vec3 exitNormal = DirectStencilPortalMath.normal(exitPortal);
        if (exitNormal.lengthSqr() < 1.0E-8D) {
            return pose;
        }

        Vec3 normalizedExitNormal = exitNormal.normalize();
        Vec3 pushedPosition = pose.position().add(normalizedExitNormal.scale(PORTAL_CAMERA_EXIT_PUSH_EPSILON_BLOCKS));
        return new DirectStencilPortalMath.PortalCameraPose(pushedPosition, pose.rotation());
    }

    private static Quaternionf resolveDirectCameraRotation(
            Quaternionf transformedMainRotation,
            Quaternionf exitRenderRotation,
            ProjectionBasis projectionBasis
    ) {
        if (DIRECT_PORTAL_CAMERA_MODE == DirectPortalCameraMode.LEGACY_EXACT) {
            return new Quaternionf(exitRenderRotation);
        }
        if (DIRECT_PORTAL_CAMERA_MODE == DirectPortalCameraMode.PORTAL_TRANSFORM) {
            return new Quaternionf(transformedMainRotation);
        }

        return switch (PORTAL_SECONDARY_ROTATION_MODE) {
            case TRANSFORM_MAIN_CAMERA -> transformedMainRotation;
            case OLD_EXIT_PORTAL_RENDER_ROTATION -> new Quaternionf(exitRenderRotation);
            case PROJECTION_BASIS_ALIGNED -> rotationFromCameraBasis(
                    projectionBasis.right(),
                    projectionBasis.up(),
                    projectionBasis.forward()
            );
        };
    }




    private static ProjectionBasis projectionBasis(PortalFrame exitPortal) {
        Vec3 projectionRight = PortalSecondaryWorldRenderer.directPortalProjectionHandedness()
                .equals("UNFLIPPED_RIGHT")
                ? PortalFrameMath.right(exitPortal)
                : PortalFrameMath.right(exitPortal).scale(-1.0D);
        Vec3 projectionUp = PortalFrameMath.up(exitPortal);
        Vec3 projectionForward = projectionRight.cross(projectionUp).scale(-1.0D).normalize();

        return new ProjectionBasis(
                projectionRight.normalize(),
                projectionUp.normalize(),
                projectionForward
        );
    }

    private static Quaternionf rotationFromCameraBasis(Vec3 right, Vec3 up, Vec3 forward) {
        Vec3 back = forward.scale(-1.0D);
        Matrix3f rotation = new Matrix3f(
                (float) right.x(), (float) right.y(), (float) right.z(),
                (float) up.x(), (float) up.y(), (float) up.z(),
                (float) back.x(), (float) back.y(), (float) back.z()
        );

        return rotation.getNormalizedRotation(new Quaternionf()).normalize();
    }

    private static void restorePortalViewportState(DirectMainState state) {
        RenderSystem.disableScissor();
        RenderSystem.viewport(
                state.viewport()[0],
                state.viewport()[1],
                state.viewport()[2],
                state.viewport()[3]
        );
        DIAGNOSTICS.portalViewportRestored = true;
    }

    private static void applyDirectDepthMode() {
        DIAGNOSTICS.beforeDirectDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        DIAGNOSTICS.beforeDirectDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        DIAGNOSTICS.directPortalDepthClearRan = false;

        switch (DIRECT_PORTAL_DEPTH_MODE) {
            case DISABLE_DEPTH_TEST -> {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            }
            case ALWAYS_NO_WRITE -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_ALWAYS);
                RenderSystem.depthMask(false);
            }
            case CLEAR_PORTAL_DEPTH_THEN_LEQUAL -> {
                DIAGNOSTICS.directPortalDepthClearRan = false;
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
            }
            case RESPECT_MAIN_DEPTH -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
            }
        }
    }

    private static void restoreDirectDepthMode() {
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void restoreMainStateAfterDirect(Minecraft minecraft, DirectMainState state) {
        try {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setProjectionMatrix(state.projection(), state.vertexSorting());

            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.identity();
            modelViewStack.mul(state.modelView());
            RenderSystem.applyModelViewMatrix();

            minecraft.getMainRenderTarget().bindWrite(false);
            RenderSystem.viewport(
                    state.viewport()[0],
                    state.viewport()[1],
                    state.viewport()[2],
                    state.viewport()[3]
            );

            DIAGNOSTICS.afterDirectStencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            DIAGNOSTICS.afterDirectColorMaskRestored = true;
            DIAGNOSTICS.afterDirectDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            DIAGNOSTICS.afterDirectDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            DIAGNOSTICS.afterDirectProjectionRestored = matricesMatch(RenderSystem.getProjectionMatrix(), state.projection());
            DIAGNOSTICS.afterDirectModelViewRestored = true;
            DIAGNOSTICS.afterDirectFramebufferRestored = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == minecraft.getMainRenderTarget().frameBufferId;
            int[] viewport = currentViewport();
            DIAGNOSTICS.afterDirectViewportRestored = viewport[0] == state.viewport()[0]
                    && viewport[1] == state.viewport()[1]
                    && viewport[2] == state.viewport()[2]
                    && viewport[3] == state.viewport()[3];
            DIAGNOSTICS.lastStateRestoreException = "";
        } catch (RuntimeException exception) {
            DIAGNOSTICS.afterDirectProjectionRestored = false;
            DIAGNOSTICS.afterDirectModelViewRestored = false;
            DIAGNOSTICS.afterDirectFramebufferRestored = false;
            DIAGNOSTICS.afterDirectViewportRestored = false;
            DIAGNOSTICS.lastStateRestoreException = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
    }

    private static int[] currentViewport() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return viewport;
    }

    private static boolean matricesMatch(Matrix4f left, Matrix4f right) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                if (Float.floatToIntBits(left.get(column, row)) != Float.floatToIntBits(right.get(column, row))) {
                    return false;
                }
            }
        }

        return true;
    }

    private static int ensureMainTargetStencilBits() {
        Minecraft minecraft = Minecraft.getInstance();

        if (!minecraft.getMainRenderTarget().isStencilEnabled()) {
            minecraft.getMainRenderTarget().enableStencil();
        }

        return minecraft.getMainRenderTarget().isStencilEnabled() ? 8 : 0;
    }

    private record DirectMainState(
            Matrix4f projection,
            VertexSorting vertexSorting,
            Matrix4f modelView,
            int[] viewport
    ) {
        private static DirectMainState capture(Minecraft minecraft) {
            int[] viewport = currentViewport();
            if (viewport[2] <= 0 || viewport[3] <= 0) {
                viewport = new int[] {
                        0,
                        0,
                        minecraft.getWindow().getWidth(),
                        minecraft.getWindow().getHeight()
                };
            }

            return new DirectMainState(
                    new Matrix4f(RenderSystem.getProjectionMatrix()),
                    RenderSystem.getVertexSorting(),
                    new Matrix4f(RenderSystem.getModelViewStack()),
                    viewport
            );
        }
    }

    private record PortalScreenRect(int x, int y, int width, int height, boolean valid) {
        private static PortalScreenRect invalid() {
            return new PortalScreenRect(0, 0, 0, 0, false);
        }
    }

    private record ProjectionBasis(Vec3 right, Vec3 up, Vec3 forward) {}

    public static int instancesRendered() {
        return DIAGNOSTICS.instancesRendered;
    }

    public static boolean scheduleDirectPortalSecondaryBlockUpdate(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        boolean scheduled = false;
        for (RegisteredPortalView view : SkyesightPortalApi.getAllPortals()) {
            if (!shouldScheduleSameDimBlockUpdateForView(view, pos)) {
                continue;
            }
            if (!isWithinPortalRenderDistanceForClientUpdate(view, "block-update")) {
                continue;
            }

            SecondaryViewContext context = PORTAL_VIEW_CONTEXTS.get(view.id());
            if (context == null) {
                continue;
            }
            scheduled |= scheduleContextBlockUpdate(view.id(), context, pos);
        }
        return scheduled;
    }

    public static boolean scheduleDirectPortalSecondaryTerrainUpdate() {
        boolean scheduled = false;
        for (RegisteredPortalView view : SkyesightPortalApi.getAllPortals()) {
            if (!isActiveSameDimRenderView(view)) {
                continue;
            }
            if (!isWithinPortalRenderDistanceForClientUpdate(view, "terrain-update")) {
                continue;
            }

            SecondaryViewContext context = PORTAL_VIEW_CONTEXTS.get(view.id());
            if (context == null) {
                continue;
            }
            scheduled |= flushPendingContextBlockUpdates(view.id(), context);
            scheduled |= scheduleContextTerrainUpdate(view.id(), context);
        }
        return scheduled;
    }

    private static boolean shouldScheduleSameDimBlockUpdateForView(RegisteredPortalView view, BlockPos pos) {
        if (!isActiveSameDimRenderView(view) || pos == null) {
            return false;
        }

        PortalRenderSettings settings = view.renderSettings();
        int radius = settings == null ? 0 : Math.max(0, settings.blockUpdateChunkRadius());
        ChunkPos updatedChunk = new ChunkPos(pos);
        ChunkPos targetChunk = new ChunkPos(BlockPos.containing(view.target().center()));
        return Math.abs(updatedChunk.x - targetChunk.x) <= radius
                && Math.abs(updatedChunk.z - targetChunk.z) <= radius;
    }

    private static boolean isActiveSameDimRenderView(RegisteredPortalView view) {
        if (view == null || view.id() == null || view.source() == null || view.target() == null) {
            return false;
        }

        PortalRenderSettings settings = view.renderSettings();
        return settings != null
                && settings.enabled()
                && settings.rendersView()
                && view.active()
                && settings.renderTerrain()
                && view.source().dimension().equals(view.target().dimension());
    }

    private static boolean isWithinPortalRenderDistanceForClientUpdate(RegisteredPortalView view, String stage) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft == null || minecraft.gameRenderer == null ? null : minecraft.gameRenderer.getMainCamera();
        boolean inside = PortalRenderDistanceGate.shouldRenderPortalFromCamera(
                minecraft,
                view,
                camera,
                SkyesightClientConfig.portalRenderDistanceBlocks()
        );
        PortalRenderDistanceGate.logDecisionIfDue(
                minecraft,
                view,
                camera,
                SkyesightClientConfig.portalRenderDistanceBlocks(),
                inside,
                stage,
                inside ? "client-render-update-within-distance" : "client-render-update-too-far"
        );
        return inside;
    }

    private static boolean scheduleContextBlockUpdate(ResourceLocation viewId, SecondaryViewContext context, BlockPos pos) {
        return SecondarySodiumTerrainPass.scheduleBlockUpdate(
                context,
                pos,
                DEFAULT_MAX_PENDING_PORTAL_BLOCK_UPDATE_CHUNKS
        );
    }

    private static boolean scheduleContextTerrainUpdate(ResourceLocation viewId, SecondaryViewContext context) {
        boolean scheduled = SecondarySodiumTerrainPass.scheduleTerrainUpdate(context);
        if (scheduled) {
            flushPendingContextBlockUpdates(viewId, context);
        }
        return scheduled;
    }

    private static boolean flushPendingContextBlockUpdates(ResourceLocation viewId, SecondaryViewContext context) {
        return SecondarySodiumTerrainPass.flushPendingBlockUpdates(
                context,
                DEFAULT_MAX_PENDING_PORTAL_BLOCK_UPDATES_FLUSH_PER_FRAME
        );
    }

    public static int stencilBits() {
        return stencilBits;
    }

    public static String directPortalDepthMode() {
        return directPortalDepthMode;
    }

    public static String lastDirectRenderException() {
        return DIAGNOSTICS.lastDirectRenderException;
    }

    public static boolean farPortalRenderBlockEntities() {
        return FAR_PORTAL_RENDER_BLOCK_ENTITIES;
    }

    private enum DirectSkyMode {
        SIMPLE_FILL,
        CLONED_VANILLA_CELESTIAL
    }

    private enum DirectPortalRenderDebugMode {
        SOLID_COLOR_ONLY,
        TERRAIN_NORMAL_PERSPECTIVE
    }

    private enum DirectPortalDepthMode {
        DISABLE_DEPTH_TEST,
        ALWAYS_NO_WRITE,
        CLEAR_PORTAL_DEPTH_THEN_LEQUAL,
        RESPECT_MAIN_DEPTH
    }

    private enum PortalMaskDepthMode {
        DEPTH_TEST_NO_DEPTH_WRITE,
        DEPTH_TEST_WITH_DEPTH_WRITE,
        NO_DEPTH_TEST
    }

    private enum PortalSecondaryRotationMode {
        TRANSFORM_MAIN_CAMERA,
        OLD_EXIT_PORTAL_RENDER_ROTATION,
        PROJECTION_BASIS_ALIGNED
    }

    private enum DirectPortalCameraMode {
        PORTAL_TRANSFORM,
        LEGACY_EXACT,
        FIXED_ROTATION_MODE
    }
}
