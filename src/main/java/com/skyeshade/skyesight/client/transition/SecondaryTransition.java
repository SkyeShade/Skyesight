package com.skyeshade.skyesight.client.transition;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.SkyesightTransition;
import com.skyeshade.skyesight.client.render.*;
import com.skyeshade.skyesight.client.view.SkyesightInternalCamera;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Bounded secondary-scene presentation; never promotes a level or transfers chunk ownership. */
public final class SecondaryTransition implements SkyesightTransition {
    private static SecondaryTransition active;
    private static SecondaryTransition successor;
    private static boolean capturing;
    private final ResourceLocation viewId;
    private final ResourceKey<Level> source;
    private final BooleanSupplier validView;
    private final Object connection;
    private Status status = Status.PREPARING;
    private Destination destination;
    private TextureTarget image;
    private long deadline = Util.getMillis() + 10_000;
    private boolean authoritative, renderedDestination;
    private boolean awaitingDestinationPosition;
    private long presentationStarted;
    private boolean live;
    private SecondarySceneFrame retainedScene;
    private final java.util.Map<Object, Runnable> deferredCloses = new java.util.IdentityHashMap<>();
    private com.skyeshade.skyesight.api.RegisteredPortalView portal;
    private long lastPresentNanos;
    private double worstFrameMs;
    private int liveFrames;
    private static long frameStart;
    private static long previousFrameStart, frameInterval;
    private static double normalFrameNanos = 16_666_667;
    private final double stableFrameBudget = Math.max(16_666_667, normalFrameNanos * 1.5);
    private static boolean presentedThisFrame;
    private boolean chunksReported;
    private ReceivingLevelScreen suppressedLoadingScreen;
    private final java.util.Map<Long, net.minecraft.world.level.chunk.LevelChunk> collisionChunks = new java.util.HashMap<>();
    private java.util.Set<BlockPos> visibleTerrain = java.util.Set.of();
    private int coverageReadyFrames;
    private boolean physicalFrameObserved;
    private boolean validatePhysicalPortals;
    private java.util.Set<BlockPos> previousPhysicalCoverage = java.util.Set.of();
    private int lastMissingCoverage = -1;
    public static net.minecraft.world.level.CollisionGetter collisionView(Level level, net.minecraft.world.entity.Entity entity) {
        var t = active;
        var mc = Minecraft.getInstance();
        if (t == null || !t.presenting() || !t.live || !t.authoritative || entity != mc.player
                || level != mc.level || !level.dimension().equals(t.destination.dimension()) || t.collisionChunks.isEmpty()) return level;
        return new SecondaryCollisionView(mc.level, t.collisionChunks);
    }
    public static void frameStarted() {
        frameStart = System.nanoTime();
        frameInterval = previousFrameStart == 0 ? 0 : frameStart - previousFrameStart;
        previousFrameStart = frameStart;
        if ((active == null || !active.presenting()) && frameInterval > 0 && frameInterval < 250_000_000)
            normalFrameNanos += (frameInterval - normalFrameNanos) * .05;
        presentedThisFrame = false;
        if (active != null) {
            if (!active.physicalFrameObserved) active.coverageReadyFrames = 0;
            active.physicalFrameObserved = false;
            active.renderedDestination = false;
        }
    }
    public static boolean shouldRenderPhysical() {
        var t = active;
        if (t == null || !t.presenting() || !t.live) return true;
        if (t.expired() || !t.validView.getAsBoolean()) {
            if (active == t) t.finish(Status.FALLBACK);
            return true;
        }
        var mc = Minecraft.getInstance();
        if (!t.authoritative || mc.player == null || mc.level == null || !mc.level.dimension().equals(t.destination.dimension())) return false;
        var center = mc.player.chunkPosition();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (SecondaryCollisionView.received(mc.level, center.x + x, center.z + z) == null) return false;
        return true;
    }
    public static void trace(String event) {
        if (active != null && active.presenting() && Boolean.getBoolean("skyesight.debugTraversal"))
            Skyesight.LOGGER.info("[LiveTimeline] event={} time={} level={}", event, Util.getMillis(),
                    Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension().location());
    }
    public static void tickRetained() {
        var t = active;
        var scene = t == null ? null : t.retainedScene;
        if (t != null && t.presenting() && t.live && scene != null && !Minecraft.getInstance().isPaused()) {
            var world = scene.visualWorld();
            if (world != null && !world.isClosed()
                    && com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager.get(t.viewId) != world) world.tick(t.viewId);
        }
    }

    public static boolean renderingPrimaryPresentation() { return capturing && active != null && active.live && active.presenting(); }
    public static boolean warmingPhysicalTerrain() {
        var t = active;
        return t != null && t.presenting() && t.live && !capturing && !t.validatePhysicalPortals;
    }
    private boolean presenting() { return status == Status.PRESENTING || status == Status.PREDICTING; }
    public static boolean deferClose(Object resource, Runnable close) {
        var t = active;
        if (t == null || !t.presenting() || !t.live || t.retainedScene == null) return false;
        var world = t.retainedScene.visualWorld();
        if (resource != t.retainedScene.context() && resource != world
                && (world == null || resource != world.particles())) return false;
        t.deferredCloses.put(resource, close);
        return true;
    }
    public static boolean concealsLoadingScreen() {
        return active != null && active.status == Status.PRESENTING && active.live && active.retainedScene != null
                && active.image != null && active.validView.getAsBoolean() && !active.expired();
    }
    public static boolean suppressLoadingScreen(ReceivingLevelScreen screen) {
        if (!concealsLoadingScreen()) return false;
        active.suppressedLoadingScreen = screen;
        return true;
    }
    @Override public boolean beginLive() {
        RenderSystem.assertOnRenderThread();
        if (status == Status.PREDICTING) {
            if (expired() || !validView.getAsBoolean()) {
                if (active == this) finish(Status.FALLBACK);
                return false;
            }
            status = Status.PRESENTING; deadline = Util.getMillis() + 10_000; return true;
        }
        live = true;
        return begin();
    }
    @Override public boolean predictCrossing() {
        live = true;
        if (!begin()) return false;
        status = Status.PREDICTING;
        deadline = Util.getMillis() + 750;
        return true;
    }

    private SecondaryTransition(ResourceLocation id, BooleanSupplier validView) {
        var mc = Minecraft.getInstance();
        this.viewId = Objects.requireNonNull(id);
        this.validView = validView;
        this.source = mc.level == null ? null : mc.level.dimension();
        this.connection = mc.getConnection();
        this.portal = com.skyeshade.skyesight.api.SkyesightPortalApi.getPortal(id.toString());
    }
    public static SkyesightTransition prepare(ResourceLocation id, BooleanSupplier validView) {
        RenderSystem.assertOnRenderThread();
        boolean retainPresentation = canPrepareSuccessor();
        if (successor != null) successor.close();
        if (active != null && !retainPresentation) active.close();
        var prepared = new SecondaryTransition(id, validView);
        if (retainPresentation) successor = prepared;
        else active = prepared;
        if (prepared.source == null || !validView.getAsBoolean()) prepared.finish(Status.FALLBACK);
        return prepared;
    }
    public static boolean canPrepareSuccessor() {
        return active != null && active.presenting() && active.authoritative && !active.awaitingDestinationPosition;
    }
    public static boolean preparingSuccessor() { return successor != null && successor.status == Status.PREPARING; }
    public static void closeAll() {
        if (successor != null) successor.close();
        if (active != null) active.close();
    }
    @Override public Status status() { return status; }
    @Override public Destination destination() { return destination; }
    @Override public boolean begin() {
        RenderSystem.assertOnRenderThread();
        if (status != Status.READY || expired()) return false;
        var mc = Minecraft.getInstance();
        if (!validView.getAsBoolean() || mc.level == null || !mc.level.dimension().equals(source)) {
            finish(Status.FALLBACK); return false;
        }
        if (live && portal != null && mc.player != null) {
            // Walking backwards can leave the aperture outside the frustum, so its last captured
            // camera may be several blocks old. The warmed scene is still valid; refresh its pose.
            var camera = new com.skyeshade.skyesight.client.view.SkyesightMutableCamera();
            camera.setRotationPublic(mc.player.getYRot(), mc.player.getXRot(), 0);
            var rotation = com.skyeshade.skyesight.portal.PortalTraversalMath.rotation(portal.source().rotation(), portal.target().rotation());
            destination = new Destination(portal.target().dimension(),
                    com.skyeshade.skyesight.portal.PortalTraversalMath.position(portal.source(), portal.target(), mc.player.getEyePosition()),
                    rotation.mul(new org.joml.Quaternionf(camera.rotation())), destination.projection());
        }
        if (active != this) {
            if (active != null) active.finish(Status.COMPLETE);
            active = this;
            if (successor == this) successor = null;
        }
        status = Status.PRESENTING;
        if (live && retainedScene != null) {
            var center = new net.minecraft.world.level.ChunkPos(BlockPos.containing(destination.eyePosition()));
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var chunk = SecondaryCollisionView.received(retainedScene.level(), center.x + x, center.z + z);
                if (chunk != null) collisionChunks.put(chunk.getPos().toLong(), chunk);
            }
        }
        presentationStarted = Util.getMillis();
        deadline = Util.getMillis() + 10_000; // Failure bound, never a completion criterion.
        Skyesight.LOGGER.debug("Transition begin view={} eye={} rotation={} projection={}", viewId,
                destination.eyePosition(), destination.rotation(), destination.projection());
        return true;
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (status == Status.PREPARING || status == Status.READY || presenting()) finish(Status.CANCELLED);
    }
    private void finish(Status result) {
        if (presentationStarted != 0 && Boolean.getBoolean("skyesight.debugTraversal"))
            Skyesight.LOGGER.info("[Traversal] handoff={} durationMs={} view={}", result, Util.getMillis() - presentationStarted, viewId);
        status = result;
        if (image != null) { image.destroyBuffers(); image = null; }
        if (active == this) active = null;
        if (successor == this) successor = null;
        retainedScene = null;
        collisionChunks.clear();
        visibleTerrain = java.util.Set.of();
        previousPhysicalCoverage = java.util.Set.of();
        var releases = java.util.List.copyOf(deferredCloses.values()); deferredCloses.clear();
        for (var release : releases) {
            try { release.run(); }
            catch (RuntimeException failure) { Skyesight.LOGGER.warn("Unable to release transition resource view={}", viewId, failure); }
        }
        var mc = Minecraft.getInstance();
        if (result != Status.COMPLETE && suppressedLoadingScreen != null && mc.screen == null
                && mc.getConnection() == connection && mc.level != null) mc.setScreen(suppressedLoadingScreen);
        suppressedLoadingScreen = null;
        if (presentationStarted != 0 && Boolean.getBoolean("skyesight.debugTraversal"))
            Skyesight.LOGGER.info("[LiveTransition] result={} liveFrames={} worstPresentationIntervalMs={}", result, liveFrames, worstFrameMs);
    }
    private boolean expired() {
        if (Util.getMillis() > deadline || Minecraft.getInstance().getConnection() != connection) {
            finish(Status.FALLBACK);
            return true;
        }
        return false;
    }

    /** A prepared reverse view can become ready before its aperture enters the frustum. */
    public static void captureStandby(SecondarySceneFrame scene) {
        // Bootstrap a newly selected reverse transition without waiting for stencil visibility.
        // Once a real portal frame is ready, never overwrite its pose with the standby pose.
        if (successor != null && successor.status == Status.PREPARING
                || active != null && active.status == Status.PREPARING) capture(scene);
    }
    /** Called only after the selected shared scene has successfully rendered. */
    public static void capture(SecondarySceneFrame original) {
        var transition = successor != null && successor.viewId.equals(original.viewId()) ? successor : active;
        if (capturing || transition == null || (transition.status != Status.PREPARING && transition.status != Status.READY)
                || !transition.viewId.equals(original.viewId()) || transition.expired()) return;
        var mc = Minecraft.getInstance();
        var center = new net.minecraft.world.level.ChunkPos(BlockPos.containing(original.view().camera().getPosition()));
        if (!transition.validView.getAsBoolean() || !original.options().terrain()
                || SecondaryCollisionView.received(original.level(), center.x, center.z) == null) return;
        if (original.visualWorld() != null ? original.visualWorld().visibleChunkCount() == 0
                : !SecondarySodiumTerrainPass.hasRenderedTerrain(original.context(), original.level())) return;
        transition.retainedScene = original;
        render(transition, original, original.view().camera().getPosition(), original.view().camera().rotation(), original.partialTick());
    }

    private static void render(SecondaryTransition transition, SecondarySceneFrame original,
            net.minecraft.world.phys.Vec3 position, org.joml.Quaternionf rotation, float partialTick) {
        var mc = Minecraft.getInstance();
        var saved = SecondarySceneOutputState.capture();
        capturing = true;
        try {
            int width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
            if (transition.image == null) {
                transition.image = new TextureTarget(width, height, true, Minecraft.ON_OSX);
                if (transition.portal != null) transition.image.enableStencil();
            }
            else if (transition.image.width != width || transition.image.height != height) transition.image.resize(width, height, Minecraft.ON_OSX);
            transition.image.bindWrite(true);
            RenderSystem.disableScissor();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            var camera = new SkyesightInternalCamera();
            camera.minecraftCamera().setup(original.level(), mc.player, false, false, partialTick);
            camera.setPosition(position);
            camera.setRotation(rotation);
            // Handoff takes over the player's viewport, including its FOV/aspect and bob phase.
            // A CCTV feed's custom FOV must not become a one-frame zoom before the physical view.
            var physicalCamera = ((com.skyeshade.skyesight.mixin.client.GameRendererStateAccessor) mc.gameRenderer).skyesight$getMainCameraField();
            var setup = (com.skyeshade.skyesight.mixin.client.GameRendererSetupInvoker) mc.gameRenderer;
            var projection = mc.gameRenderer.getProjectionMatrix(setup.skyesight$getFov(physicalCamera, partialTick, true));
            var bob = new com.mojang.blaze3d.vertex.PoseStack();
            setup.skyesight$bobHurt(bob, partialTick);
            if (mc.options.bobView().get()) setup.skyesight$bobView(bob, partialTick);
            projection.mul(bob.last().pose()); // Deliberately no exit oblique clipping.
            var model = new org.joml.Matrix4f().rotation(new org.joml.Quaternionf(rotation).conjugate());
            var frame = new SecondaryViewFrame(camera.minecraftCamera(), transition.image, width, height,
                    projection, model, projection, SkyesightFrustumFactory.create(camera.minecraftCamera(), model, projection));
            var policy = original.options();
            int radius = PlayerPerspectiveViews.radius(original.viewId(), policy.terrainRadius());
            var options = new SecondarySceneOptions(radius, policy.entityRadius(), policy.blockEntityRadius(),
                    policy.terrain(), policy.translucent(), policy.blockEntities(), policy.entities(), policy.particles());
            frame.diagnostics().setRenderToCurrentTarget(true);
            frame.diagnostics().setCameraView(true);
            frame.diagnostics().setRenderTerrain(original.options().terrain());
            frame.diagnostics().setRenderTranslucent(original.options().translucent());
            frame.diagnostics().setEntityWatchRegionId(original.viewId());
            frame.diagnostics().setTerrainChunkRadius(radius);
            frame.diagnostics().setPortalOwnedRenderRadiusChunks(PlayerPerspectiveViews.radius(original.viewId(), original.view().diagnostics().portalOwnedRenderRadiusChunks()));
            frame.diagnostics().setSameDimPlayerLoadedReuseRadiusChunks(original.view().diagnostics().sameDimPlayerLoadedReuseRadiusChunks());
            frame.diagnostics().setReusePlayerLoadedChunksForSameDim(original.view().diagnostics().reusePlayerLoadedChunksForSameDim());
            var scene = new SecondarySceneFrame(original.viewId(), original.level(), original.visualWorld(), frame,
                    original.context(), partialTick, transition.image, () -> {}, options);
            SecondarySceneEnvironmentRenderer.renderBackground(scene, original.context().clouds());
            if (!SecondarySceneRenderer.renderContents(scene)) {
                transition.finish(Status.FALLBACK); return;
            }
            if (transition.portal != null && transition.presenting() && transition.authoritative)
                com.skyeshade.skyesight.client.portal.PortalDirectStencilRenderer.renderPrimaryPresentation(scene);
            SkyesightCameraOutput.makeOpaque(transition.image);
            if (transition.live) transition.visibleTerrain = SecondaryTerrainCoverage.visible(scene);
            transition.destination = new Destination(original.level().dimension(), camera.position(), camera.rotation(), projection);
            if (!transition.presenting()) {
                transition.status = Status.READY; transition.deadline = Util.getMillis() + 10_000;
            }
        } catch (RuntimeException failure) {
            transition.finish(Status.FALLBACK);
            Skyesight.LOGGER.warn("Unable to prepare transition view={}", transition.viewId, failure);
        } finally {
            capturing = false;
            saved.restore();
        }
    }

    public static void incomingDimension(ResourceKey<Level> dimension) {
        trace("incomingDimension");
        if (active != null && active.presenting() && !active.destination.dimension().equals(dimension))
            active.finish(Status.FALLBACK);
        else if (active != null && active.presenting()) active.awaitingDestinationPosition = true;
    }
    public static void authoritativePosition() {
        var t = active;
        var mc = Minecraft.getInstance();
        if (t == null || !t.presenting()) return;
        if (mc.level == null || mc.player == null || !t.destination.dimension().equals(mc.level.dimension())
                || mc.player.getEyePosition().distanceToSqr(t.destination.eyePosition()) > 4) {
            t.finish(Status.FALLBACK);
            return;
        }
        t.authoritative = true;
        t.awaitingDestinationPosition = false;
        trace("authoritativePosition");
    }
    public static void afterWorldFrame() {
        var t = active;
        var mc = Minecraft.getInstance();
        if (t == null || !t.presenting() || !t.authoritative || mc.level == null || mc.player == null
                || !mc.level.dimension().equals(t.destination.dimension())) return;
        // Consecutive readiness must be established anew after any failed physical frame.
        int previousReadyFrames = t.coverageReadyFrames;
        t.coverageReadyFrames = 0;
        t.physicalFrameObserved = true;
        var center = mc.player.chunkPosition();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (SecondaryCollisionView.received(mc.level, center.x + x, center.z + z) == null) return;
        if (!t.chunksReported) { t.chunksReported = true; trace("requiredChunksObserved"); }
        if (!mc.level.isOutsideBuildHeight(mc.player.blockPosition()) && !mc.levelRenderer.isSectionCompiled(mc.player.blockPosition())) return;
        if (!mc.levelRenderer.hasRenderedAllSections()) return;
        if (mc.levelRenderer.countRenderedSections() == 0) return;
        var camera = mc.gameRenderer.getMainCamera();
        if (!t.live && (camera.getPosition().distanceToSqr(t.destination.eyePosition()) > 4
                || Math.abs(camera.rotation().dot(t.destination.rotation())) < Math.cos(Math.toRadians(5) / 2))) {
            t.finish(Status.FALLBACK); return;
        }
        if (t.live) {
            // An empty compile queue says nothing about chunks not yet delivered or discovered.
            // Retain the complete live scene until meshes covering its current view are ready.
            // The physical graph can legitimately reject buried sections submitted by the
            // sparse visual world. Compare actual draws for graph-visible sections, and
            // require loaded chunks plus consecutive stable coverage before trusting culling.
            var model = new org.joml.Matrix4f().rotation(new org.joml.Quaternionf(camera.rotation()).conjugate());
            var frustum = SkyesightFrustumFactory.create(camera, model, t.destination.projection());
            int radius = Math.min(PlayerPerspectiveViews.radius(t.viewId, t.retainedScene.options().terrainRadius()), mc.options.getEffectiveRenderDistance());
            java.util.Set<BlockPos> physicalVisible, physicalGraph;
            try { physicalVisible = SecondaryTerrainCoverage.physicalDrawn(mc.levelRenderer); physicalGraph = SecondaryTerrainCoverage.physicalVisible(mc.levelRenderer); }
            catch (RuntimeException failure) {
                Skyesight.LOGGER.warn("Unable to verify physical transition coverage view={}", t.viewId, failure);
                t.finish(Status.FALLBACK); return;
            }
            int needed = 0, missing = 0;
            for (var pos : t.visibleTerrain) {
                if (Math.abs((pos.getX() >> 4) - center.x) > radius || Math.abs((pos.getZ() >> 4) - center.z) > radius
                        || !frustum.isVisible(new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(),
                                pos.getX() + 16, pos.getY() + 16, pos.getZ() + 16))) continue;
                needed++;
                var chunk = SecondaryCollisionView.received(mc.level, pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || physicalGraph.contains(pos) && !physicalVisible.contains(pos)
                        && !chunk.getSection(chunk.getSectionIndex(pos.getY())).hasOnlyAir()) {
                    missing++;
                }
            }
            if (missing != t.lastMissingCoverage && Boolean.getBoolean("skyesight.debugTraversal"))
                Skyesight.LOGGER.info("[TransitionCoverage] required={} missing={} physicalSections={}", needed, missing, mc.levelRenderer.countRenderedSections());
            t.lastMissingCoverage = missing;
            boolean stableCoverage = true;
            for (var pos : t.previousPhysicalCoverage) {
                // Camera motion may legitimately remove a section from the current view.
                // Only disappearance from a still-visible region is a coverage regression.
                if (physicalGraph.contains(pos) && !physicalVisible.contains(pos)
                        && frustum.isVisible(new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(),
                                pos.getX() + 16, pos.getY() + 16, pos.getZ() + 16))) {
                    var chunk = SecondaryCollisionView.received(mc.level, pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk == null || !chunk.getSection(chunk.getSectionIndex(pos.getY())).hasOnlyAir()) {
                        stableCoverage = false;
                        break;
                    }
                }
            }
            t.previousPhysicalCoverage = physicalVisible;
            boolean stableTiming = frameInterval <= t.stableFrameBudget
                    && System.nanoTime() - frameStart <= t.stableFrameBudget;
            t.coverageReadyFrames = missing == 0 && stableCoverage && stableTiming ? previousReadyFrames + 1 : 0;
            // The second completed frame observes render-list updates following mesh uploads.
            if (t.coverageReadyFrames < 2) return;
            if (!t.validatePhysicalPortals) {
                // Do not initialize reverse portal renderers in a covered near-plane frame.
                // Re-enable normal portal passes and validate two complete frames before exposure.
                t.validatePhysicalPortals = true;
                t.coverageReadyFrames = 0;
                return;
            }
            float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
            var expected = new com.skyeshade.skyesight.client.view.SkyesightMutableCamera();
            expected.setRotationPublic(mc.player.getViewYRot(partial), mc.player.getViewXRot(partial), 0);
            if (camera.getPosition().distanceToSqr(mc.player.getEyePosition(partial)) > .0025
                    || Math.abs(camera.rotation().dot(expected.rotation())) < Math.cos(Math.toRadians(.5) / 2)) {
                t.coverageReadyFrames = 0;
                return;
            }
        }
        t.renderedDestination = true;
        trace("matchingPhysicalFrame");
    }
    /** Replaces world color before GameRenderer's normal hand and GUI composition. */
    public static void present() {
        if (presentedThisFrame) return;
        var t = active;
        if (t == null || t.status == Status.COMPLETE || t.status == Status.CANCELLED || t.status == Status.FALLBACK || t.expired()) return;
        if (!t.presenting()) return;
        presentedThisFrame = true;
        var mc = Minecraft.getInstance();
        if (mc.level != null && !mc.level.dimension().equals(t.source) && !mc.level.dimension().equals(t.destination.dimension())) {
            t.finish(Status.FALLBACK); return;
        }
        if (mc.screen != null && !(mc.screen instanceof ReceivingLevelScreen)) { t.finish(Status.FALLBACK); return; }
        long now = System.nanoTime();
        if (t.lastPresentNanos != 0) t.worstFrameMs = Math.max(t.worstFrameMs, (now - t.lastPresentNanos) / 1_000_000.0);
        t.lastPresentNanos = now;
        t.worstFrameMs = Math.max(t.worstFrameMs, (now - frameStart) / 1_000_000.0);
        if (t.renderedDestination && mc.screen == null) {
            Skyesight.LOGGER.debug("Transition complete view={} eye={} rotation={}", t.viewId,
                    mc.gameRenderer.getMainCamera().getPosition(), mc.gameRenderer.getMainCamera().rotation());
            t.finish(Status.COMPLETE); return;
        }
        if (t.live && t.retainedScene != null && mc.player != null) {
            if (!t.validView.getAsBoolean() || t.retainedScene.visualWorld() != null && t.retainedScene.visualWorld().isClosed()) {
                t.finish(Status.FALLBACK); return;
            }
            float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
            var camera = new com.skyeshade.skyesight.client.view.SkyesightMutableCamera();
            camera.setup(mc.level, mc.player, false, false, partialTick);
            camera.setPositionPublic(mc.player.getEyePosition(partialTick));
            camera.setRotationPublic(mc.player.getViewYRot(partialTick), mc.player.getViewXRot(partialTick), 0);
            var position = camera.getPosition();
            var rotation = new org.joml.Quaternionf(camera.rotation());
            if (!t.authoritative && t.awaitingDestinationPosition) {
                // Respawn may be processed a frame before the position packet. The new
                // LocalPlayer is at its default spawn, not another source movement sample.
                position = t.destination.eyePosition(); rotation = t.destination.rotation();
            } else if (!t.authoritative && t.portal != null) {
                position = com.skyeshade.skyesight.portal.PortalTraversalMath.position(t.portal.source(), t.portal.target(), position);
                rotation = com.skyeshade.skyesight.portal.PortalTraversalMath.rotation(t.portal.source().rotation(), t.portal.target().rotation()).mul(rotation);
            } else if (!t.authoritative) {
                position = t.destination.eyePosition(); rotation = t.destination.rotation();
            }
            render(t, t.retainedScene, position, rotation, partialTick);
            if (active != t || t.image == null) return;
            t.liveFrames++;
        }
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, t.image.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
            GL30.glBlitFramebuffer(0, 0, t.image.width, t.image.height, 0, 0, mc.getMainRenderTarget().width,
                    mc.getMainRenderTarget().height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }
}
