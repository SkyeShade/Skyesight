package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.SkyesightClientConfig;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Camera;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalRenderTargetBounds {
    private static final boolean ENABLE_BOUNDS_SCALED_RENDER_TARGETS_EXPERIMENTAL = true;
    private static final float CLIP_EPSILON = 1.0E-5F;
    private static final double BOUNDS_RESOLUTION_SCALE = 1.0D;
    private static final int BOUNDS_PADDING_PIXELS = 32;
    private static final int BOUNDS_MIN_WIDTH = 128;
    private static final int BOUNDS_MIN_HEIGHT = 128;
    private static final int BOUNDS_SIZE_QUANTUM = 64;
    private static final int BOUNDS_SHRINK_STABLE_FRAMES = 30;
    private static final boolean RESOLUTION_REPORT_DEBUG_ENABLED = false;
    private static final Map<ResourceLocation, StableSizeState> STABLE_SIZE_BY_VIEW = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, DiagnosticSize> DIAGNOSTIC_SIZE_BY_VIEW = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, TargetSize> RESOLVED_SIZE_BY_VIEW = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Long> LAST_INVALID_PROOF_LOG_BY_VIEW = new ConcurrentHashMap<>();
    private static volatile long currentFrameId = Long.MIN_VALUE;
    private static volatile long frameGeneration;
    private static volatile int fullscreenTargetsThisFrame;
    private static volatile int boundsTargetsThisFrame;
    private static volatile int tinyTargetsThisFrame;

    private PortalRenderTargetBounds() {}

    /**
     * Resolves the off-screen target size for a portal view.
     *
     * <p>The returned size is expressed in framebuffer pixels. Bounds mode derives it from the portal
     * aperture's projected screen-space rectangle; otherwise it matches the main framebuffer.</p>
     */
    public static TargetSize resolveTargetSize(
            Matrix4f viewProjection,
            PortalFrame portal,
            Vec3 mainCameraPosition,
            int framebufferWidth,
            int framebufferHeight,
            int fallbackWidth,
            int fallbackHeight
    ) {
        return resolveTargetSize(null, viewProjection, portal, mainCameraPosition, framebufferWidth, framebufferHeight, fallbackWidth, fallbackHeight);
    }

    public static TargetSize resolveTargetSize(
            ResourceLocation viewId,
            Matrix4f viewProjection,
            PortalFrame portal,
            Vec3 mainCameraPosition,
            int framebufferWidth,
            int framebufferHeight,
            int fallbackWidth,
            int fallbackHeight
    ) {
        TargetSize cached = viewId == null ? null : RESOLVED_SIZE_BY_VIEW.get(viewId);
        if (cached != null) {
            recordTargetSize(cached);
            return cached;
        }
        if (!boundsScalingActive()) {
            TargetSize size = TargetSize.fullscreen(
                    fullscreenWidth(framebufferWidth, fallbackWidth),
                    fullscreenHeight(framebufferHeight, fallbackHeight),
                    SkyesightClientConfig.enableBoundsScaledPortalResolution()
                            ? "bounds-scaling-experimental-disabled"
                            : "config-disabled"
            );
            recordTargetSize(size);
            cacheResolvedSize(viewId, size);
            return size;
        }
        if (viewProjection == null || portal == null || framebufferWidth <= 0 || framebufferHeight <= 0) {
            TargetSize size = TargetSize.fullscreen(
                    fullscreenWidth(framebufferWidth, fallbackWidth),
                    fullscreenHeight(framebufferHeight, fallbackHeight),
                    "missing-input-fallback"
            );

            recordTargetSize(size);
            return markStableFallback(viewId, size);
        }

        TargetSize cachedDiagnosticSize = targetSizeFromDiagnostic(viewId);
        if (cachedDiagnosticSize != null) {
            TargetSize size = stabilizeTargetSize(viewId, cachedDiagnosticSize);
            recordTargetSize(size);
            cacheResolvedSize(viewId, size);
            return size;
        }

        Bounds bounds = computeBounds(viewProjection, portal, mainCameraPosition, framebufferWidth, framebufferHeight);
        if (!bounds.valid()) {
            TargetSize size = TargetSize.fullscreen(
                    fullscreenWidth(framebufferWidth, fallbackWidth),
                    fullscreenHeight(framebufferHeight, fallbackHeight),
                    bounds.reason()
            );
            logBoundsInvalidProofIfDue(viewId, bounds, portal, framebufferWidth, framebufferHeight, "resolveTargetSize camera-relative");
            recordTargetSize(size);
            return markStableFallback(viewId, size);
        }

        int padding = BOUNDS_PADDING_PIXELS;
        int paddedWidth = clamp(bounds.width() + padding * 2, 1, framebufferWidth);
        int paddedHeight = clamp(bounds.height() + padding * 2, 1, framebufferHeight);
        double scale = BOUNDS_RESOLUTION_SCALE;
        int scaledWidth = (int) Math.ceil(paddedWidth * scale);
        int scaledHeight = (int) Math.ceil(paddedHeight * scale);
        int minWidth = BOUNDS_MIN_WIDTH;
        int minHeight = BOUNDS_MIN_HEIGHT;
        int quantum = Math.max(1, BOUNDS_SIZE_QUANTUM);
        int targetWidth = roundUp(clamp(Math.max(minWidth, scaledWidth), 1, framebufferWidth), quantum);
        int targetHeight = roundUp(clamp(Math.max(minHeight, scaledHeight), 1, framebufferHeight), quantum);
        targetWidth = clamp(targetWidth, 1, framebufferWidth);
        targetHeight = clamp(targetHeight, 1, framebufferHeight);
        TargetSize size = new TargetSize(
                targetWidth,
                targetHeight,
                true,
                false,
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                "bounds-scaled"
        );
        size = stabilizeTargetSize(viewId, size);
        recordTargetSize(size);
        cacheResolvedSize(viewId, size);
        return size;
    }

    private static void cacheResolvedSize(ResourceLocation viewId, TargetSize size) {
        if (viewId != null && size != null) {
            RESOLVED_SIZE_BY_VIEW.put(viewId, size);
        }
    }

    static void clear(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        STABLE_SIZE_BY_VIEW.remove(viewId);
        DIAGNOSTIC_SIZE_BY_VIEW.remove(viewId);
        RESOLVED_SIZE_BY_VIEW.remove(viewId);
        LAST_INVALID_PROOF_LOG_BY_VIEW.remove(viewId);
    }

    static void clearAll() {
        STABLE_SIZE_BY_VIEW.clear();
        DIAGNOSTIC_SIZE_BY_VIEW.clear();
        LAST_INVALID_PROOF_LOG_BY_VIEW.clear();
        resetFrameCounters();
    }

    public static void resetFrameCounters() {
        fullscreenTargetsThisFrame = 0;
        boundsTargetsThisFrame = 0;
        tinyTargetsThisFrame = 0;
        RESOLVED_SIZE_BY_VIEW.clear();
    }

    public static synchronized void beginFrame(long frameId) {
        if (currentFrameId == frameId) {
            return;
        }
        currentFrameId = frameId;
        frameGeneration++;
        resetFrameCounters();
        DIAGNOSTIC_SIZE_BY_VIEW.clear();
    }

    public static int fullscreenTargetsThisFrame() {
        return fullscreenTargetsThisFrame;
    }

    public static int boundsTargetsThisFrame() {
        return boundsTargetsThisFrame;
    }

    public static int tinyTargetsThisFrame() {
        return tinyTargetsThisFrame;
    }

    private static TargetSize stabilizeTargetSize(ResourceLocation viewId, TargetSize desired) {
        if (viewId == null || desired == null || !boundsScalingActive()) {
            return desired;
        }
        StableSizeState state = STABLE_SIZE_BY_VIEW.computeIfAbsent(viewId, ignored -> new StableSizeState());
        return state.stabilize(desired, frameGeneration);
    }

    private static TargetSize markStableFallback(ResourceLocation viewId, TargetSize desired) {
        if (viewId != null) {
            STABLE_SIZE_BY_VIEW.computeIfAbsent(viewId, ignored -> new StableSizeState()).markFallback(desired);
            cacheResolvedSize(viewId, desired);
        }
        return desired;
    }

    private static void recordTargetSize(TargetSize size) {
        if (size == null) {
            return;
        }
        if (size.boundsScaled()) {
            boundsTargetsThisFrame++;
        }
        if (size.fallback()) {
            fullscreenTargetsThisFrame++;
        }
        if (size.width() > 0 && size.height() > 0 && (size.width() < BOUNDS_MIN_WIDTH || size.height() < BOUNDS_MIN_HEIGHT)) {
            tinyTargetsThisFrame++;
        }
    }

    public static void logIfEnabled(ResourceLocation viewId, String sourceTag, TargetSize targetSize) {
        if (!SkyesightDebugConfig.shouldLogRenderTargetAudit()) {
            return;
        }
        boolean fromDebugStick = (sourceTag != null && sourceTag.equals("debug-stick"))
                || (viewId != null && viewId.getPath().startsWith("debug_stick"));
        TargetSize size = targetSize == null ? TargetSize.fullscreen(0, 0, "missing-target-size") : targetSize;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BOUNDS_RENDER_TARGET: viewId={} source=registered-view fromDebugStick={} boundsEnabled={} usingFullscreenFallback={} screenBounds={} targetSize={} reason={}",
                viewId == null ? "-" : viewId,
                fromDebugStick ? "yes" : "no",
                boundsScalingActive() ? "yes" : "no",
                size.fallback() ? "yes" : "no",
                size.boundsX() + "," + size.boundsY() + " " + size.boundsWidth() + "x" + size.boundsHeight(),
                size.width() + "x" + size.height(),
                size.reason()
        );
    }

    public static void recordResolutionUse(
            ResourceLocation viewId,
            TargetSize targetSize,
            int actualWidth,
            int actualHeight,
            int previousWidth,
            int previousHeight,
            boolean resized
    ) {
        if (viewId == null || targetSize == null) {
            return;
        }
        StableSizeState state = STABLE_SIZE_BY_VIEW.computeIfAbsent(viewId, ignored -> new StableSizeState());
        state.recordRender(resized);
        if (!RESOLUTION_REPORT_DEBUG_ENABLED && !SkyesightDebugConfig.shouldLogRenderTargetAudit()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastReportMillis < 1_000L) {
            return;
        }
        state.lastReportMillis = now;
        int resizedThisSecond = state.resizedThisSecond;
        int renderedThisSecond = state.renderedThisSecond;
        state.resizedThisSecond = 0;
        state.renderedThisSecond = 0;
        DiagnosticSize diagnostic = DIAGNOSTIC_SIZE_BY_VIEW.get(viewId);
        String view = shortViewId(viewId);
        String diag = diagnostic == null ? "-" : diagnostic.boundsSummary();
        String desired = diagnostic == null
                ? targetSize.width() + "x" + targetSize.height()
                : diagnostic.requestedWidth() + "x" + diagnostic.requestedHeight();
        String stable = targetSize.width() + "x" + targetSize.height();
        String fallback = diagnostic != null && diagnostic.fallback()
                ? " fallback=" + compactFallbackReason(diagnostic.reason())
                : "";
        String reason = compactResolutionReason(targetSize.reason());
        String stabilizerState = state.compactState();
        if (diagnostic != null && !diagnostic.fallback()
                && (targetSize.width() < diagnostic.requestedWidth() || targetSize.height() < diagnostic.requestedHeight())) {
            reason += "+stable-under-desired";
        }
        if (targetSize.width() != actualWidth || targetSize.height() != actualHeight) {
            reason += "+actual-mismatch";
        }
        String changed = previousWidth != actualWidth || previousHeight != actualHeight
                ? " prev=" + previousWidth + "x" + previousHeight
                : "";
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_RES view={} cfg={} active={} diag={}{} desired={} stable={} actual={} resizes={} renders={} state={} reason={}{}",
                view,
                SkyesightClientConfig.enableBoundsScaledPortalResolution() ? "yes" : "no",
                boundsScalingActive() ? "yes" : "no",
                diag,
                fallback,
                desired,
                stable,
                actualWidth + "x" + actualHeight,
                resizedThisSecond,
                renderedThisSecond,
                stabilizerState,
                reason,
                changed
        );
    }

    public static void captureDiagnosticBounds(
            ResourceLocation viewId,
            Matrix4f viewProjection,
            PortalFrame portal,
            Camera camera,
            int framebufferWidth,
            int framebufferHeight
    ) {
        if (viewId == null || (!SkyesightClientConfig.enableBoundsScaledPortalResolution()
                && !SkyesightDebugConfig.shouldLogRenderTargetAudit())) {
            return;
        }
        if (viewProjection == null || portal == null || camera == null || framebufferWidth <= 0 || framebufferHeight <= 0) {
            DIAGNOSTIC_SIZE_BY_VIEW.put(viewId, DiagnosticSize.fallback(framebufferWidth, framebufferHeight, "missing-input"));
            return;
        }
        Bounds bounds = computeBounds(viewProjection, portal, camera.getPosition(), framebufferWidth, framebufferHeight);
        logWConventionProofIfEnabled(viewId, bounds.normalMode(), bounds.flippedMode(), bounds.chosenMode());
        logProjectionComparisonIfEnabled(viewId, computeBounds(viewProjection, portal, null, framebufferWidth, framebufferHeight), bounds);
        logProjectionProofIfEnabled(viewId, bounds);
        if (!bounds.valid()) {
            DIAGNOSTIC_SIZE_BY_VIEW.put(viewId, DiagnosticSize.fallback(framebufferWidth, framebufferHeight, bounds.reason()));
            return;
        }
        TargetSize requested = targetSizeForBounds(bounds, framebufferWidth, framebufferHeight);
        DIAGNOSTIC_SIZE_BY_VIEW.put(viewId, DiagnosticSize.fromBounds(bounds, requested, "diagnostic-bounds"));
    }

    private static boolean boundsScalingActive() {
        return SkyesightClientConfig.enableBoundsScaledPortalResolution()
                && ENABLE_BOUNDS_SCALED_RENDER_TARGETS_EXPERIMENTAL;
    }

    private static void logProjectionProofIfEnabled(ResourceLocation viewId, Bounds bounds) {
        if (!SkyesightDebugConfig.shouldLogRenderTargetAudit() || viewId == null || bounds == null) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BOUNDS_PROJECTION_PROOF: view={} stage=mask sourceOrTarget=source usesStencilApertureGeometry=yes cameraRelative={} matrixSource=main-mask-stage cornerCount=4 invalidCorners={} firstCornerRelative={} firstCornerClip={} reason={}",
                shortViewId(viewId),
                bounds.cameraRelative() ? "yes" : "no",
                bounds.invalidCorners(),
                bounds.firstCornerRelative(),
                bounds.firstCornerClip(),
                bounds.reason()
        );
    }

    private static void logProjectionComparisonIfEnabled(ResourceLocation viewId, Bounds rawWorld, Bounds cameraRelative) {
        if (!SkyesightDebugConfig.shouldLogRenderTargetAudit() || viewId == null || rawWorld == null || cameraRelative == null) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BOUNDS_PROJECTION_COMPARE: view={} modeA=rawWorld validA={}/4 boundsA={} reasonA={} modeB=cameraRelative validB={}/4 boundsB={} reasonB={}",
                shortViewId(viewId),
                rawWorld.validCornerCount(),
                rawWorld.boundsSummary(),
                rawWorld.reason(),
                cameraRelative.validCornerCount(),
                cameraRelative.boundsSummary(),
                cameraRelative.reason()
        );
    }

    private static void logWConventionProofIfEnabled(ResourceLocation viewId, ProjectionMode normal, ProjectionMode flipped, ProjectionMode chosen) {
        if (!SkyesightDebugConfig.shouldLogRenderTargetAudit() || viewId == null || normal == null || flipped == null || chosen == null) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BOUNDS_W_CONVENTION_PROOF: view={} wValues={} modeNormalValid={}/4 modeNormalBounds={} modeFlippedValid={}/4 modeFlippedBounds={} chosenMode={}",
                shortViewId(viewId),
            chosen.wValuesSummary(),
                normal.validCorners(),
                normal.boundsSummary(),
                flipped.validCorners(),
                flipped.boundsSummary(),
                chosen.modeName()
        );
    }

    private static void logBoundsInvalidProofIfDue(
            ResourceLocation viewId,
            Bounds bounds,
            PortalFrame portal,
            int framebufferWidth,
            int framebufferHeight,
            String matrixSource
    ) {
        if (!SkyesightDebugConfig.shouldLogRenderTargetAudit() || bounds == null) {
            return;
        }
        long now = System.currentTimeMillis();
        ResourceLocation key = viewId == null ? ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "unknown") : viewId;
        Long previous = LAST_INVALID_PROOF_LOG_BY_VIEW.get(key);
        if (previous != null && now - previous < 1_000L) {
            return;
        }
        LAST_INVALID_PROOF_LOG_BY_VIEW.put(key, now);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BOUNDS_INVALID_PROOF: viewId={} reason={} framebuffer={} portalCenter={} portalWidth={} portalHeight={} cameraRelativeUsed={} matrixSource={} corner0World={} corner0Relative={} corner0Clip={} corner0W={} corner0InvalidReason={} corner1W={} corner1InvalidReason={} corner2W={} corner2InvalidReason={} corner3W={} corner3InvalidReason={} validCornerCount={} invalidCornerCount={}",
                key,
                bounds.reason(),
                framebufferWidth + "x" + framebufferHeight,
                portal == null ? "-" : formatVec(portal.position()),
                portal == null ? 0.0F : portal.width(),
                portal == null ? 0.0F : portal.height(),
                bounds.cameraRelative() ? "yes" : "no",
                matrixSource == null ? "-" : matrixSource,
                bounds.firstCornerWorld(),
                bounds.firstCornerRelative(),
                bounds.firstCornerClip(),
                bounds.cornerW(0),
                bounds.cornerInvalidReason(0),
                bounds.cornerW(1),
                bounds.cornerInvalidReason(1),
                bounds.cornerW(2),
                bounds.cornerInvalidReason(2),
                bounds.cornerW(3),
                bounds.cornerInvalidReason(3),
                bounds.validCornerCount(),
                bounds.invalidCorners()
        );
    }

    private static String shortViewId(ResourceLocation viewId) {
        if (viewId == null) {
            return "-";
        }
        return Skyesight.MODID.equals(viewId.getNamespace()) ? viewId.getPath() : viewId.toString();
    }

    private static String compactFallbackReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "fallback";
        }
        if (reason.startsWith("near-plane-ambiguous invalidCorners=")) {
            return "near-plane/" + reason.substring("near-plane-ambiguous invalidCorners=".length());
        }
        if (reason.startsWith("projection-uncertain")) {
            return "projection-uncertain";
        }
        return reason.replace(' ', '-');
    }

    private static String compactResolutionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "-";
        }
        return reason.replace(' ', '-');
    }

    private static int fullscreenWidth(int framebufferWidth, int fallbackWidth) {
        return framebufferWidth > 0 ? framebufferWidth : Math.max(1, fallbackWidth);
    }

    private static int fullscreenHeight(int framebufferHeight, int fallbackHeight) {
        return framebufferHeight > 0 ? framebufferHeight : Math.max(1, fallbackHeight);
    }

    private static Bounds computeBounds(Matrix4f viewProjection, PortalFrame portal, int framebufferWidth, int framebufferHeight) {
        return computeBounds(viewProjection, portal, null, framebufferWidth, framebufferHeight);
    }

    /**
     * Projects the portal aperture into main-screen framebuffer coordinates.
     */
    private static Bounds computeBounds(Matrix4f viewProjection, PortalFrame portal, Vec3 cameraPosition, int framebufferWidth, int framebufferHeight) {
        Vec3 center = portal.position();
        Vec3 right = PortalFrameMath.right(portal).scale(-1.0D);
        Vec3 up = PortalFrameMath.up(portal);
        double halfWidth = portal.width() * 0.5D;
        double halfHeight = portal.height() * 0.5D;
        Vec3[] corners = new Vec3[] {
                center.subtract(right.scale(halfWidth)).subtract(up.scale(halfHeight)),
                center.add(right.scale(halfWidth)).subtract(up.scale(halfHeight)),
                center.add(right.scale(halfWidth)).add(up.scale(halfHeight)),
                center.subtract(right.scale(halfWidth)).add(up.scale(halfHeight))
        };
        String firstCornerRelative = "-";
        String firstCornerWorld = "-";
        String firstCornerClip = "-";
        Vector4f[] clips = new Vector4f[corners.length];
        String[] cornerWs = new String[corners.length];
        for (int i = 0; i < corners.length; i++) {
            Vec3 projectPosition = cameraPosition == null ? corners[i] : corners[i].subtract(cameraPosition);
            clips[i] = projectClip(viewProjection, projectPosition);
            if (i == 0) {
                firstCornerWorld = formatVec(corners[i]);
                firstCornerRelative = formatVec(projectPosition);
                firstCornerClip = formatClip(clips[i]);
            }
            cornerWs[i] = formatFloat(clips[i].w);
        }
        ProjectionMode normal = projectMode(clips, false, framebufferWidth, framebufferHeight);
        ProjectionMode flipped = projectMode(clips, true, framebufferWidth, framebufferHeight);
        ProjectionMode chosen = allClipWNegative(clips) && flipped.valid() ? flipped : chooseProjectionMode(normal, flipped);
        String[] cornerInvalidReasons = chosen.cornerReasons();
        int invalidCorners = chosen.invalidCorners();
        int validCorners = chosen.validCorners();
        Bounds preview = chosen.toBounds(
                firstCornerWorld,
                firstCornerRelative,
                firstCornerClip,
                cameraPosition != null,
                cornerWs
        );
        if (validCorners == 0) {
            return Bounds.invalid(
                    "near-plane-ambiguous invalidCorners=" + invalidCorners + " mode=" + chosen.modeName(),
                    invalidCorners,
                    validCorners,
                    firstCornerWorld,
                    firstCornerRelative,
                    firstCornerClip,
                    cameraPosition != null,
                    cornerWs,
                    cornerInvalidReasons,
                    normal,
                    flipped,
                    chosen
            );
        }

        if (!preview.valid()) {
            return Bounds.invalid(
                    preview.reason(),
                    invalidCorners,
                    validCorners,
                    firstCornerWorld,
                    firstCornerRelative,
                    firstCornerClip,
                    cameraPosition != null,
                    cornerWs,
                    cornerInvalidReasons,
                    normal,
                    flipped,
                    chosen
            );
        }
        return new Bounds(
                preview.x(),
                preview.y(),
                preview.width(),
                preview.height(),
                true,
                invalidCorners == 0
                        ? "visible mode=" + chosen.modeName()
                        : "partial-near-plane validCorners=" + validCorners + " invalidCorners=" + invalidCorners + " mode=" + chosen.modeName(),
                invalidCorners,
                validCorners,
                firstCornerWorld,
                firstCornerRelative,
                firstCornerClip,
                cameraPosition != null,
                cornerWs,
                cornerInvalidReasons,
                normal,
                flipped,
                chosen
        );
    }

    private static TargetSize targetSizeForBounds(Bounds bounds, int framebufferWidth, int framebufferHeight) {
        if (bounds == null || !bounds.valid()) {
            return TargetSize.fullscreen(framebufferWidth, framebufferHeight, bounds == null ? "missing-bounds" : bounds.reason());
        }
        int padding = BOUNDS_PADDING_PIXELS;
        int paddedWidth = clamp(bounds.width() + padding * 2, 1, framebufferWidth);
        int paddedHeight = clamp(bounds.height() + padding * 2, 1, framebufferHeight);
        double scale = BOUNDS_RESOLUTION_SCALE;
        int scaledWidth = (int) Math.ceil(paddedWidth * scale);
        int scaledHeight = (int) Math.ceil(paddedHeight * scale);
        int quantum = Math.max(1, BOUNDS_SIZE_QUANTUM);
        int targetWidth = roundUp(clamp(Math.max(BOUNDS_MIN_WIDTH, scaledWidth), 1, framebufferWidth), quantum);
        int targetHeight = roundUp(clamp(Math.max(BOUNDS_MIN_HEIGHT, scaledHeight), 1, framebufferHeight), quantum);
        targetWidth = clamp(targetWidth, 1, framebufferWidth);
        targetHeight = clamp(targetHeight, 1, framebufferHeight);
        return new TargetSize(
                targetWidth,
                targetHeight,
                true,
                false,
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                "diagnostic-bounds-scaled"
        );
    }

    private static String invalidClipReason(Vector4f clip) {
        if (clip == null) {
            return "missing";
        }
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.z) || !Float.isFinite(clip.w)) {
            return "non-finite";
        }
        if (Math.abs(clip.w) < CLIP_EPSILON) {
            return "w-near-zero";
        }
        return null;
    }

    private static boolean allClipWNegative(Vector4f[] clips) {
        if (clips == null || clips.length == 0) {
            return false;
        }
        for (Vector4f clip : clips) {
            if (clip == null || !Float.isFinite(clip.w) || clip.w >= -CLIP_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static ProjectionMode projectMode(Vector4f[] clips, boolean flipW, int framebufferWidth, int framebufferHeight) {
        Vector3f[] ndc = new Vector3f[clips.length];
        String[] invalidReasons = new String[clips.length];
        String[] wValues = new String[clips.length];
        int valid = 0;
        int invalid = 0;
        for (int i = 0; i < clips.length; i++) {
            Vector4f clip = clips[i];
            wValues[i] = clip == null ? "-" : formatFloat(clip.w);
            String invalidReason = invalidClipReason(clip);
            if (invalidReason == null) {
                float divisor = flipW && clip.w < 0.0F ? -clip.w : clip.w;
                if (Math.abs(divisor) < CLIP_EPSILON || !Float.isFinite(divisor)) {
                    invalidReason = "divisor-invalid";
                } else {
                    ndc[i] = new Vector3f(clip.x / divisor, clip.y / divisor, clip.z / divisor);
                }
            }
            invalidReasons[i] = invalidReason;
            if (invalidReason == null) {
                valid++;
            } else {
                invalid++;
            }
        }
        BoundsLike bounds = boundsFromNdc(ndc, framebufferWidth, framebufferHeight);
        return new ProjectionMode(flipW ? "flipped" : "normal", valid, invalid, bounds, invalidReasons, wValues);
    }

    private static ProjectionMode chooseProjectionMode(ProjectionMode normal, ProjectionMode flipped) {
        if (flipped.valid() && !normal.valid()) {
            return flipped;
        }
        if (normal.valid() && !flipped.valid()) {
            return normal;
        }
        if (flipped.valid() && normal.valid()) {
            return flipped.area() < normal.area() ? flipped : normal;
        }
        return flipped.validCorners() >= normal.validCorners() ? flipped : normal;
    }

    private static BoundsLike boundsFromNdc(Vector3f[] ndc, int framebufferWidth, int framebufferHeight) {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int valid = 0;
        for (Vector3f point : ndc) {
            if (point == null || !Float.isFinite(point.x) || !Float.isFinite(point.y)) {
                continue;
            }
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
            valid++;
        }
        if (valid == 0) {
            return BoundsLike.invalid("no-valid-corners");
        }
        if (maxX < -1.0F || minX > 1.0F || maxY < -1.0F || minY > 1.0F) {
            return BoundsLike.invalid("offscreen");
        }
        int x0 = clamp((int) Math.floor((minX * 0.5F + 0.5F) * framebufferWidth), 0, framebufferWidth);
        int y0 = clamp((int) Math.floor((minY * 0.5F + 0.5F) * framebufferHeight), 0, framebufferHeight);
        int x1 = clamp((int) Math.ceil((maxX * 0.5F + 0.5F) * framebufferWidth), 0, framebufferWidth);
        int y1 = clamp((int) Math.ceil((maxY * 0.5F + 0.5F) * framebufferHeight), 0, framebufferHeight);
        int width = Math.max(0, x1 - x0);
        int height = Math.max(0, y1 - y0);
        if (width <= 0 || height <= 0) {
            return BoundsLike.invalid("zero-screen-bounds");
        }
        return new BoundsLike(x0, y0, width, height, true, "visible");
    }

    private static Vector4f projectClip(Matrix4f viewProjection, Vec3 position) {
        return new Vector4f(
                (float) position.x(),
                (float) position.y(),
                (float) position.z(),
                1.0F
        ).mul(viewProjection);
    }

    private static int roundUp(int value, int quantum) {
        if (quantum <= 1) {
            return value;
        }
        return ((value + quantum - 1) / quantum) * quantum;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatVec(Vec3 point) {
        if (point == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", point.x(), point.y(), point.z());
    }

    private static String formatClip(Vector4f point) {
        if (point == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", point.x, point.y, point.z, point.w);
    }

    private static String formatFloat(float value) {
        return Float.isFinite(value) ? String.format(Locale.ROOT, "%.4f", value) : "nan";
    }

    private static TargetSize targetSizeFromDiagnostic(ResourceLocation viewId) {
        if (viewId == null) {
            return null;
        }
        DiagnosticSize diagnostic = DIAGNOSTIC_SIZE_BY_VIEW.get(viewId);
        if (diagnostic == null || diagnostic.fallback()) {
            return null;
        }
        return new TargetSize(
                diagnostic.requestedWidth(),
                diagnostic.requestedHeight(),
                true,
                false,
                diagnostic.boundsX(),
                diagnostic.boundsY(),
                diagnostic.boundsWidth(),
                diagnostic.boundsHeight(),
                "bounds-scaled:mask-stage"
        );
    }

    private record Bounds(
            int x,
            int y,
            int width,
            int height,
            boolean valid,
            String reason,
            int invalidCorners,
            int validCornerCount,
            String firstCornerWorld,
            String firstCornerRelative,
            String firstCornerClip,
            boolean cameraRelative,
            String[] cornerWs,
            String[] cornerInvalidReasons,
            ProjectionMode normalMode,
            ProjectionMode flippedMode,
            ProjectionMode chosenMode
    ) {
        private static Bounds invalid(String reason) {
            return invalid(reason, 0, 0, "-", "-", "-", false, null, null, null, null, null);
        }

        private static Bounds invalid(
                String reason,
                int invalidCorners,
                int validCornerCount,
                String firstCornerWorld,
                String firstCornerRelative,
                String firstCornerClip,
                boolean cameraRelative,
                String[] cornerWs,
                String[] cornerInvalidReasons,
                ProjectionMode normalMode,
                ProjectionMode flippedMode,
                ProjectionMode chosenMode
        ) {
            return new Bounds(
                    0,
                    0,
                    0,
                    0,
                    false,
                    reason,
                    invalidCorners,
                    validCornerCount,
                    firstCornerWorld,
                    firstCornerRelative,
                    firstCornerClip,
                    cameraRelative,
                    cornerWs == null ? new String[] {"-", "-", "-", "-"} : cornerWs.clone(),
                    cornerInvalidReasons == null ? new String[] {"-", "-", "-", "-"} : cornerInvalidReasons.clone(),
                    normalMode,
                    flippedMode,
                    chosenMode
            );
        }

        private String cornerW(int index) {
            return index >= 0 && index < this.cornerWs.length ? this.cornerWs[index] : "-";
        }

        private String cornerInvalidReason(int index) {
            String reason = index >= 0 && index < this.cornerInvalidReasons.length ? this.cornerInvalidReasons[index] : null;
            return reason == null ? "-" : reason;
        }

        private String boundsSummary() {
            return this.valid ? this.x + "," + this.y + " " + this.width + "x" + this.height : "invalid";
        }
    }

    private record BoundsLike(int x, int y, int width, int height, boolean valid, String reason) {
        private static BoundsLike invalid(String reason) {
            return new BoundsLike(0, 0, 0, 0, false, reason);
        }

        private int area() {
            return Math.max(0, this.width) * Math.max(0, this.height);
        }

        private String summary() {
            return this.valid ? this.x + "," + this.y + " " + this.width + "x" + this.height : "invalid";
        }
    }

    private record ProjectionMode(
            String modeName,
            int validCorners,
            int invalidCorners,
            BoundsLike bounds,
            String[] cornerInvalidReasons,
            String[] wValues
    ) {
        private boolean valid() {
            return this.validCorners > 0 && this.bounds != null && this.bounds.valid();
        }

        private int area() {
            return this.bounds == null ? Integer.MAX_VALUE : this.bounds.area();
        }

        private String boundsSummary() {
            return this.bounds == null ? "invalid" : this.bounds.summary();
        }

        private String[] cornerReasons() {
            return this.cornerInvalidReasons == null
                    ? new String[] {"-", "-", "-", "-"}
                    : this.cornerInvalidReasons.clone();
        }

        private String wValuesSummary() {
            if (this.wValues == null || this.wValues.length == 0) {
                return "-";
            }
            return String.join(",", this.wValues);
        }

        private Bounds toBounds(
                String firstCornerWorld,
                String firstCornerRelative,
                String firstCornerClip,
                boolean cameraRelative,
                String[] cornerWs
        ) {
            if (!valid()) {
                return Bounds.invalid(
                        this.bounds == null ? "invalid" : this.bounds.reason(),
                        this.invalidCorners,
                        this.validCorners,
                        firstCornerWorld,
                        firstCornerRelative,
                        firstCornerClip,
                        cameraRelative,
                        cornerWs,
                        cornerReasons(),
                        null,
                        null,
                        this
                );
            }
            return new Bounds(
                    this.bounds.x(),
                    this.bounds.y(),
                    this.bounds.width(),
                    this.bounds.height(),
                    true,
                    this.bounds.reason(),
                    this.invalidCorners,
                    this.validCorners,
                    firstCornerWorld,
                    firstCornerRelative,
                    firstCornerClip,
                    cameraRelative,
                    cornerWs == null ? new String[] {"-", "-", "-", "-"} : cornerWs.clone(),
                    cornerReasons(),
                    null,
                    null,
                    this
            );
        }
    }

    private record DiagnosticSize(
            boolean fallback,
            int boundsX,
            int boundsY,
            int boundsWidth,
            int boundsHeight,
            int requestedWidth,
            int requestedHeight,
            String reason
    ) {
        private static DiagnosticSize fallback(int framebufferWidth, int framebufferHeight, String reason) {
            return new DiagnosticSize(
                    true,
                    0,
                    0,
                    0,
                    0,
                    Math.max(1, framebufferWidth),
                    Math.max(1, framebufferHeight),
                    reason == null || reason.isBlank() ? "fallback" : reason
            );
        }

        private static DiagnosticSize fromBounds(Bounds bounds, TargetSize targetSize, String reason) {
            return new DiagnosticSize(
                    false,
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    targetSize.width(),
                    targetSize.height(),
                    reason
            );
        }

        private String boundsSummary() {
            return this.boundsX + "," + this.boundsY + " " + this.boundsWidth + "x" + this.boundsHeight;
        }
    }

    public record TargetSize(
            int width,
            int height,
            boolean boundsScaled,
            boolean fallback,
            int boundsX,
            int boundsY,
            int boundsWidth,
            int boundsHeight,
            String reason
    ) {
        private static TargetSize fullscreen(int width, int height, String reason) {
            return new TargetSize(width, height, false, true, 0, 0, width, height, reason);
        }

        private TargetSize withSize(int width, int height, String reason) {
            return new TargetSize(
                    width,
                    height,
                    this.boundsScaled,
                    this.fallback,
                    this.boundsX,
                    this.boundsY,
                    this.boundsWidth,
                    this.boundsHeight,
                    reason
            );
        }
    }

    private static final class StableSizeState {
        private int stableWidth;
        private int stableHeight;
        private int shrinkCandidateWidth;
        private int shrinkCandidateHeight;
        private int shrinkCandidateFrames;
        private int resizedThisSecond;
        private int renderedThisSecond;
        private long lastReportMillis;
        private long cachedFrameGeneration = Long.MIN_VALUE;
        private int cachedDesiredWidth;
        private int cachedDesiredHeight;
        private boolean cachedDesiredFallback;
        private boolean cachedDesiredBoundsScaled;
        private boolean lastFallback;
        private TargetSize cachedFrameTargetSize;

        private synchronized TargetSize stabilize(TargetSize desired, long generation) {
            if (this.cachedFrameGeneration == generation
                    && this.cachedFrameTargetSize != null
                    && this.cachedDesiredWidth == desired.width()
                    && this.cachedDesiredHeight == desired.height()
                    && this.cachedDesiredFallback == desired.fallback()
                    && this.cachedDesiredBoundsScaled == desired.boundsScaled()) {
                return this.cachedFrameTargetSize;
            }
            TargetSize stable = stabilizeUncached(desired);
            this.cachedFrameGeneration = generation;
            this.cachedDesiredWidth = desired.width();
            this.cachedDesiredHeight = desired.height();
            this.cachedDesiredFallback = desired.fallback();
            this.cachedDesiredBoundsScaled = desired.boundsScaled();
            this.cachedFrameTargetSize = stable;
            return stable;
        }

        private synchronized void markFallback(TargetSize desired) {
            if (desired == null) {
                return;
            }
            if (this.stableWidth <= 0 || this.stableHeight <= 0) {
                this.stableWidth = desired.width();
                this.stableHeight = desired.height();
            }
            this.lastFallback = true;
            this.shrinkCandidateWidth = 0;
            this.shrinkCandidateHeight = 0;
            this.shrinkCandidateFrames = 0;
            this.cachedFrameGeneration = Long.MIN_VALUE;
            this.cachedDesiredWidth = 0;
            this.cachedDesiredHeight = 0;
            this.cachedDesiredFallback = false;
            this.cachedDesiredBoundsScaled = false;
            this.cachedFrameTargetSize = null;
        }

        private TargetSize stabilizeUncached(TargetSize desired) {
            if (desired.width() <= 0 || desired.height() <= 0 || desired.fallback()) {
                markFallback(desired);
                return desired;
            }
            if (this.stableWidth <= 0 || this.stableHeight <= 0) {
                this.stableWidth = desired.width();
                this.stableHeight = desired.height();
                this.lastFallback = false;
                return desired.withSize(this.stableWidth, this.stableHeight, desired.reason() + ":stable-init");
            }
            boolean grow = desired.width() > this.stableWidth || desired.height() > this.stableHeight;
            if (grow) {
                this.stableWidth = Math.max(this.stableWidth, desired.width());
                this.stableHeight = Math.max(this.stableHeight, desired.height());
                this.shrinkCandidateFrames = 0;
                this.lastFallback = false;
                return desired.withSize(this.stableWidth, this.stableHeight, desired.reason() + ":grow");
            }
            boolean same = desired.width() == this.stableWidth && desired.height() == this.stableHeight;
            if (same) {
                this.shrinkCandidateFrames = 0;
                this.lastFallback = false;
                return desired.withSize(this.stableWidth, this.stableHeight, desired.reason() + ":stable");
            }
            if (this.lastFallback) {
                this.stableWidth = desired.width();
                this.stableHeight = desired.height();
                this.shrinkCandidateFrames = 0;
                this.lastFallback = false;
                return desired.withSize(this.stableWidth, this.stableHeight, desired.reason() + ":recover-from-fallback");
            }
            if (desired.width() != this.shrinkCandidateWidth || desired.height() != this.shrinkCandidateHeight) {
                this.shrinkCandidateWidth = desired.width();
                this.shrinkCandidateHeight = desired.height();
                this.shrinkCandidateFrames = 1;
            } else {
                this.shrinkCandidateFrames++;
            }
            if (this.shrinkCandidateFrames >= BOUNDS_SHRINK_STABLE_FRAMES) {
                this.stableWidth = desired.width();
                this.stableHeight = desired.height();
                this.shrinkCandidateFrames = 0;
                this.lastFallback = false;
                return desired.withSize(this.stableWidth, this.stableHeight, desired.reason() + ":shrink");
            }
            return desired.withSize(
                    this.stableWidth,
                    this.stableHeight,
                    desired.reason() + ":shrink-held(" + this.shrinkCandidateFrames + "/" + BOUNDS_SHRINK_STABLE_FRAMES + ")"
            );
        }

        private synchronized void recordRender(boolean resized) {
            this.renderedThisSecond++;
            if (resized) {
                this.resizedThisSecond++;
            }
        }

        private synchronized String compactState() {
            StringBuilder builder = new StringBuilder();
            builder.append("stable=").append(this.stableWidth).append('x').append(this.stableHeight);
            if (this.shrinkCandidateFrames > 0) {
                builder.append(",shrink=").append(this.shrinkCandidateFrames).append('/').append(BOUNDS_SHRINK_STABLE_FRAMES);
            }
            if (this.lastFallback) {
                builder.append(",fallback=yes");
            }
            return builder.toString();
        }
    }
}
